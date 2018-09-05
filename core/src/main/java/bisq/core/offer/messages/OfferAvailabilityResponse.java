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


import bisq.core.offer.AvailabilityResult;

import bisq.network.p2p.SupportedCapabilitiesMessage;

import bisq.common.app.Capabilities;
import bisq.common.app.Version;
import bisq.common.proto.ProtoUtil;

import io.bisq.generated.protobuffer.PB;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;

// We add here the SupportedCapabilitiesMessage interface as that message always predates a direct connection
// to the trading peer
@EqualsAndHashCode(callSuper = true)
@Value
public final class OfferAvailabilityResponse extends OfferMessage implements SupportedCapabilitiesMessage {
    private final AvailabilityResult availabilityResult;
    @Nullable
    private final List<Integer> supportedCapabilities;

    public OfferAvailabilityResponse(String offerId, AvailabilityResult availabilityResult) {
        this(offerId,
                availabilityResult,
                Capabilities.getSupportedCapabilities(),
                Version.getP2PMessageVersion(),
                UUID.randomUUID().toString());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private OfferAvailabilityResponse(String offerId,
                                      AvailabilityResult availabilityResult,
                                      @Nullable List<Integer> supportedCapabilities,
                                      int messageVersion,
                                      @Nullable String uid) {
        super(messageVersion, offerId, uid);
        this.availabilityResult = availabilityResult;
        this.supportedCapabilities = supportedCapabilities;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        final PB.OfferAvailabilityResponse.Builder builder = PB.OfferAvailabilityResponse.newBuilder()
                .setOfferId(offerId)
                .setAvailabilityResult(PB.AvailabilityResult.valueOf(availabilityResult.name()));

        Optional.ofNullable(supportedCapabilities).ifPresent(e -> builder.addAllSupportedCapabilities(supportedCapabilities));
        Optional.ofNullable(uid).ifPresent(e -> builder.setUid(uid));

        return getNetworkEnvelopeBuilder()
                .setOfferAvailabilityResponse(builder)
                .build();
    }

    public static OfferAvailabilityResponse fromProto(PB.OfferAvailabilityResponse proto, int messageVersion) {
        return new OfferAvailabilityResponse(proto.getOfferId(),
                ProtoUtil.enumFromProto(AvailabilityResult.class, proto.getAvailabilityResult().name()),
                proto.getSupportedCapabilitiesList().isEmpty() ? null : proto.getSupportedCapabilitiesList(),
                messageVersion,
                proto.getUid().isEmpty() ? null : proto.getUid());
    }
}
