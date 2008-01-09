/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.servlets.standard;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

import javax.servlet.ServletException;

import org.apache.sling.api.SlingException;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;

/**
 * The <code>FolderServlet</code> handles nt:folder nodes
 *
 * @scr.component immediate="true" metatype="false"
 * @scr.property name="service.description"
 *          value="Servlet to handle nt:folder nodes"
 * @scr.property name="service.vendor" value="The Apache Software Foundation"
 * @scr.property name="sling.core.resourceTypes" value="nt:folder"
 * @scr.service
 */
public class FolderServlet extends SlingAllMethodsServlet {

    @Override
    protected void doGet(SlingHttpServletRequest request,
            SlingHttpServletResponse response) throws ServletException,
            IOException {

        Resource resource = request.getResource();
        FolderObject content = resource.adaptTo(FolderObject.class);
        if (content == null) {
            throw new SlingException("Missing mapped object for folder "
                + resource.getPath());
        }

        response.setContentType("text/html");
        PrintWriter pw = response.getWriter();
        pw.println("<html><head>");
        pw.println("<title>" + content.getPath() + "</title>");
        pw.println("</head><body bgcolor='white'>");
        pw.println("<h1>Contents of <code>" + content.getPath() + "</code></h1>");
        pw.println("<ul>");

        try {
            Iterator<Resource> entries = request.getResourceResolver().listChildren(resource);
            while (entries.hasNext()) {
                Resource entry = entries.next();
                pw.println("<li>" + entry.getPath() + "</li>");
            }
        } catch (SlingException ce) {
            // TODO: handle
        }

        pw.println("</ul>");
        pw.println("</body></html>");
    }
}
