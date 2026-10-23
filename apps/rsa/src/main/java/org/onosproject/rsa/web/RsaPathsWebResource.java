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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.rest.AbstractWebResource;
import org.onosproject.rsa.RsaResult;
import org.onosproject.rsa.RsaService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Read-only REST resource listing all currently-active RSA requests.
 *
 * <ul>
 *   <li>{@code GET /onos/rsa/paths} — returns {@code {"paths": [ ... ]}} where
 *       each entry is the same object shape as a single
 *       {@code GET /onos/rsa/path/{id}} result.</li>
 * </ul>
 */
@Path("paths")
public class RsaPathsWebResource extends AbstractWebResource {

    /**
     * Returns all currently-active RSA requests.
     *
     * @return 200 OK with {@code {"paths": [ <result>, ... ]}}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPaths() {
        ObjectNode root = mapper().createObjectNode();
        ArrayNode paths = root.putArray("paths");
        for (RsaResult result : get(RsaService.class).listActive()) {
            paths.add(RsaJson.encodeResult(mapper(), result));
        }
        return ok(root).build();
    }
}
