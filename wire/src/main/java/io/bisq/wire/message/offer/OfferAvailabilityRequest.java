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

package io.bisq.wire.message.offer;

import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.wire.message.p2p.SupportedCapabilitiesMessage;
import io.bisq.wire.payload.crypto.PubKeyRing;
import io.bisq.wire.proto.Messages;

import javax.annotation.Nullable;
import java.util.ArrayList;

// We add here the SupportedCapabilitiesMessage interface as that message always predates a direct connection 
// to the trading peer
public final class OfferAvailabilityRequest extends OfferMessage implements SupportedCapabilitiesMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final PubKeyRing pubKeyRing;
    public final long takersTradePrice;
    @Nullable
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public OfferAvailabilityRequest(String offerId, PubKeyRing pubKeyRing, long takersTradePrice) {
        super(offerId);
        this.pubKeyRing = pubKeyRing;
        this.takersTradePrice = takersTradePrice;
    }

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    public PubKeyRing getPubKeyRing() {
        return pubKeyRing;
    }

    @Override
    public String toString() {
        return "OfferAvailabilityRequest{" +
                "pubKeyRing=" + pubKeyRing +
                "} " + super.toString();
    }

    @Override
    public Messages.Envelope toProtoBuf() {
        return Messages.Envelope.newBuilder()
                .setOfferAvailabilityRequest(Messages.OfferAvailabilityRequest.newBuilder()
                        .setOfferId(offerId)
                        .setPubKeyRing(pubKeyRing.toProtoBuf())
                        .setTakersTradePrice(takersTradePrice)
                        .addAllSupportedCapabilities(supportedCapabilities)).build();
    }
}
