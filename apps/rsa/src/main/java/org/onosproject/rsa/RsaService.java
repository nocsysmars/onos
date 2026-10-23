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

package org.onosproject.rsa;

import org.onosproject.net.ConnectPoint;

/**
 * Service providing Routing and Spectrum Assignment (RSA) decisions.
 *
 * <p>Given a connectivity demand (source/destination {@link ConnectPoint} and a
 * number of 50 GHz slots), the service selects a route using k-shortest-path and
 * assigns a contiguous spectrum segment using First-Fit, enforcing the three
 * classic RSA constraints (spectrum contiguity, path continuity and
 * non-overlap). The resulting decision is carried by an ONOS intent.</p>
 */
public interface RsaService {

    /**
     * Computes an RSA decision for the given demand and, if feasible, submits
     * the corresponding intent and records the spectrum occupancy.
     *
     * @param src   source connect point (device/port)
     * @param dst   destination connect point (device/port)
     * @param slots number of 50 GHz slots requested
     * @return the RSA result (either an accepted assignment or a blocked result)
     */
    RsaResult assign(ConnectPoint src, ConnectPoint dst, int slots);

    /**
     * Releases a previously accepted RSA request: withdraws its intent and
     * reclaims all spectrum slots it occupied.
     *
     * @param requestId request identifier returned by {@link #assign}
     * @return true if the request existed and was released, false if unknown
     */
    boolean release(String requestId);

    /**
     * Returns the result of a previously accepted request, or null if unknown.
     *
     * @param requestId request identifier
     * @return the stored result, or null
     */
    RsaResult get(String requestId);

    /**
     * Returns all currently-accepted, not-yet-released results.
     *
     * @return an immutable collection of active RSA results
     */
    java.util.Collection<RsaResult> listActive();

    /**
     * Returns the number of 50 GHz slots per link in the spectrum table.
     *
     * @return slot count per link
     */
    int slotCount();

    /**
     * Returns a point-in-time snapshot of current spectrum occupancy.
     *
     * @return current spectrum snapshot
     */
    SpectrumTable.SpectrumSnapshot spectrumSnapshot();

    /**
     * Returns the total number of assign requests that have been blocked.
     *
     * @return blocked request count
     */
    long blockedCount();
}
