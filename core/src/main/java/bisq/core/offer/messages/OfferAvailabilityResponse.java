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

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SupportedCapabilitiesMessage;

import bisq.common.app.Capabilities;
import bisq.common.app.Version;
import bisq.common.proto.ProtoUtil;

import io.bisq.generated.protobuffer.PB;

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
    private final Capabilities supportedCapabilities;

    // Was introduced in v 0.9.0. Might be null if msg received from node with old version
    @Nullable
    private final NodeAddress arbitrator;

    public OfferAvailabilityResponse(String offerId, AvailabilityResult availabilityResult, NodeAddress arbitrator) {
        this(offerId,
                availabilityResult,
                Capabilities.app,
                Version.getP2PMessageVersion(),
                UUID.randomUUID().toString(),
                arbitrator);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private OfferAvailabilityResponse(String offerId,
                                      AvailabilityResult availabilityResult,
                                      @Nullable Capabilities supportedCapabilities,
                                      int messageVersion,
                                      @Nullable String uid,
                                      @Nullable NodeAddress arbitrator) {
        super(messageVersion, offerId, uid);
        this.availabilityResult = availabilityResult;
        this.supportedCapabilities = supportedCapabilities;
        this.arbitrator = arbitrator;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        final PB.OfferAvailabilityResponse.Builder builder = PB.OfferAvailabilityResponse.newBuilder()
                .setOfferId(offerId)
                .setAvailabilityResult(PB.AvailabilityResult.valueOf(availabilityResult.name()));

        Optional.ofNullable(supportedCapabilities).ifPresent(e -> builder.addAllSupportedCapabilities(Capabilities.toIntList(supportedCapabilities)));
        Optional.ofNullable(uid).ifPresent(e -> builder.setUid(uid));
        Optional.ofNullable(arbitrator).ifPresent(e -> builder.setArbitrator(arbitrator.toProtoMessage()));

        return getNetworkEnvelopeBuilder()
                .setOfferAvailabilityResponse(builder)
                .build();
    }

    public static OfferAvailabilityResponse fromProto(PB.OfferAvailabilityResponse proto, int messageVersion) {
        return new OfferAvailabilityResponse(proto.getOfferId(),
                ProtoUtil.enumFromProto(AvailabilityResult.class, proto.getAvailabilityResult().name()),
                Capabilities.fromIntList(proto.getSupportedCapabilitiesList()),
                messageVersion,
                proto.getUid().isEmpty() ? null : proto.getUid(),
                proto.hasArbitrator() ? NodeAddress.fromProto(proto.getArbitrator()) : null);
    }
}
