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
package org.apache.sling.api.resource;

import javax.servlet.http.HttpServletResponse;

import org.apache.sling.api.SlingException;

/**
 * An Exception that causes Sling to return a 404 (NOT FOUND) status code. This
 * exception should not be caught but rather let be handed up the call stack up
 * to the Sling error and exception handling.
 * <p>
 * The advantage of using this exception over the
 * <code>HttpServletResponse.sendError</code> methods is that the request can
 * be aborted immediately all the way up in the call stack and that in addition
 * to the status code and an optional message a <code>Throwable</code> may be
 * supplied providing more information.
 */
public class ResourceNotFoundException extends SlingException {

    private final int statusCode;

    public ResourceNotFoundException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ResourceNotFoundException(int statusCode, String message,
            Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    }

    public int getStatusCode() {
        return statusCode;
    }
}
