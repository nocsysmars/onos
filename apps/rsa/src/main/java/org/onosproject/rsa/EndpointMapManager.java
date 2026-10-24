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

import com.google.common.collect.ImmutableList;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * In-memory, thread-safe implementation of {@link EndpointMapService}.
 *
 * <p>Holds the {@code podRef -> Entry} mapping in a {@link ConcurrentHashMap}.
 * The map is volatile PoC state (lost on pod restart), mirroring the
 * spectrum/topology state held by {@link RsaManager}; the landing-controller
 * re-pushes mappings on resync.</p>
 */
@Component(immediate = true, service = EndpointMapService.class)
public class EndpointMapManager implements EndpointMapService {

    private final Logger log = getLogger(getClass());

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    @Activate
    protected void activate() {
        log.info("RSA endpoint-map service started");
    }

    @Deactivate
    protected void deactivate() {
        entries.clear();
        log.info("RSA endpoint-map service stopped");
    }

    @Override
    public void put(String podRef, String nodeName, String fabricIP,
                    String owsDevice, String owsPort) {
        entries.put(podRef, new Entry(podRef, nodeName, fabricIP, owsDevice, owsPort));
        log.info("endpoint-map put {} -> {}:{} (node={}, fabricIP={})",
                podRef, owsDevice, owsPort, nodeName, fabricIP);
    }

    @Override
    public boolean remove(String podRef) {
        boolean removed = entries.remove(podRef) != null;
        if (removed) {
            log.info("endpoint-map remove {}", podRef);
        }
        return removed;
    }

    @Override
    public Entry get(String podRef) {
        return entries.get(podRef);
    }

    @Override
    public Collection<Entry> listAll() {
        return ImmutableList.copyOf(entries.values());
    }
}
