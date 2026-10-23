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
import org.onosproject.rest.AbstractWebResource;
import org.onosproject.rsa.RsaService;
import org.onosproject.rsa.SpectrumTable.LinkSpectrum;
import org.onosproject.rsa.SpectrumTable.SpectrumSnapshot;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Read-only REST resource exposing aggregate RSA key performance indicators.
 *
 * <ul>
 *   <li>{@code GET /onos/rsa/stats} — returns network-wide spectrum KPIs
 *       computed from the current spectrum snapshot.</li>
 * </ul>
 *
 * <p>KPI formulas (all derived from the spectrum snapshot, so {@code totalLinks}
 * counts only links that currently have occupancy entries):</p>
 * <ul>
 *   <li>{@code totalLinks} = number of links in the snapshot.</li>
 *   <li>{@code totalSlots} = {@code totalLinks * slotCount}.</li>
 *   <li>{@code usedSlots} = sum over links of {@code (slotCount - freeSlotCount)}.</li>
 *   <li>{@code overallUtilization} = {@code usedSlots / totalSlots}, or 0.0 when
 *       {@code totalSlots == 0}.</li>
 *   <li>{@code activeRequests} = {@code listActive().size()}.</li>
 *   <li>{@code blockedCount} = {@code blockedCount()}.</li>
 *   <li>{@code maxContiguousFree} = max over links of {@code maxContiguousFree};
 *       0 when there are no links.</li>
 *   <li>{@code averageFragmentation} = average over links with
 *       {@code freeSlotCount > 0} of {@code 1 - maxContiguousFree / freeSlotCount};
 *       0.0 when no such link exists.</li>
 * </ul>
 */
@Path("stats")
public class RsaStatsWebResource extends AbstractWebResource {

    /**
     * Returns aggregate RSA KPIs.
     *
     * @return 200 OK with the KPI object
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStats() {
        RsaService service = get(RsaService.class);
        int slotCount = service.slotCount();
        SpectrumSnapshot snapshot = service.spectrumSnapshot();

        int totalLinks = snapshot.links().size();
        long totalSlots = (long) totalLinks * slotCount;
        long usedSlots = 0;
        int maxContiguousFree = 0;
        double fragmentationSum = 0.0;
        int fragmentationLinks = 0;

        for (LinkSpectrum link : snapshot.links()) {
            int used = slotCount - link.freeSlotCount();
            usedSlots += used;
            maxContiguousFree = Math.max(maxContiguousFree, link.maxContiguousFree());
            if (link.freeSlotCount() > 0) {
                fragmentationSum += 1.0 - (double) link.maxContiguousFree() / link.freeSlotCount();
                fragmentationLinks++;
            }
        }

        double overallUtilization = totalSlots == 0 ? 0.0 : (double) usedSlots / totalSlots;
        double averageFragmentation = fragmentationLinks == 0 ? 0.0 : fragmentationSum / fragmentationLinks;

        ObjectNode root = mapper().createObjectNode();
        root.put("totalLinks", totalLinks);
        root.put("totalSlots", totalSlots);
        root.put("usedSlots", usedSlots);
        root.put("overallUtilization", overallUtilization);
        root.put("activeRequests", service.listActive().size());
        root.put("blockedCount", service.blockedCount());
        root.put("maxContiguousFree", maxContiguousFree);
        root.put("averageFragmentation", averageFragmentation);
        return ok(root).build();
    }
}
