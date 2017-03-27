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
import io.bisq.protobuffer.message.p2p.SupportedCapabilitiesMessage;
import io.bisq.protobuffer.payload.crypto.PubKeyRingPayload;

import javax.annotation.Nullable;
import java.util.ArrayList;

// We add here the SupportedCapabilitiesMessage interface as that message always predates a direct connection 
// to the trading peer
public final class OfferAvailabilityRequest extends OfferMessage implements SupportedCapabilitiesMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final PubKeyRingPayload pubKeyRingPayload;
    public final long takersTradePrice;
    @Nullable
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public OfferAvailabilityRequest(String offerId, PubKeyRingPayload pubKeyRingPayload, long takersTradePrice) {
        super(offerId);
        this.pubKeyRingPayload = pubKeyRingPayload;
        this.takersTradePrice = takersTradePrice;
    }

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    public PubKeyRingPayload getPubKeyRing() {
        return pubKeyRingPayload;
    }

    @Override
    public String toString() {
        return "OfferAvailabilityRequest{" +
                "pubKeyRingPayload=" + pubKeyRingPayload +
                "} " + super.toString();
    }

    @Override
    public PB.Envelope toProto() {
        return PB.Envelope.newBuilder()
                .setOfferAvailabilityRequest(PB.OfferAvailabilityRequest.newBuilder()
                        .setOfferId(offerId)
                        .setPubKeyRingPayload(pubKeyRingPayload.toProto())
                        .setTakersTradePrice(takersTradePrice)
                        .addAllSupportedCapabilities(supportedCapabilities)).build();
    }
}
