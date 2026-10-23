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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.onosproject.net.Link;
import org.onosproject.net.optical.json.OchSignalCodec;
import org.onosproject.rsa.RsaResult;

/**
 * Shared JSON serialization helpers for the RSA REST resources.
 *
 * <p>Kept package-private so the single-result encoding used by
 * {@link RsaPathWebResource} and the array encoding used by
 * {@link RsaPathsWebResource} stay identical and cannot drift.</p>
 */
final class RsaJson {

    private RsaJson() {
    }

    /**
     * Encodes an accepted {@link RsaResult} into the canonical JSON object used
     * across the RSA REST surface.
     *
     * <p>Field set and order: {@code requestId}, {@code blocked} (always
     * {@code false}), {@code src}, {@code dst}, {@code startSlot}, {@code width},
     * {@code devices[]} (first link's src deviceId then each link's dst
     * deviceId), {@code links[]} ({@code {src,dst}} as ConnectPoint strings),
     * and {@code ochSignal} ({@link OchSignalCodec#encode}) when a signal is
     * present.</p>
     *
     * @param mapper object mapper used to build nodes
     * @param result accepted RSA result to encode
     * @return the encoded result object
     */
    static ObjectNode encodeResult(ObjectMapper mapper, RsaResult result) {
        ObjectNode body = mapper.createObjectNode();
        body.put("requestId", result.requestId());
        body.put("blocked", false);
        body.put("src", result.src().toString());
        body.put("dst", result.dst().toString());
        body.put("startSlot", result.startSlot());
        body.put("width", result.width());

        ArrayNode devices = body.putArray("devices");
        if (!result.path().isEmpty()) {
            devices.add(result.path().get(0).src().deviceId().toString());
            for (Link link : result.path()) {
                devices.add(link.dst().deviceId().toString());
            }
        }

        ArrayNode links = body.putArray("links");
        for (Link link : result.path()) {
            ObjectNode l = mapper.createObjectNode();
            l.put("src", link.src().toString());
            l.put("dst", link.dst().toString());
            links.add(l);
        }

        if (result.signal() != null) {
            body.set("ochSignal", OchSignalCodec.encode(result.signal()));
        }
        return body;
    }
}
