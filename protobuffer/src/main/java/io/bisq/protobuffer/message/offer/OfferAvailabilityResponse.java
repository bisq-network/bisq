/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.protobuffer.message.offer;


import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.message.p2p.SupportedCapabilitiesMessage;
import io.bisq.protobuffer.payload.offer.AvailabilityResult;

import javax.annotation.Nullable;
import java.util.ArrayList;

// We add here the SupportedCapabilitiesMessage interface as that message always predates a direct connection
// to the trading peer
public final class OfferAvailabilityResponse extends OfferMessage implements SupportedCapabilitiesMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final AvailabilityResult availabilityResult;

    // TODO keep for backward compatibility. Can be removed once everyone is on v0.4.9
    public final boolean isAvailable;
    @Nullable
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public OfferAvailabilityResponse(String offerId, AvailabilityResult availabilityResult) {
        super(offerId);
        this.availabilityResult = availabilityResult;
        isAvailable = availabilityResult == AvailabilityResult.AVAILABLE;
    }

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public String toString() {
        return "OfferAvailabilityResponse{" +
                "availabilityResult=" + availabilityResult +
                "} " + super.toString();
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Message.getBaseEnvelope();
        return baseEnvelope.setOfferAvailabilityResponse(PB.OfferAvailabilityResponse.newBuilder().setMessageVersion(getMessageVersion())
                .setOfferId(offerId)
                .setAvailabilityResult(PB.AvailabilityResult.forNumber(availabilityResult.ordinal()))
                .addAllSupportedCapabilities(supportedCapabilities)).build();
    }
}
