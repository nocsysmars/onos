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
import org.onosproject.rsa.RsaService;
import org.onosproject.rsa.SpectrumTable.LinkSpectrum;
import org.onosproject.rsa.SpectrumTable.ReservationView;
import org.onosproject.rsa.SpectrumTable.SpectrumSnapshot;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Read-only REST resource exposing per-link spectrum occupancy.
 *
 * <ul>
 *   <li>{@code GET /onos/rsa/spectrum} — returns {@code slotCount} and, per
 *       undirected link that has occupancy, its occupied slots, free slot
 *       count, longest contiguous free run, and the reservations held on
 *       it.</li>
 * </ul>
 *
 * <p>Link endpoints are rendered via {@link Object#toString()} (keys are
 * undirected, matching the SpectrumTable convention).</p>
 */
@Path("spectrum")
public class RsaSpectrumWebResource extends AbstractWebResource {

    /**
     * Returns the current per-link spectrum occupancy snapshot.
     *
     * @return 200 OK with the spectrum snapshot
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSpectrum() {
        RsaService service = get(RsaService.class);
        SpectrumSnapshot snapshot = service.spectrumSnapshot();

        ObjectNode root = mapper().createObjectNode();
        root.put("slotCount", service.slotCount());

        ArrayNode links = root.putArray("links");
        for (LinkSpectrum link : snapshot.links()) {
            ObjectNode l = mapper().createObjectNode();
            l.put("src", link.src().toString());
            l.put("dst", link.dst().toString());

            ArrayNode occupied = l.putArray("occupiedSlots");
            for (Integer slot : link.occupiedSlots()) {
                occupied.add(slot);
            }

            l.put("freeSlotCount", link.freeSlotCount());
            l.put("maxContiguousFree", link.maxContiguousFree());

            ArrayNode reservations = l.putArray("reservations");
            for (ReservationView r : link.reservations()) {
                ObjectNode rn = mapper().createObjectNode();
                rn.put("requestId", r.requestId());
                rn.put("startSlot", r.startSlot());
                rn.put("width", r.width());
                reservations.add(rn);
            }

            links.add(l);
        }
        return ok(root).build();
    }
}
