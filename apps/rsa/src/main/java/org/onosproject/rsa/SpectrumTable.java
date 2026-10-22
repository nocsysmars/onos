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
}
