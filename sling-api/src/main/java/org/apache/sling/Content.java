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
package org.apache.sling;

/**
 * The <code>Content</code> interface defines the API to be implemented by all
 * content to be acted upon by the Sling framework.
 *
 * @ocm.mapped discriminator="false"
 */
public interface Content {

    /**
     * Returns the name of the servlet responsible for the handling and
     * presentation of this content object. This method must never return
     * <code>null</code>.
     *
     * @return the name of the handling Servlet.
     */
    String getServletName();

    /**
     * Returns the path of this content object in the persistence layer from
     * where the object has been loaded.
     */
    String getPath();
}
