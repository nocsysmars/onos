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
import org.onosproject.net.LinkKey;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.onosproject.net.LinkKey.linkKey;

/**
 * Spectrum occupancy table for RSA.
 *
 * <p>Each link is modelled as a C-band slice cut into a fixed number of 50 GHz
 * slots (see {@link #DEFAULT_SLOT_COUNT}, aligned with the FEAT-001 topology
 * convention {@code RSA_SLOTS_PER_LINK=16}). Occupancy is tracked per link with
 * a {@link BitSet}: a set bit means the slot is in use.</p>
 *
 * <p>Links are keyed by their undirected endpoints. A directional ONOS link and
 * its reverse map to the same key, so a wavelength occupied in one direction is
 * also considered occupied in the other (a wavelength is a shared physical
 * resource on the fibre pair). This keeps the three RSA constraints honest on a
 * bidirectional optical topology.</p>
 *
 * <p>Storage is an in-process {@link ConcurrentHashMap}; this is sufficient for
 * the single-node PoC. A multi-node deployment would move this to a
 * {@code StorageService} ConsistentMap, but that is out of scope here.</p>
 */
public class SpectrumTable {

    /** Default number of 50 GHz slots per link (aligned with FEAT-001). */
    public static final int DEFAULT_SLOT_COUNT = 16;

    private final int slotCount;

    // Occupancy per undirected link.
    private final Map<LinkKey, BitSet> occupancy = new ConcurrentHashMap<>();

    // Reservations per request: which (link, startSlot, width) a request owns.
    private final Map<String, List<Reservation>> reservations = new ConcurrentHashMap<>();

    /**
     * A single (link, slot range) reservation held by a request.
     */
    private static final class Reservation {
        final LinkKey link;
        final int startSlot;
        final int width;

        Reservation(LinkKey link, int startSlot, int width) {
            this.link = link;
            this.startSlot = startSlot;
            this.width = width;
        }
    }

    /**
     * Creates a table with the default slot count.
     */
    public SpectrumTable() {
        this(DEFAULT_SLOT_COUNT);
    }

    /**
     * Creates a table with the given slot count.
     *
     * @param slotCount number of 50 GHz slots per link
     */
    public SpectrumTable(int slotCount) {
        this.slotCount = slotCount;
    }

    /**
     * Returns the number of slots per link.
     *
     * @return slot count
     */
    public int slotCount() {
        return slotCount;
    }

    private static LinkKey undirected(LinkKey key) {
        // Normalise direction so (a,b) and (b,a) share occupancy.
        if (key.src().toString().compareTo(key.dst().toString()) <= 0) {
            return key;
        }
        return linkKey(key.dst(), key.src());
    }

    private BitSet bits(LinkKey key) {
        return occupancy.computeIfAbsent(undirected(key), k -> new BitSet(slotCount));
    }

    /**
     * Returns true if the segment [startSlot, startSlot+width) on the given link
     * is entirely free. Enforces spectrum contiguity (a single contiguous range)
     * and non-overlap (none of the slots currently occupied).
     *
     * @param key       link key
     * @param startSlot segment start slot index
     * @param width     number of slots
     * @return true if free
     */
    public synchronized boolean isFree(LinkKey key, int startSlot, int width) {
        if (startSlot < 0 || width <= 0 || startSlot + width > slotCount) {
            return false;
        }
        BitSet bs = bits(key);
        // nextSetBit within the window; if none, the window is free.
        int next = bs.nextSetBit(startSlot);
        return next < 0 || next >= startSlot + width;
    }

    /**
     * Occupies the segment [startSlot, startSlot+width) on the given link for the
     * given request.
     *
     * @param requestId request identifier
     * @param key       link key
     * @param startSlot segment start slot index
     * @param width     number of slots
     */
    public synchronized void occupy(String requestId, LinkKey key, int startSlot, int width) {
        BitSet bs = bits(key);
        bs.set(startSlot, startSlot + width);
        reservations.computeIfAbsent(requestId, r -> new ArrayList<>())
                .add(new Reservation(undirected(key), startSlot, width));
    }

    /**
     * Releases every slot reserved by the given request.
     *
     * @param requestId request identifier
     * @return true if the request held reservations and they were released
     */
    public synchronized boolean release(String requestId) {
        List<Reservation> held = reservations.remove(requestId);
        if (held == null) {
            return false;
        }
        for (Reservation r : held) {
            BitSet bs = occupancy.get(r.link);
            if (bs != null) {
                bs.clear(r.startSlot, r.startSlot + r.width);
            }
        }
        return true;
    }

    /**
     * Returns true if the request currently holds reservations.
     *
     * @param requestId request identifier
     * @return true if known
     */
    public synchronized boolean contains(String requestId) {
        return reservations.containsKey(requestId);
    }

    /**
     * Immutable view of a single request's reservation on a link: the owning
     * request id and the contiguous slot range it holds.
     */
    public static final class ReservationView {
        private final String requestId;
        private final int startSlot;
        private final int width;

        /**
         * Creates a reservation view.
         *
         * @param requestId owning request identifier
         * @param startSlot start slot index of the reserved segment
         * @param width     number of slots reserved
         */
        public ReservationView(String requestId, int startSlot, int width) {
            this.requestId = requestId;
            this.startSlot = startSlot;
            this.width = width;
        }

        /**
         * Returns the owning request identifier.
         *
         * @return request id
         */
        public String requestId() {
            return requestId;
        }

        /**
         * Returns the start slot index of the reserved segment.
         *
         * @return start slot
         */
        public int startSlot() {
            return startSlot;
        }

        /**
         * Returns the number of slots reserved.
         *
         * @return width
         */
        public int width() {
            return width;
        }
    }

    /**
     * Immutable occupancy view of a single undirected link.
     */
    public static final class LinkSpectrum {
        private final ConnectPoint src;
        private final ConnectPoint dst;
        private final int slotCount;
        private final List<Integer> occupiedSlots;
        private final int freeSlotCount;
        private final int maxContiguousFree;
        private final List<ReservationView> reservations;

        /**
         * Creates a link spectrum view.
         *
         * @param src               undirected key source endpoint
         * @param dst               undirected key destination endpoint
         * @param slotCount         number of slots on the link
         * @param occupiedSlots     sorted indices of occupied slots
         * @param freeSlotCount     number of free slots
         * @param maxContiguousFree longest run of consecutive free slots
         * @param reservations      reservations held on this link
         */
        public LinkSpectrum(ConnectPoint src, ConnectPoint dst, int slotCount,
                            List<Integer> occupiedSlots, int freeSlotCount,
                            int maxContiguousFree, List<ReservationView> reservations) {
            this.src = src;
            this.dst = dst;
            this.slotCount = slotCount;
            this.occupiedSlots = ImmutableList.copyOf(occupiedSlots);
            this.freeSlotCount = freeSlotCount;
            this.maxContiguousFree = maxContiguousFree;
            this.reservations = ImmutableList.copyOf(reservations);
        }

        /**
         * Returns the undirected key source endpoint.
         *
         * @return source connect point
         */
        public ConnectPoint src() {
            return src;
        }

        /**
         * Returns the undirected key destination endpoint.
         *
         * @return destination connect point
         */
        public ConnectPoint dst() {
            return dst;
        }

        /**
         * Returns the number of slots on the link.
         *
         * @return slot count
         */
        public int slotCount() {
            return slotCount;
        }

        /**
         * Returns the sorted indices of occupied slots.
         *
         * @return immutable list of occupied slot indices
         */
        public List<Integer> occupiedSlots() {
            return occupiedSlots;
        }

        /**
         * Returns the number of free slots.
         *
         * @return free slot count
         */
        public int freeSlotCount() {
            return freeSlotCount;
        }

        /**
         * Returns the longest run of consecutive free slots.
         *
         * @return max contiguous free slot count
         */
        public int maxContiguousFree() {
            return maxContiguousFree;
        }

        /**
         * Returns the reservations held on this link.
         *
         * @return immutable list of reservation views
         */
        public List<ReservationView> reservations() {
            return reservations;
        }
    }

    /**
     * Immutable snapshot of the whole spectrum table: the per-link slot count
     * and one {@link LinkSpectrum} per undirected link that has occupancy.
     */
    public static final class SpectrumSnapshot {
        private final int slotCount;
        private final List<LinkSpectrum> links;

        /**
         * Creates a spectrum snapshot.
         *
         * @param slotCount number of slots per link
         * @param links     per-link occupancy views
         */
        public SpectrumSnapshot(int slotCount, List<LinkSpectrum> links) {
            this.slotCount = slotCount;
            this.links = ImmutableList.copyOf(links);
        }

        /**
         * Returns the number of slots per link.
         *
         * @return slot count
         */
        public int slotCount() {
            return slotCount;
        }

        /**
         * Returns the per-link occupancy views.
         *
         * @return immutable list of link spectra
         */
        public List<LinkSpectrum> links() {
            return links;
        }
    }

    /**
     * Builds an immutable snapshot of current occupancy and reservations.
     *
     * <p>For each undirected link with occupancy this reports the occupied slot
     * indices, the free slot count, the longest run of consecutive free slots,
     * and the per-request reservations ({@code requestId}, {@code startSlot},
     * {@code width}) attached to that link. The snapshot is a point-in-time copy
     * and does not reflect subsequent mutations.</p>
     *
     * @return current spectrum snapshot
     */
    public synchronized SpectrumSnapshot snapshot() {
        // Group reservations by their (undirected) link so each LinkSpectrum
        // can carry the reservations that apply to it.
        Map<LinkKey, List<ReservationView>> byLink = new ConcurrentHashMap<>();
        for (Map.Entry<String, List<Reservation>> entry : reservations.entrySet()) {
            String requestId = entry.getKey();
            for (Reservation r : entry.getValue()) {
                byLink.computeIfAbsent(r.link, k -> new ArrayList<>())
                        .add(new ReservationView(requestId, r.startSlot, r.width));
            }
        }

        List<LinkSpectrum> linkViews = new ArrayList<>();
        for (Map.Entry<LinkKey, BitSet> entry : occupancy.entrySet()) {
            LinkKey key = entry.getKey();
            BitSet bs = entry.getValue();

            List<Integer> occupied = new ArrayList<>();
            for (int i = bs.nextSetBit(0); i >= 0 && i < slotCount; i = bs.nextSetBit(i + 1)) {
                occupied.add(i);
            }

            int freeSlotCount = slotCount - occupied.size();
            int maxContiguousFree = longestZeroRun(bs);

            List<ReservationView> linkReservations =
                    byLink.getOrDefault(key, new ArrayList<>());

            linkViews.add(new LinkSpectrum(key.src(), key.dst(), slotCount,
                    occupied, freeSlotCount, maxContiguousFree, linkReservations));
        }

        return new SpectrumSnapshot(slotCount, linkViews);
    }

    /**
     * Returns the length of the longest run of consecutive free (clear) slots
     * within [0, slotCount).
     */
    private int longestZeroRun(BitSet bs) {
        int longest = 0;
        int current = 0;
        for (int i = 0; i < slotCount; i++) {
            if (bs.get(i)) {
                current = 0;
            } else {
                current++;
                if (current > longest) {
                    longest = current;
                }
            }
        }
        return longest;
    }
}
