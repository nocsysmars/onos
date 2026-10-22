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

import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.ChannelSpacing;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.GridType;
import org.onosproject.net.Link;
import org.onosproject.net.OchSignal;
import org.onosproject.net.Path;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.PointToPointIntent;
import org.onosproject.net.topology.PathService;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.onosproject.net.LinkKey.linkKey;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Core implementation of the RSA (Routing and Spectrum Assignment) service.
 *
 * <p>Routing uses {@link PathService#getKShortestPaths} (hop-count weigher).
 * Spectrum assignment uses a First-Fit sweep that enforces the three classic
 * RSA constraints:</p>
 * <ul>
 *   <li><b>Contiguity</b>: the assigned slots form a single contiguous range
 *       {@code [s, s+width)}.</li>
 *   <li><b>Continuity</b>: the same {@code [s, s+width)} range is used on every
 *       link along the chosen path.</li>
 *   <li><b>Non-overlap</b>: those slots are free on every link (checked against
 *       the {@link SpectrumTable}).</li>
 * </ul>
 *
 * <p>Once a (path, start slot) is chosen, the decision is submitted as an ONOS
 * intent. On the null-provider topology used by this PoC the ROADM ports are of
 * type {@link Port.Type#COPPER} (not OCH), so an {@code OpticalConnectivityIntent}
 * would not compile to INSTALLED. The decision is therefore carried by a
 * {@link PointToPointIntent} with {@code suggestedPath} set to the RSA-computed
 * links, and the selected wavelength attached to the selector as an
 * OchSignalCriterion. {@link #buildIntent} keeps an abstraction point: when both
 * endpoints are real OCH ports it is intended to switch to an optical intent for
 * a future real-ROADM (B-track) deployment. See docs/rsa-onos-app.md.</p>
 */
@Component(immediate = true, service = RsaService.class)
public class RsaManager implements RsaService {

    private final Logger log = getLogger(getClass());

    private static final String APP_NAME = "org.onosproject.rsa";

    // Number of candidate paths considered from the k-shortest-path stream.
    private static final int MAX_CANDIDATE_PATHS = 5;

    // Default intent priority for RSA-submitted intents.
    private static final int INTENT_PRIORITY = 100;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PathService pathService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    private ApplicationId appId;

    private final SpectrumTable spectrumTable = new SpectrumTable();
    private final AtomicLong requestCounter = new AtomicLong();

    // requestId -> result (for GET/DELETE) and requestId -> intent key (for withdraw).
    private final Map<String, RsaResult> results = new ConcurrentHashMap<>();
    private final Map<String, Key> intentKeys = new ConcurrentHashMap<>();

    /**
     * Spectrum-assignment fit strategy. First-Fit is the default; Best-Fit is
     * reserved as an extension point (see {@link #chooseStartSlot}).
     */
    public enum FitStrategy {
        FIRST_FIT,
        BEST_FIT
    }

    private final FitStrategy fitStrategy = FitStrategy.FIRST_FIT;

    @Activate
    protected void activate() {
        appId = coreService.registerApplication(APP_NAME);
        log.info("RSA app started, appId={}", appId);
    }

    @Deactivate
    protected void deactivate() {
        log.info("RSA app stopped");
    }

    @Override
    public RsaResult assign(ConnectPoint src, ConnectPoint dst, int slots) {
        if (slots <= 0 || slots > spectrumTable.slotCount()) {
            return RsaResult.blocked(src, dst,
                    "Invalid slot count " + slots + " (table size " + spectrumTable.slotCount() + ")");
        }

        List<Path> candidates = pathService
                .getKShortestPaths(src.deviceId(), dst.deviceId())
                .limit(MAX_CANDIDATE_PATHS)
                .collect(Collectors.toList());

        if (candidates.isEmpty()) {
            return RsaResult.blocked(src, dst,
                    "No path between " + src.deviceId() + " and " + dst.deviceId());
        }

        // Try candidate paths in order; First-Fit on spectrum for each.
        for (Path path : candidates) {
            List<Link> links = path.links();
            int startSlot = chooseStartSlot(links, slots);
            if (startSlot >= 0) {
                return accept(src, dst, links, startSlot, slots);
            }
        }

        return RsaResult.blocked(src, dst,
                "No contiguous " + slots + "-slot segment free on any of the "
                        + candidates.size() + " candidate path(s)");
    }

    /**
     * Returns the first start slot for which the whole path can carry a
     * contiguous {@code width}-slot segment (continuity + non-overlap), or -1 if
     * none. First-Fit scans from slot 0 upward. Best-Fit is a TODO extension.
     */
    private int chooseStartSlot(List<Link> links, int width) {
        switch (fitStrategy) {
            case BEST_FIT:
                // TODO(RSA): implement Best-Fit (choose the tightest feasible gap).
                // Falls through to First-Fit for now.
            case FIRST_FIT:
            default:
                int max = spectrumTable.slotCount() - width;
                for (int s = 0; s <= max; s++) {
                    if (segmentFreeOnPath(links, s, width)) {
                        return s;
                    }
                }
                return -1;
        }
    }

    /**
     * Continuity + non-overlap: the same [s, s+width) must be free on every link.
     */
    private boolean segmentFreeOnPath(List<Link> links, int startSlot, int width) {
        for (Link link : links) {
            if (!spectrumTable.isFree(linkKey(link.src(), link.dst()), startSlot, width)) {
                return false;
            }
        }
        return true;
    }

    private RsaResult accept(ConnectPoint src, ConnectPoint dst,
                             List<Link> links, int startSlot, int width) {
        String requestId = Long.toString(requestCounter.incrementAndGet());

        // Occupy spectrum on every link of the chosen path.
        for (Link link : links) {
            spectrumTable.occupy(requestId, linkKey(link.src(), link.dst()), startSlot, width);
        }

        OchSignal signal = toOchSignal(startSlot, width);

        Intent intent = buildIntent(requestId, src, dst, links, signal);
        intentService.submit(intent);
        intentKeys.put(requestId, intent.key());

        RsaResult result = RsaResult.accepted(requestId, src, dst, links, startSlot, width, signal);
        results.put(requestId, result);
        log.info("RSA accepted request {}: {} -> {} via {} hop(s), slots [{}, {})",
                requestId, src, dst, links.size(), startSlot, startSlot + width);
        return result;
    }

    /**
     * Maps a (startSlot, width) assignment to a fixed DWDM 50 GHz OchSignal.
     * spacingMultiplier encodes the start slot; slotGranularity encodes the width.
     */
    private OchSignal toOchSignal(int startSlot, int width) {
        return new OchSignal(GridType.DWDM, ChannelSpacing.CHL_50GHZ, startSlot, width);
    }

    /**
     * Builds the intent carrying the RSA decision.
     *
     * <p>Abstraction point: if both endpoints are real OCH ports this method is
     * meant to delegate to an optical connectivity intent for a real-ROADM
     * deployment. On the null-provider PoC (COPPER ports) it falls back to a
     * {@link PointToPointIntent} with the computed path as {@code suggestedPath}
     * and the chosen wavelength attached as an OchSignalCriterion in the
     * selector, which reaches INSTALLED on the null provider.</p>
     */
    private Intent buildIntent(String requestId, ConnectPoint src, ConnectPoint dst,
                               List<Link> links, OchSignal signal) {
        if (bothOchPorts(src, dst)) {
            // TODO(RSA/B-track): real OCH ports present -> build an
            // OpticalConnectivityIntent via OpticalIntentUtility for real ROADMs.
            // Not reachable on the null-provider PoC; kept as an explicit hook.
            log.info("Both endpoints are OCH ports; optical intent path reserved for B-track");
        }

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .add(Criteria.matchLambda(signal))
                .build();

        return PointToPointIntent.builder()
                .appId(appId)
                .key(Key.of("rsa-" + requestId, appId))
                .filteredIngressPoint(new FilteredConnectPoint(src))
                .filteredEgressPoint(new FilteredConnectPoint(dst))
                .suggestedPath(links)
                .selector(selector)
                .priority(INTENT_PRIORITY)
                .build();
    }

    private boolean bothOchPorts(ConnectPoint src, ConnectPoint dst) {
        Port srcPort = deviceService.getPort(src);
        Port dstPort = deviceService.getPort(dst);
        return srcPort != null && dstPort != null
                && srcPort.type() == Port.Type.OCH
                && dstPort.type() == Port.Type.OCH;
    }

    @Override
    public boolean release(String requestId) {
        RsaResult result = results.remove(requestId);
        if (result == null) {
            return false;
        }
        Key key = intentKeys.remove(requestId);
        if (key != null) {
            Intent intent = intentService.getIntent(key);
            if (intent != null) {
                intentService.withdraw(intent);
            }
        }
        spectrumTable.release(requestId);
        log.info("RSA released request {}", requestId);
        return true;
    }

    @Override
    public RsaResult get(String requestId) {
        return results.get(requestId);
    }
}
