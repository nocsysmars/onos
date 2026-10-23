/*
 * Copyright 2016-present Open Networking Foundation
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

package org.onosproject.provider.nil.cli;

import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Link;
import org.onosproject.provider.nil.NullProviders;
import org.onosproject.provider.nil.TopologySimulator;

import static org.onosproject.net.ConnectPoint.deviceConnectPoint;

/**
 * Adds a simulated link between two explicit connect points to the active
 * topology simulation.
 *
 * <p>Unlike {@code null-create-link}, which resolves source/destination by the
 * custom simulator's device <em>name</em> and auto-picks free edge ports (so it
 * only works between null-simulated devices), this command takes two explicit
 * {@link ConnectPoint}s in {@code deviceId/port} form (e.g.
 * {@code rest:10.0.0.1:8080/2}). It then reports the link via the null
 * provider's {@code LinkProviderService} by delegating to
 * {@link TopologySimulator#createLink(ConnectPoint, ConnectPoint, Link.Type, boolean)}.
 *
 * <p>Purpose (rsa-real-roadm / FEAT-001): inject OPTICAL links between the OCh
 * ports of real {@code rest:} opticalemulator ROADM devices. Those devices have
 * no LinkDiscovery behaviour and the emulated optical ROADMs never emit
 * LLDP/BDDP, so neither LLDP link discovery nor ONOS 2.7's
 * NetworkConfigLinksProvider (which only whitelists LLDP-discovered links) can
 * ever create ROADM-to-ROADM OPTICAL links. This command provides the manual
 * adjacency configuration path that cross-domain optical links commonly rely on
 * in the real world.
 *
 * <p>This class is additive only: it does not change any existing null-provider
 * behaviour or existing commands. It requires an active topology simulation
 * (e.g. {@code null-simulation start custom}) so that
 * {@link NullProviders#currentSimulator()} is non-null.
 */
@Service
@Command(scope = "onos", name = "null-create-link-cp",
        description = "Adds a simulated link between two explicit connect points "
                + "(deviceId/port) to the active topology simulation")
public class CreateNullLinkCp extends CreateNullEntity {

    @Argument(index = 0, name = "type",
            description = "Link type, e.g. direct, indirect, optical",
            required = true)
    String type = null;

    @Argument(index = 1, name = "srcConnectPoint",
            description = "Source connect point, e.g. rest:10.0.0.1:8080/2",
            required = true)
    String srcCp = null;

    @Argument(index = 2, name = "dstConnectPoint",
            description = "Destination connect point, e.g. rest:10.0.0.2:8080/2",
            required = true)
    String dstCp = null;

    @Option(name = "-u", aliases = "--unidirectional", description = "Unidirectional link only")
    private boolean unidirectional = false;

    @Override
    protected void doExecute() {
        NullProviders service = get(NullProviders.class);

        TopologySimulator simulator = service.currentSimulator();
        if (simulator == null) {
            error("\u001B[1;31mNo active topology simulation. "
                    + "Start one first, e.g. 'null-simulation start custom'.\u001B[0m");
            return;
        }

        ConnectPoint one;
        ConnectPoint two;
        try {
            one = deviceConnectPoint(srcCp);
            two = deviceConnectPoint(dstCp);
        } catch (RuntimeException e) {
            error("\u001B[1;31mInvalid connect point (expected deviceId/port): %s\u001B[0m",
                    e.getMessage());
            return;
        }

        simulator.createLink(one, two, Link.Type.valueOf(type.toUpperCase()), !unidirectional);
        print("Created %s link %s <-> %s", type.toUpperCase(), one, two);
    }

}
