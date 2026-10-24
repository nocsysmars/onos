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
import org.onosproject.rsa.EndpointMapService;
import org.slf4j.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;

import static org.onlab.util.Tools.nullIsIllegal;
import static org.onlab.util.Tools.readTreeFromStream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * REST resource for the RSA endpoint-map (podRef -&gt; OWS port) mapping fed by
 * the landing-controller (see docs/design-landing-controller.md §6).
 *
 * <ul>
 *   <li>{@code PUT /onos/rsa/endpoint-map} — insert/replace a mapping
 *       {@code {podRef, nodeName, fabricIP, owsDevice, owsPort}}.</li>
 *   <li>{@code DELETE /onos/rsa/endpoint-map/{podRef}} — remove a mapping;
 *       {@code podRef} may contain a slash ({@code namespace/name}).</li>
 *   <li>{@code GET /onos/rsa/endpoint-map} — list all mappings.</li>
 * </ul>
 */
@Path("endpoint-map")
public class RsaEndpointMapWebResource extends AbstractWebResource {

    private static final Logger log = getLogger(RsaEndpointMapWebResource.class);

    private static final String POD_REF = "podRef";
    private static final String NODE_NAME = "nodeName";
    private static final String FABRIC_IP = "fabricIP";
    private static final String OWS_DEVICE = "owsDevice";
    private static final String OWS_PORT = "owsPort";
    private static final String MISSING = " member is required";

    /**
     * Inserts or replaces a podRef mapping.
     *
     * @param stream input JSON {podRef, nodeName, fabricIP, owsDevice, owsPort}
     * @return 200 OK
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response putMapping(InputStream stream) {
        try {
            ObjectNode root = readTreeFromStream(mapper(), stream);

            String podRef = nullIsIllegal(root.get(POD_REF), POD_REF + MISSING).asText();
            String nodeName = nullIsIllegal(root.get(NODE_NAME), NODE_NAME + MISSING).asText();
            String fabricIP = nullIsIllegal(root.get(FABRIC_IP), FABRIC_IP + MISSING).asText();
            String owsDevice = nullIsIllegal(root.get(OWS_DEVICE), OWS_DEVICE + MISSING).asText();
            String owsPort = nullIsIllegal(root.get(OWS_PORT), OWS_PORT + MISSING).asText();

            get(EndpointMapService.class).put(podRef, nodeName, fabricIP, owsDevice, owsPort);

            ObjectNode body = encodeEntry(
                    new EndpointMapService.Entry(podRef, nodeName, fabricIP, owsDevice, owsPort));
            return Response.ok(body).build();
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
    }

    /**
     * Removes a podRef mapping.
     *
     * @param podRef pod reference ({@code namespace/name}; may contain a slash)
     * @return 204 NO CONTENT, or 404 NOT FOUND if unknown
     */
    @DELETE
    @Path("{podRef:.+}")
    public Response deleteMapping(@PathParam("podRef") String podRef) {
        boolean removed = get(EndpointMapService.class).remove(podRef);
        if (!removed) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    /**
     * Lists all podRef mappings.
     *
     * @return 200 OK with a JSON array of {podRef, nodeName, fabricIP, owsDevice, owsPort}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listMappings() {
        ArrayNode array = mapper().createArrayNode();
        for (EndpointMapService.Entry entry : get(EndpointMapService.class).listAll()) {
            array.add(encodeEntry(entry));
        }
        ObjectNode body = mapper().createObjectNode();
        body.set("endpointMap", array);
        return ok(body).build();
    }

    private ObjectNode encodeEntry(EndpointMapService.Entry entry) {
        ObjectNode node = mapper().createObjectNode();
        node.put(POD_REF, entry.podRef());
        node.put(NODE_NAME, entry.nodeName());
        node.put(FABRIC_IP, entry.fabricIP());
        node.put(OWS_DEVICE, entry.owsDevice());
        node.put(OWS_PORT, entry.owsPort());
        return node;
    }
}
