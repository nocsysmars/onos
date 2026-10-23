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

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.rest.AbstractWebResource;
import org.onosproject.rsa.RsaResult;
import org.onosproject.rsa.RsaService;
import org.slf4j.Logger;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.io.IOException;
import java.io.InputStream;

import static org.onlab.util.Tools.nullIsIllegal;
import static org.onlab.util.Tools.readTreeFromStream;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * REST resource for RSA (Routing and Spectrum Assignment) requests.
 *
 * <ul>
 *   <li>{@code POST /onos/rsa/path} — submit a demand
 *       {@code {srcDevice, srcPort, dstDevice, dstPort, slots}}; returns the
 *       chosen path + assigned spectrum + requestId, or a blocked result.</li>
 *   <li>{@code DELETE /onos/rsa/path/{id}} — release a request (withdraw intent,
 *       reclaim slots).</li>
 *   <li>{@code GET /onos/rsa/path/{id}} — inspect an accepted request.</li>
 * </ul>
 */
@Path("path")
public class RsaPathWebResource extends AbstractWebResource {

    private static final Logger log = getLogger(RsaPathWebResource.class);

    private static final String SRC_DEVICE = "srcDevice";
    private static final String SRC_PORT = "srcPort";
    private static final String DST_DEVICE = "dstDevice";
    private static final String DST_PORT = "dstPort";
    private static final String SLOTS = "slots";
    private static final String MISSING = " member is required";

    @Context
    private UriInfo uriInfo;

    /**
     * Submits an RSA request.
     *
     * @param stream input JSON {srcDevice, srcPort, dstDevice, dstPort, slots}
     * @return 201 CREATED with the assignment, or 409 CONFLICT when blocked
     * @onos.rsModel RsaPathRequest
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createPath(InputStream stream) {
        try {
            ObjectNode root = readTreeFromStream(mapper(), stream);

            String srcDevice = nullIsIllegal(root.get(SRC_DEVICE), SRC_DEVICE + MISSING).asText();
            String srcPort = nullIsIllegal(root.get(SRC_PORT), SRC_PORT + MISSING).asText();
            String dstDevice = nullIsIllegal(root.get(DST_DEVICE), DST_DEVICE + MISSING).asText();
            String dstPort = nullIsIllegal(root.get(DST_PORT), DST_PORT + MISSING).asText();
            int slots = nullIsIllegal(root.get(SLOTS), SLOTS + MISSING).asInt();

            ConnectPoint src = new ConnectPoint(DeviceId.deviceId(srcDevice), PortNumber.portNumber(srcPort));
            ConnectPoint dst = new ConnectPoint(DeviceId.deviceId(dstDevice), PortNumber.portNumber(dstPort));

            RsaResult result = get(RsaService.class).assign(src, dst, slots);

            if (result.isBlocked()) {
                ObjectNode body = mapper().createObjectNode();
                body.put("blocked", true);
                body.put("reason", result.reason());
                body.put("src", src.toString());
                body.put("dst", dst.toString());
                return Response.status(Response.Status.CONFLICT).entity(body).build();
            }

            ObjectNode body = encodeResult(result);
            UriBuilder location = uriInfo.getBaseUriBuilder()
                    .path("path")
                    .path(result.requestId());
            return Response.created(location.build()).entity(body).build();
        } catch (IOException ioe) {
            throw new IllegalArgumentException(ioe);
        }
    }

    /**
     * Returns an accepted RSA request.
     *
     * @param id request identifier
     * @return 200 OK with the assignment, or 404 NOT FOUND
     */
    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPath(@PathParam("id") String id) {
        RsaResult result = get(RsaService.class).get(id);
        if (result == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return ok(encodeResult(result)).build();
    }

    /**
     * Releases an RSA request: withdraws its intent and reclaims its slots.
     *
     * @param id request identifier
     * @return 204 NO CONTENT, or 404 NOT FOUND if unknown
     */
    @DELETE
    @Path("{id}")
    public Response deletePath(@PathParam("id") String id) {
        boolean released = get(RsaService.class).release(id);
        if (!released) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.noContent().build();
    }

    private ObjectNode encodeResult(RsaResult result) {
        return RsaJson.encodeResult(mapper(), result);
    }
}
