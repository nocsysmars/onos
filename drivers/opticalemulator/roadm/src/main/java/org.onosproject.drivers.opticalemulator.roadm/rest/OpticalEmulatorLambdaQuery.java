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

import org.onlab.util.Frequency;
import org.onlab.util.Spectrum;
import org.onosproject.net.ChannelSpacing;
import org.onosproject.net.GridType;
import org.onosproject.net.OchSignal;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.LambdaQuery;
import org.onosproject.net.driver.AbstractHandlerBehaviour;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Lambda query implementation for the Optical Emulator (Mininet-Optical) ROADM.
 *
 * The companion {@link OpticalEmulatorDeviceDescription} advertises a fixed set of
 * six OCh ports but does not expose their usable optical spectrum. Without a
 * {@link LambdaQuery} behaviour the core {@code ResourceDeviceListener} registers
 * no OchSignal (lambda) resources for those ports, so the
 * {@code OpticalConnectivityIntentCompiler} fails resource allocation and the
 * intent never reaches INSTALLED.
 *
 * This behaviour closes that gap by advertising the full C-band 50 GHz DWDM grid
 * (96 channels, 191.350 - 196.100 THz) for every OCh port, matching the
 * {@code CHL_50GHZ / DWDM} signal used by the emulator intents.
 */
public class OpticalEmulatorLambdaQuery extends AbstractHandlerBehaviour implements LambdaQuery {

    public static final GridType GRID_TYPE = GridType.DWDM;
    public static final ChannelSpacing CHANNEL_SPACING_50 = ChannelSpacing.CHL_50GHZ;
    public static final Frequency START_CENTER_FREQ_50 = Frequency.ofGHz(191_350);
    private static final int LAMBDA_COUNT_50 = 96;
    // 50 GHz channel = 4 x 12.5 GHz slots on the underlying flex grid.
    private static final int SLOT_GRANULARITY_50 = 4;

    @Override
    public Set<OchSignal> queryLambdas(PortNumber portNumber) {
        // The emulator ROADM exposes OCh ports only; advertise the full 50 GHz
        // C-band grid on every port regardless of port number.
        int startMultiplier50 = (int) (START_CENTER_FREQ_50.subtract(Spectrum.CENTER_FREQUENCY).asHz()
                / Frequency.ofGHz(50).asHz());

        return IntStream.range(0, LAMBDA_COUNT_50)
                .mapToObj(x -> new OchSignal(GRID_TYPE, CHANNEL_SPACING_50,
                        startMultiplier50 + x, SLOT_GRANULARITY_50))
                .collect(Collectors.toSet());
    }
}
