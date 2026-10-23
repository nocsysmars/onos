/*
 * Copyright 2024-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.rsa.web;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;

/**
 * CORS response filter for the RSA REST surface.
 *
 * <p>Stamps permissive CORS headers on every response so an external web UI
 * (e.g. served behind nginx) can read the RSA endpoints from the browser. This
 * filter is registered solely in {@link RsaWebApplication#getClasses()}, so its
 * scope is the {@code /onos/rsa} context only and it does not affect any other
 * ONOS REST API. Follows the {@code ContainerResponseFilter} registration
 * pattern used by {@code CoreWebApplication}/{@code AuditFilter}.</p>
 */
@Provider
public class RsaCorsFilter implements ContainerResponseFilter {

    /** Allowed origin; permissive by default for the read-only RSA surface. */
    private static final String ALLOW_ORIGIN = "*";
    private static final String ALLOW_METHODS = "GET, POST, DELETE, OPTIONS";
    private static final String ALLOW_HEADERS = "Content-Type, Authorization";

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) {
        responseContext.getHeaders().putSingle("Access-Control-Allow-Origin", ALLOW_ORIGIN);
        responseContext.getHeaders().putSingle("Access-Control-Allow-Methods", ALLOW_METHODS);
        responseContext.getHeaders().putSingle("Access-Control-Allow-Headers", ALLOW_HEADERS);
    }
}
