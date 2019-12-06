/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.offer.messages;

import bisq.network.p2p.SupportedCapabilitiesMessage;

import bisq.common.app.Capabilities;
import bisq.common.app.Version;
import bisq.common.crypto.PubKeyRing;

import java.util.Optional;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

// Here we add the SupportedCapabilitiesMessage interface as that message always predates a direct connection
// to the trading peer
@EqualsAndHashCode(callSuper = true)
@Value
@Slf4j
public final class OfferAvailabilityRequest extends OfferMessage implements SupportedCapabilitiesMessage {
    private final PubKeyRing pubKeyRing;
    private final long takersTradePrice;
    @Nullable
    private final Capabilities supportedCapabilities;

    public OfferAvailabilityRequest(String offerId,
                                    PubKeyRing pubKeyRing,
                                    long takersTradePrice) {
        this(offerId,
                pubKeyRing,
                takersTradePrice,
                Capabilities.app,
                Version.getP2PMessageVersion(),
                UUID.randomUUID().toString());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private OfferAvailabilityRequest(String offerId,
                                     PubKeyRing pubKeyRing,
                                     long takersTradePrice,
                                     @Nullable Capabilities supportedCapabilities,
                                     int messageVersion,
                                     @Nullable String uid) {
        super(messageVersion, offerId, uid);
        this.pubKeyRing = pubKeyRing;
        this.takersTradePrice = takersTradePrice;
        this.supportedCapabilities = supportedCapabilities;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        final protobuf.OfferAvailabilityRequest.Builder builder = protobuf.OfferAvailabilityRequest.newBuilder()
                .setOfferId(offerId)
                .setPubKeyRing(pubKeyRing.toProtoMessage())
                .setTakersTradePrice(takersTradePrice);

        Optional.ofNullable(supportedCapabilities).ifPresent(e -> builder.addAllSupportedCapabilities(Capabilities.toIntList(supportedCapabilities)));
        Optional.ofNullable(uid).ifPresent(e -> builder.setUid(uid));

        return getNetworkEnvelopeBuilder()
                .setOfferAvailabilityRequest(builder)
                .build();
    }

    public static OfferAvailabilityRequest fromProto(protobuf.OfferAvailabilityRequest proto, int messageVersion) {
        return new OfferAvailabilityRequest(proto.getOfferId(),
                PubKeyRing.fromProto(proto.getPubKeyRing()),
                proto.getTakersTradePrice(),
                Capabilities.fromIntList(proto.getSupportedCapabilitiesList()),
                messageVersion,
                proto.getUid().isEmpty() ? null : proto.getUid());
    }
}
