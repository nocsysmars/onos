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
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Link;
import org.onosproject.net.OchSignal;

import java.util.List;

/**
 * Immutable result of an RSA (Routing and Spectrum Assignment) decision.
 *
 * <p>A result is either accepted (a route plus a contiguous spectrum segment
 * were found) or blocked (no feasible path/spectrum). When blocked, the path,
 * spectrum and signal fields are empty/zero and {@link #reason()} explains why.</p>
 */
public final class RsaResult {

    private final String requestId;
    private final boolean blocked;
    private final String reason;
    private final ConnectPoint src;
    private final ConnectPoint dst;
    private final List<Link> path;
    private final int startSlot;
    private final int width;
    private final OchSignal signal;

    private RsaResult(String requestId, boolean blocked, String reason,
                      ConnectPoint src, ConnectPoint dst, List<Link> path,
                      int startSlot, int width, OchSignal signal) {
        this.requestId = requestId;
        this.blocked = blocked;
        this.reason = reason;
        this.src = src;
        this.dst = dst;
        this.path = path == null ? ImmutableList.of() : ImmutableList.copyOf(path);
        this.startSlot = startSlot;
        this.width = width;
        this.signal = signal;
    }

    /**
     * Creates an accepted result.
     *
     * @param requestId request identifier
     * @param src       source connect point
     * @param dst       destination connect point
     * @param path      chosen path as an ordered list of links
     * @param startSlot start slot index of the assigned segment
     * @param width     number of slots assigned
     * @param signal    OchSignal representing the assigned spectrum
     * @return accepted result
     */
    public static RsaResult accepted(String requestId, ConnectPoint src, ConnectPoint dst,
                                     List<Link> path, int startSlot, int width, OchSignal signal) {
        return new RsaResult(requestId, false, null, src, dst, path, startSlot, width, signal);
    }

    /**
     * Creates a blocked result.
     *
     * @param src    source connect point
     * @param dst    destination connect point
     * @param reason human-readable blocking reason
     * @return blocked result
     */
    public static RsaResult blocked(ConnectPoint src, ConnectPoint dst, String reason) {
        return new RsaResult(null, true, reason, src, dst, null, 0, 0, null);
    }

    public String requestId() {
        return requestId;
    }

    public boolean isBlocked() {
        return blocked;
    }

    public String reason() {
        return reason;
    }

    public ConnectPoint src() {
        return src;
    }

    public ConnectPoint dst() {
        return dst;
    }

    public List<Link> path() {
        return path;
    }

    public int startSlot() {
        return startSlot;
    }

    public int width() {
        return width;
    }

    public OchSignal signal() {
        return signal;
    }
}
