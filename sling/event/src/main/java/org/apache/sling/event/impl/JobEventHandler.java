/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.event.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.jcr.ItemExistsException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.lock.Lock;
import javax.jcr.observation.EventIterator;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;

import org.apache.sling.event.EventUtil;
import org.apache.sling.event.JobStatusProvider;
import org.osgi.framework.BundleEvent;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;


/**
 * An event handler handling special job events.
 *
 * @scr.component
 * @scr.service interface="org.apache.sling.event.JobStatusProvider"
 * @scr.property name="event.topics" refValues="EventUtil.TOPIC_JOB"
 *               values.updated="org/osgi/framework/BundleEvent/UPDATED"
 *               values.started="org/osgi/framework/BundleEvent/STARTED"
 * @scr.property name="repository.path" value="/sling/jobs"
 */
public class JobEventHandler
    extends AbstractRepositoryEventHandler
    implements EventUtil.JobStatusNotifier, JobStatusProvider {

    /** The topic prefix for bundle events. */
    protected static final String BUNDLE_EVENT_PREFIX = BundleEvent.class.getName().replace('.', '/') + '/';

    /** A map for keeping track of currently processed job topics. */
    protected final Map<String, Boolean> processingMap = new HashMap<String, Boolean>();

    /** Default sleep time. */
    protected static final long DEFAULT_SLEEP_TIME = 20;

    /** @scr.property valueRef="DEFAULT_SLEEP_TIME" */
    protected static final String CONFIG_PROPERTY_SLEEP_TIME = "sleep.time";

    /** We check every 20 secs by default. */
    protected long sleepTime;

    /** Background session. */
    protected Session backgroundSession;

    /** Unloaded jobs. */
    protected Set<String>unloadedJobs = new HashSet<String>();

    /**
     * Activate this component.
     * @param context
     * @throws RepositoryException
     */
    protected void activate(final ComponentContext context)
    throws RepositoryException {
        if ( context.getProperties().get(CONFIG_PROPERTY_SLEEP_TIME) != null ) {
            this.sleepTime = (Long)context.getProperties().get(CONFIG_PROPERTY_SLEEP_TIME) * 1000;
        } else {
            this.sleepTime = DEFAULT_SLEEP_TIME * 1000;
        }
        this.backgroundSession = this.createSession();
        super.activate(context);
    }

    /**
     * @see org.apache.sling.event.impl.AbstractRepositoryEventHandler#deactivate(org.osgi.service.component.ComponentContext)
     */
    protected void deactivate(final ComponentContext context) {
        super.deactivate(context);
        if ( this.backgroundSession != null ) {
            try {
                this.backgroundSession.getWorkspace().getObservationManager().removeEventListener(this);
            } catch (RepositoryException e) {
                // we just ignore it
                this.logger.warn("Unable to remove event listener.", e);
            }
            this.backgroundSession.logout();
            this.backgroundSession = null;
        }
    }

    /**
     * @see org.apache.sling.event.impl.AbstractRepositoryEventHandler#processWriteQueue()
     */
    protected void processWriteQueue() {
        while ( this.running ) {
            // so let's wait/get the next job from the queue
            Event event = null;
            try {
                event = this.writeQueue.take();
            } catch (InterruptedException e) {
                // we ignore this
                this.ignoreException(e);
            }
            if ( event != null && this.running ) {
                final String jobId = (String)event.getProperty(EventUtil.PROPERTY_JOB_ID);

                final EventInfo info = new EventInfo();
                info.event = event;
                // if the job has no job id, we can just write the job to the repo and don't
                // need locking
                final String nodeName = this.getNodeName(event);
                if ( jobId == null ) {
                    try {
                        final Node eventNode = this.writeEvent(event, nodeName);
                        info.nodePath = eventNode.getPath();
                    } catch (RepositoryException re ) {
                        // something went wrong, so let's log it
                        this.logger.error("Exception during writing new job '" + nodeName + "' to repository.", re);
                    }
                } else {
                    try {
                        // let's first search for an existing node with the same id
                        final Node parentNode = (Node)this.writerSession.getItem(this.repositoryPath);
                        Node foundNode = null;
                        if ( parentNode.hasNode(nodeName) ) {
                            foundNode = parentNode.getNode(nodeName);
                        }
                        if ( foundNode != null ) {
                            // if the node is locked, someone else was quicker
                            // and we don't have to process this job
                            if ( !foundNode.isLocked() ) {
                                // node is already in repository, so we just overwrite it
                                try {
                                    foundNode.remove();
                                    parentNode.save();
                                    foundNode = null;
                                } catch (RepositoryException re) {
                                    // if anything goes wrong, it means that (hopefully) someone
                                    // else is processing this node
                                }
                            }
                        }
                        if ( foundNode == null ) {
                            // We now write the event into the repository
                            try {
                                final Node eventNode = this.writeEvent(event, nodeName);
                                info.nodePath = eventNode.getPath();
                            } catch (ItemExistsException iee) {
                                // someone else did already write this node in the meantime
                                // nothing to do for us
                            }
                        }
                    } catch (RepositoryException re ) {
                        // something went wrong, so let's log it
                        this.logger.error("Exception during writing new job to repository.", re);
                    }
                }
                // if we were able to write the event into the repository
                // we will queue it for processing
                if ( info.nodePath != null ) {
                    try {
                        this.queue.put(info);
                    } catch (InterruptedException e) {
                        // this should never happen
                        this.ignoreException(e);
                    }
                }
            }
        }
    }

    /**
     * This method runs in the background and processes the local queue.
     */
    protected void runInBackground() {
        while ( this.running ) {
            // so let's wait/get the next job from the queue
            EventInfo info = null;
            try {
                info = this.queue.take();
            } catch (InterruptedException e) {
                // we ignore this
                this.ignoreException(e);
            }

            if ( info != null && this.running ) {
                // check if the node still exists
                try {
                    if ( this.backgroundSession.itemExists(info.nodePath) ) {
                        final Event event = info.event;
                        final String jobTopic = (String)event.getProperty(EventUtil.PROPERTY_JOB_TOPIC);
                        final boolean parallelProcessing = event.getProperty(EventUtil.PROPERTY_JOB_PARALLEL) != null;

                        // check how we can process this job
                        // if parallel processing is allowed, we can just process
                        // if not we should check if any other job with the same topic is currently running
                        boolean process = parallelProcessing;
                        if ( !process ) {
                            synchronized ( this.processingMap ) {
                                final Boolean value = this.processingMap.get(jobTopic);
                                if ( value == null || !value.booleanValue() ) {
                                    this.processingMap.put(jobTopic, Boolean.TRUE);
                                    process = true;
                                }
                            }

                        }
                        if ( process ) {
                            boolean unlock = true;
                            try {
                                final Node eventNode = (Node) this.backgroundSession.getItem(info.nodePath);
                                if ( !eventNode.isLocked() ) {
                                    // lock node
                                    Lock lock = null;
                                    try {
                                        lock = eventNode.lock(false, true);
                                    } catch (RepositoryException re) {
                                        // lock failed which means that the node is locked by someone else, so we don't have to requeue
                                        process = false;
                                    }
                                    if ( process ) {
                                        unlock = false;
                                        this.processJob(info.event, eventNode, lock.getLockToken());
                                    }
                                }
                            } catch (RepositoryException e) {
                                // ignore
                                this.ignoreException(e);
                            } finally {
                                if ( unlock && !parallelProcessing ) {
                                    synchronized ( this.processingMap ) {
                                        this.processingMap.put(jobTopic, Boolean.FALSE);
                                    }
                                }
                            }
                        } else {
                            try {
                                // check if the node is in processing or already finished
                                final Node eventNode = (Node) this.backgroundSession.getItem(info.nodePath);
                                if ( !eventNode.isLocked() ) {
                                    try {
                                        this.queue.put(info);
                                    } catch (InterruptedException e) {
                                        // ignore
                                        this.ignoreException(e);
                                    }
                                    // wait time before we restart the cycle, if there is only one job in the queue!
                                    if ( this.queue.size() == 1 ) {
                                        try {
                                            Thread.sleep(this.sleepTime);
                                        } catch (InterruptedException e) {
                                            // ignore
                                            this.ignoreException(e);
                                        }
                                    }
                                }
                            } catch (RepositoryException e) {
                                // ignore
                                this.ignoreException(e);
                            }
                        }
                    }
                } catch (RepositoryException re) {
                    this.ignoreException(re);
                }
            }
        }
    }

    /**
     * Start the repository session and add this handler as an observer
     * for new events created on other nodes.
     * @throws RepositoryException
     */
    protected void startWriterSession() throws RepositoryException {
        super.startWriterSession();
        // load unprocessed jobs from repository
        this.loadJobs();
        this.backgroundSession.getWorkspace().getObservationManager()
            .addEventListener(this,
                              javax.jcr.observation.Event.PROPERTY_REMOVED,
                              this.repositoryPath,
                              true,
                              null,
                              new String[] {this.getEventNodeType()},
                              true);
    }

    /**
     * @see org.apache.sling.core.event.impl.JobPersistenceHandler#getContainerNodeType()
     */
    protected String getContainerNodeType() {
        return EventHelper.JOBS_NODE_TYPE;
    }

    /**
     * @see org.apache.sling.core.event.impl.JobPersistenceHandler#getEventNodeType()
     */
    protected String getEventNodeType() {
        return EventHelper.JOB_NODE_TYPE;
    }

    /**
     * @see org.osgi.service.event.EventHandler#handleEvent(org.osgi.service.event.Event)
     */
    public void handleEvent(final Event event) {
        // we ignore remote job events
        if ( EventUtil.isLocal(event) ) {
            // check for bundle event
            if ( event.getTopic().equals(EventUtil.TOPIC_JOB)) {
                // job event
                final String jobTopic = (String)event.getProperty(EventUtil.PROPERTY_JOB_TOPIC);

                //  job topic must be set, otherwise we ignore this event!
                if ( jobTopic != null ) {
                    // queue the event in order to respond quickly
                    try {
                        this.writeQueue.put(event);
                    } catch (InterruptedException e) {
                        // this should never happen
                        this.ignoreException(e);
                    }
                } else {
                    this.logger.warn("Event does not contain job topic: {}", event);
                }

            } else {
                // bundle event started or updated
                boolean doIt = false;
                synchronized ( this.unloadedJobs ) {
                    if ( this.unloadedJobs.size() > 0 ) {
                        doIt = true;
                    }
                }
                if ( doIt ) {
                    final Thread t = new Thread() {

                        public void run() {
                            synchronized (unloadedJobs) {
                                Session s = null;
                                final Set<String> newUnloadedJobs = new HashSet<String>();
                                newUnloadedJobs.addAll(unloadedJobs);
                                try {
                                    s = createSession();
                                    for(String path : unloadedJobs ) {
                                        newUnloadedJobs.remove(path);
                                        try {
                                            if ( s.itemExists(path) ) {
                                                final Node eventNode = (Node) s.getItem(path);
                                                if ( !eventNode.isLocked() ) {
                                                    try {
                                                        final EventInfo info = new EventInfo();
                                                        info.event = readEvent(eventNode);
                                                        info.nodePath = path;
                                                        try {
                                                            queue.put(info);
                                                        } catch (InterruptedException e) {
                                                            // we ignore this exception as this should never occur
                                                            ignoreException(e);
                                                        }
                                                    } catch (ClassNotFoundException cnfe) {
                                                        newUnloadedJobs.add(path);
                                                        ignoreException(cnfe);
                                                    }
                                                }
                                            }
                                        } catch (RepositoryException re) {
                                            // we ignore this and readd
                                            newUnloadedJobs.add(path);
                                            ignoreException(re);
                                        }
                                    }
                                } catch (RepositoryException re) {
                                    // unable to create session, so we try it again next time
                                    ignoreException(re);
                                } finally {
                                    if ( s != null ) {
                                        s.logout();
                                    }
                                    unloadedJobs.clear();
                                    unloadedJobs.addAll(newUnloadedJobs);
                                }
                            }
                        }

                    };
                    t.start();
                }
            }
        }
    }

    /**
     * Create a unique node name for the job.
     */
    protected String getNodeName(Event event) {
        final String jobId = (String)event.getProperty(EventUtil.PROPERTY_JOB_ID);
        if ( jobId != null ) {
            final String jobTopic = ((String)event.getProperty(EventUtil.PROPERTY_JOB_TOPIC)).replace('/', '.');
            return jobTopic + " " + jobId.replace('/', '.');
        }

        return "Job " + UUID.randomUUID().toString();
    }

    /**
     * Process a job and unlock the node in the repository.
     * @param event The original event.
     * @param eventNode The node in the repository where the job is stored.
     */
    protected void processJob(Event event, Node eventNode, String lockToken)  {
        final boolean parallelProcessing = event.getProperty(EventUtil.PROPERTY_JOB_PARALLEL) != null;
        final String jobTopic = (String)event.getProperty(EventUtil.PROPERTY_JOB_TOPIC);
        boolean unlock = true;
        try {
            final Event jobEvent = this.getJobEvent(event, eventNode, lockToken);
            eventNode.setProperty(EventHelper.NODE_PROPERTY_PROCESSOR, this.applicationId);
            eventNode.save();
            final EventAdmin localEA = this.eventAdmin;
            if ( localEA != null ) {
                localEA.sendEvent(jobEvent);
                // do not unlock if sending was successful
                unlock = false;
            } else {
                this.logger.error("Job event can't be sent as no event admin is available.");
            }
        } catch (RepositoryException re) {
            // if an exception occurs, we just log
            this.logger.error("Exception during job processing.", re);
        } finally {
            if ( unlock ) {
                if ( !parallelProcessing ) {
                    synchronized ( this.processingMap ) {
                        this.processingMap.put(jobTopic, Boolean.FALSE);
                    }
                }
                // unlock node
                try {
                    eventNode.unlock();
                } catch (RepositoryException e) {
                    // if unlock fails, we silently ignore this
                    this.ignoreException(e);
                }
            }
        }
    }

    /**
     * Create the job event.
     * @param e
     * @return
     */
    protected Event getJobEvent(Event e, Node eventNode, String lockToken)
    throws RepositoryException {
        final String eventTopic = (String)e.getProperty(EventUtil.PROPERTY_JOB_TOPIC);
        final Dictionary<String, Object> properties = new Hashtable<String, Object>();
        final String[] propertyNames = e.getPropertyNames();
        for(int i=0; i<propertyNames.length; i++) {
            properties.put(propertyNames[i], e.getProperty(propertyNames[i]));
        }
        // put properties for finished job callback
        properties.put(EventUtil.JobStatusNotifier.CONTEXT_PROPERTY_NAME, new EventUtil.JobStatusNotifier.NotifierContext(this, eventNode.getPath(), lockToken));
        return new Event(eventTopic, properties);
    }

    /**
     * @see org.apache.sling.core.event.impl.JobPersistenceHandler#addNodeProperties(javax.jcr.Node, org.osgi.service.event.Event)
     */
    protected void addNodeProperties(Node eventNode, Event event)
    throws RepositoryException {
        super.addNodeProperties(eventNode, event);
        eventNode.setProperty(EventHelper.NODE_PROPERTY_TOPIC, (String)event.getProperty(EventUtil.PROPERTY_JOB_TOPIC));
        final String jobId = (String)event.getProperty(EventUtil.PROPERTY_JOB_ID);
        if ( jobId != null ) {
            eventNode.setProperty(EventHelper.NODE_PROPERTY_JOBID, jobId);
        }
    }

    /**
     * @see javax.jcr.observation.EventListener#onEvent(javax.jcr.observation.EventIterator)
     */
    public void onEvent(EventIterator iter) {
        // we create an own session here
        Session s = null;
        try {
            s = this.createSession();
            while ( iter.hasNext() ) {
                final javax.jcr.observation.Event event = iter.nextEvent();
                if ( event.getType() == javax.jcr.observation.Event.PROPERTY_CHANGED
                   || event.getType() == javax.jcr.observation.Event.PROPERTY_REMOVED) {
                    try {
                        final String propPath = event.getPath();
                        int pos = propPath.lastIndexOf('/');
                        final String nodePath = propPath.substring(0, pos);
                        final String propertyName = propPath.substring(pos+1);

                        // we are only interested in unlocks
                        if ( "jcr:lockOwner".equals(propertyName) ) {
                            final Node eventNode = (Node) s.getItem(nodePath);
                            if ( !eventNode.isLocked() ) {
                                try {
                                    final EventInfo info = new EventInfo();
                                    info.event = this.readEvent(eventNode);
                                    info.nodePath = nodePath;
                                    try {
                                        this.queue.put(info);
                                    } catch (InterruptedException e) {
                                        // we ignore this exception as this should never occur
                                        this.ignoreException(e);
                                    }
                                } catch (ClassNotFoundException cnfe) {
                                    // store path for lazy loading
                                    synchronized ( this.unloadedJobs ) {
                                        this.unloadedJobs.add(nodePath);
                                    }
                                    this.ignoreException(cnfe);
                                }
                            }
                        }
                    } catch (RepositoryException re) {
                        this.logger.error("Exception during jcr event processing.", re);
                    }
                }
            }
        } catch (RepositoryException re) {
            this.logger.error("Unable to create a session.", re);
        } finally {
            if ( s != null ) {
                s.logout();
            }
        }
    }

    /**
     * Load all active jobs from the repository.
     * @throws RepositoryException
     */
    protected void loadJobs() {
        try {
            final QueryManager qManager = this.writerSession.getWorkspace().getQueryManager();
            final StringBuffer buffer = new StringBuffer("/jcr:root");
            buffer.append(this.repositoryPath);
            buffer.append("//element(*, ");
            buffer.append(this.getEventNodeType());
            buffer.append(")");
            final Query q = qManager.createQuery(buffer.toString(), Query.XPATH);
            final NodeIterator result = q.execute().getNodes();
            while ( result.hasNext() ) {
                final Node eventNode = result.nextNode();
                if ( !eventNode.isLocked() ) {
                    final String nodePath = eventNode.getPath();
                    try {
                        final Event event = this.readEvent(eventNode);
                        final EventInfo info = new EventInfo();
                        info.event = event;
                        info.nodePath = nodePath;
                        try {
                            this.queue.put(info);
                        } catch (InterruptedException e) {
                            // we ignore this exception as this should never occur
                            this.ignoreException(e);
                        }
                    } catch (ClassNotFoundException cnfe) {
                        // store path for lazy loading
                        synchronized ( this.unloadedJobs ) {
                            this.unloadedJobs.add(nodePath);
                        }
                        this.ignoreException(cnfe);
                    } catch (RepositoryException re) {
                        this.logger.error("Unable to load stored job from " + nodePath, re);
                    }
                }
            }
        } catch (RepositoryException re) {
            this.logger.error("Exception during initial loading of stored jobs.", re);
        }
    }

    /**
     * @see org.apache.sling.event.EventUtil.JobStatusNotifier#finishedJob(org.osgi.service.event.Event, String, String, boolean)
     */
    public boolean finishedJob(Event job, String eventNodePath, String lockToken, boolean shouldReschedule) {
        boolean reschedule = shouldReschedule;
        if ( shouldReschedule ) {
            // check if we exceeded the number of retries
            if ( job.getProperty(EventUtil.PROPERTY_JOB_RETRIES) != null ) {
                int retries = (Integer) job.getProperty(EventUtil.PROPERTY_JOB_RETRIES);
                int retryCount = 0;
                if ( job.getProperty(EventUtil.PROPERTY_JOB_RETRY_COUNT) != null ) {
                    retryCount = (Integer)job.getProperty(EventUtil.PROPERTY_JOB_RETRY_COUNT);
                }
                retryCount++;
                if ( retryCount >= retries ) {
                    reschedule = false;
                }
                // update event with retry count
                final Dictionary<String, Object> newProperties;
                // create a new dictionary
                newProperties = new Hashtable<String, Object>();
                final String[] names = job.getPropertyNames();
                for(int i=0; i<names.length; i++ ) {
                    newProperties.put(names[i], job.getProperty(names[i]));
                }
                newProperties.put(EventUtil.PROPERTY_JOB_RETRY_COUNT, retryCount);
                job = new Event(job.getTopic(), newProperties);
            }
        }
        final boolean parallelProcessing = job.getProperty(EventUtil.PROPERTY_JOB_PARALLEL) != null;
        Session s = null;
        try {
            s = this.createSession();
            // remove lock token from shared session and add it to current session
            this.backgroundSession.removeLockToken(lockToken);
            s.addLockToken(lockToken);
            final Node eventNode = (Node) s.getItem(eventNodePath);
            try {
                if ( !reschedule ) {
                    // remove node from repository
                    final Node parentNode = eventNode.getParent();
                    eventNode.remove();
                    parentNode.save();
                    lockToken = null;
                }
            } catch (RepositoryException re) {
                // if an exception occurs, we just log
                this.logger.error("Exception during job finishing.", re);
            } finally {
                if ( !parallelProcessing) {
                    final String jobTopic = (String)job.getProperty(EventUtil.PROPERTY_JOB_TOPIC);
                    synchronized ( this.processingMap ) {
                        this.processingMap.put(jobTopic, Boolean.FALSE);
                    }
                }
                if ( lockToken != null ) {
                    // unlock node
                    try {
                        eventNode.unlock();
                    } catch (RepositoryException e) {
                        // if unlock fails, we silently ignore this
                        this.ignoreException(e);
                    }
                }
            }
            if ( reschedule ) {
                final EventInfo info = new EventInfo();
                try {
                    info.event = job;
                    info.nodePath = eventNode.getPath();
                } catch (RepositoryException e) {
                    // this should never happen
                    this.ignoreException(e);
                }
                // delay rescheduling?
                if ( job.getProperty(EventUtil.PROPERTY_JOB_RETRY_DELAY) != null ) {
                    final long delay = (Long)job.getProperty(EventUtil.PROPERTY_JOB_RETRY_DELAY);
                    final Thread t = new Thread() {
                        public void run() {
                            try {
                                Thread.sleep(delay);
                            } catch (InterruptedException e) {
                                // this should never happen
                                ignoreException(e);
                            }
                            try {
                                queue.put(info);
                            } catch (InterruptedException e) {
                                // this should never happen
                                ignoreException(e);
                            }
                        }
                    };
                    t.start();
                } else {
                    // put directly into queue
                    try {
                        this.queue.put(info);
                    } catch (InterruptedException e) {
                        // this should never happen
                        this.ignoreException(e);
                    }
                }
            }
            if ( !shouldReschedule ) {
                return true;
            }
            return reschedule;
        } catch (RepositoryException re) {
            this.logger.error("Unable to create new session.", re);
            return false;
        } finally {
            if ( s != null ) {
                s.logout();
            }
        }
    }

    /**
     * Search for active nodes
     * @param topic
     * @return
     * @throws RepositoryException
     */
    protected Collection<Event> queryCurrentJobs(String topic, boolean locked)  {
        // we create a new session
        Session s = null;
        final List<Event> jobs = new ArrayList<Event>();
        try {
            s = this.createSession();
            final QueryManager qManager = s.getWorkspace().getQueryManager();
            final StringBuffer buffer = new StringBuffer("/jcr:root");
            buffer.append(this.repositoryPath);
            buffer.append("//element(*, ");
            buffer.append(this.getEventNodeType());
            buffer.append(")");
            if ( topic != null ) {
                buffer.append(" [");
                buffer.append(EventHelper.NODE_PROPERTY_TOPIC);
                buffer.append(" = '");
                buffer.append(topic);
                buffer.append("'");
            }
            if ( locked ) {
                buffer.append(" and ");
                buffer.append("jcr:lockOwner");
            }
            buffer.append("]");
            final Query q = qManager.createQuery(buffer.toString(), Query.XPATH);
            final NodeIterator iter = q.execute().getNodes();
            while ( iter.hasNext() ) {
                final Node eventNode = iter.nextNode();
                try {
                    final Event event = this.readEvent(eventNode);
                    jobs.add(event);
                } catch (ClassNotFoundException cnfe) {
                    // in the case of a class not found exception we just ignore the exception
                    this.ignoreException(cnfe);
                }
            }
        } catch (RepositoryException e) {
            // in the case of an error, we return an empty list
            this.ignoreException(e);
        } finally {
            if ( s != null) {
                s.logout();
            }
        }
        return jobs;
    }

    /**
     * @see org.apache.sling.event.JobStatusProvider#getCurrentJobs(java.lang.String)
     */
    public Collection<Event> getCurrentJobs(String topic) {
        return this.queryCurrentJobs(topic, true);
    }

    /**
     * @see org.apache.sling.event.JobStatusProvider#scheduledJobs(java.lang.String)
     */
    public Collection<Event> scheduledJobs(String topic) {
        return this.queryCurrentJobs(topic, false);
    }
}
