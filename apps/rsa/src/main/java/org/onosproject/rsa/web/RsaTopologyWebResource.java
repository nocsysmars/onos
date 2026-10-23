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
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.Link;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.link.LinkService;
import org.onosproject.rest.AbstractWebResource;
import org.onosproject.rsa.RsaService;
import org.onosproject.rsa.SpectrumTable.LinkSpectrum;
import org.onosproject.rsa.SpectrumTable.SpectrumSnapshot;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;

/**
 * Read-only REST resource exposing the topology (nodes + links) overlaid with
 * current spectrum utilization.
 *
 * <ul>
 *   <li>{@code GET /onos/rsa/topology} — returns {@code nodes[]} (all devices
 *       from {@link DeviceService}) and {@code links[]} (all active links from
 *       {@link LinkService}) with per-link {@code capacity}/{@code used}/{@code
 *       utilization} sourced from the RSA spectrum snapshot.</li>
 * </ul>
 *
 * <p>All devices are included as nodes (not restricted to ROADM/optical). A
 * link's {@code used} is {@code slotCount - freeSlotCount} of the matching
 * undirected spectrum entry, or 0 when the link has no occupancy entry.</p>
 */
@Path("topology")
public class RsaTopologyWebResource extends AbstractWebResource {

    /**
     * Returns the topology overlaid with spectrum utilization.
     *
     * @return 200 OK with {@code {"nodes": [...], "links": [...]}}
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getTopology() {
        RsaService rsa = get(RsaService.class);
        DeviceService deviceService = get(DeviceService.class);
        LinkService linkService = get(LinkService.class);

        int slotCount = rsa.slotCount();
        SpectrumSnapshot snapshot = rsa.spectrumSnapshot();

        // Index used-slot count by undirected endpoint pair so each ONOS link
        // can be matched against its spectrum occupancy entry.
        Map<String, Integer> usedByLink = new HashMap<>();
        for (LinkSpectrum ls : snapshot.links()) {
            usedByLink.put(undirectedKey(ls.src(), ls.dst()),
                    slotCount - ls.freeSlotCount());
        }

        ObjectNode root = mapper().createObjectNode();

        ArrayNode nodes = root.putArray("nodes");
        for (Device device : deviceService.getDevices()) {
            ObjectNode n = mapper().createObjectNode();
            String id = device.id().toString();
            n.put("id", id);
            String name = device.annotations().value(AnnotationKeys.NAME);
            n.put("name", name != null ? name : id);
            n.put("type", device.type().toString());
            nodes.add(n);
        }

        ArrayNode links = root.putArray("links");
        for (Link link : linkService.getActiveLinks()) {
            ObjectNode l = mapper().createObjectNode();
            l.put("src", link.src().toString());
            l.put("dst", link.dst().toString());
            int used = usedByLink.getOrDefault(undirectedKey(link.src(), link.dst()), 0);
            l.put("capacity", slotCount);
            l.put("used", used);
            l.put("utilization", slotCount == 0 ? 0.0 : (double) used / slotCount);
            links.add(l);
        }
        return ok(root).build();
    }

    /**
     * Builds a direction-independent key for a pair of connect points so a link
     * and its reverse map to the same spectrum entry (matching the SpectrumTable
     * undirected convention).
     */
    private static String undirectedKey(ConnectPoint a, ConnectPoint b) {
        String as = a.toString();
        String bs = b.toString();
        return as.compareTo(bs) <= 0 ? as + "|" + bs : bs + "|" + as;
    }
}
