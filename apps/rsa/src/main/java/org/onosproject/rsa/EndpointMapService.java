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

import java.util.Collection;

/**
 * Service maintaining the {@code podRef -> (owsDevice, owsPort)} mapping fed by
 * the landing-controller (see docs/design-landing-controller.md §6).
 *
 * <p>The mapping lets {@code POST /onos/rsa/path} accept a pod-identity demand
 * ({@code srcPodRef}/{@code dstPodRef}) instead of a raw optical device/port,
 * translating each podRef into a {@link org.onosproject.net.ConnectPoint}
 * before reusing the existing {@link RsaService#assign} core. The RSA routing
 * and spectrum-assignment logic is unchanged; this is purely a translation
 * layer in front of it.</p>
 */
public interface EndpointMapService {

    /**
     * Immutable mapping entry for a single pod.
     */
    final class Entry {
        private final String podRef;
        private final String nodeName;
        private final String fabricIP;
        private final String owsDevice;
        private final String owsPort;

        public Entry(String podRef, String nodeName, String fabricIP,
                     String owsDevice, String owsPort) {
            this.podRef = podRef;
            this.nodeName = nodeName;
            this.fabricIP = fabricIP;
            this.owsDevice = owsDevice;
            this.owsPort = owsPort;
        }

        public String podRef() {
            return podRef;
        }

        public String nodeName() {
            return nodeName;
        }

        public String fabricIP() {
            return fabricIP;
        }

        public String owsDevice() {
            return owsDevice;
        }

        public String owsPort() {
            return owsPort;
        }
    }

    /**
     * Inserts or replaces the mapping for {@code podRef}.
     *
     * @param podRef    pod reference ({@code namespace/name})
     * @param nodeName  K8s node the pod landed on
     * @param fabricIP  node fabric IP used to correlate with ONOS topology
     * @param owsDevice OWS/ROADM device id serving that node
     * @param owsPort   OWS/ROADM port serving that node
     */
    void put(String podRef, String nodeName, String fabricIP,
             String owsDevice, String owsPort);

    /**
     * Removes the mapping for {@code podRef}.
     *
     * @param podRef pod reference ({@code namespace/name})
     * @return true if a mapping existed and was removed, false otherwise
     */
    boolean remove(String podRef);

    /**
     * Returns the mapping for {@code podRef}, or null if none.
     *
     * @param podRef pod reference ({@code namespace/name})
     * @return the stored entry, or null
     */
    Entry get(String podRef);

    /**
     * Returns all current mappings.
     *
     * @return a snapshot collection of all entries
     */
    Collection<Entry> listAll();
}
