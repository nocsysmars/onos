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
package org.onosproject.drivers.opticalemulator.roadm.rest;

import org.onosproject.net.DeviceId;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.flow.DefaultFlowEntry;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleProgrammable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Emulated flow-rule programmable behaviour for the Optical Emulator (Mininet-Optical) ROADM.
 *
 * The Mininet-Optical dataplane is a simulation reached over a read-oriented REST API; it does
 * not expose a per-cross-connect flow-programming endpoint. Without a {@link FlowRuleProgrammable}
 * behaviour the FlowRuleManager can never confirm that the cross-connect flow rules produced by
 * the {@code OpticalConnectivityIntentCompiler} are installed or removed. In that case an
 * installed OpticalConnectivityIntent cannot be torn down: its flow rules stay PENDING_REMOVE and
 * the intent is stuck in CORRUPT on withdraw, so it never reaches WITHDRAWN and cannot be purged.
 *
 * This behaviour closes the loop by keeping an in-memory, per-device record of the applied rules
 * and acknowledging apply/remove immediately (the optical cross-connect is realised by the
 * emulator itself). {@link #getFlowEntries()} reflects that record so ONOS sees the rules as
 * ADDED after install and gone after removal, allowing the intent to reach INSTALLED and then be
 * cleanly released (WITHDRAWN / 404).
 */
public class OpticalEmulatorFlowRuleProgrammable extends AbstractHandlerBehaviour
        implements FlowRuleProgrammable {

    private final Logger log = getLogger(getClass());

    // Per-device record of currently installed rules. Static so it is shared across the
    // short-lived behaviour instances ONOS creates per invocation.
    private static final Map<DeviceId, Set<FlowRule>> INSTALLED = new ConcurrentHashMap<>();

    @Override
    public Collection<FlowEntry> getFlowEntries() {
        DeviceId deviceId = handler().data().deviceId();
        Set<FlowRule> rules = INSTALLED.getOrDefault(deviceId, java.util.Collections.emptySet());
        return rules.stream()
                .map(r -> new DefaultFlowEntry(r, FlowEntry.FlowEntryState.ADDED))
                .collect(Collectors.toList());
    }

    @Override
    public Collection<FlowRule> applyFlowRules(Collection<FlowRule> rules) {
        DeviceId deviceId = handler().data().deviceId();
        INSTALLED.computeIfAbsent(deviceId, k -> ConcurrentHashMap.newKeySet()).addAll(rules);
        log.debug("Emulated apply of {} flow rule(s) on {}", rules.size(), deviceId);
        // All rules are accepted by the emulated cross-connect.
        return rules;
    }

    @Override
    public Collection<FlowRule> removeFlowRules(Collection<FlowRule> rules) {
        DeviceId deviceId = handler().data().deviceId();
        Set<FlowRule> installed = INSTALLED.get(deviceId);
        if (installed != null) {
            installed.removeAll(rules);
        }
        log.debug("Emulated remove of {} flow rule(s) on {}", rules.size(), deviceId);
        // All rules are removed from the emulated cross-connect.
        return rules;
    }
}
