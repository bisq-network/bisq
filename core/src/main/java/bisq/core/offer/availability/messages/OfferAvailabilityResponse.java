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

package bisq.core.offer.availability.messages;


import bisq.core.offer.availability.AvailabilityResult;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SupportedCapabilitiesMessage;

import bisq.common.app.Capabilities;
import bisq.common.app.Version;
import bisq.common.proto.ProtoUtil;

import java.util.Optional;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import javax.annotation.Nullable;

// We add here the SupportedCapabilitiesMessage interface as that message always predates a direct connection
// to the trading peer
@EqualsAndHashCode(callSuper = true)
@Getter
public final class OfferAvailabilityResponse extends OfferMessage implements SupportedCapabilitiesMessage {
    private final AvailabilityResult availabilityResult;
    @Nullable
    private final Capabilities supportedCapabilities;

    private final NodeAddress arbitrator;
    // Was introduced in v 1.1.6. Might be null if msg received from node with old version
    @Nullable
    private final NodeAddress mediator;

    // Added v1.2.0
    @Nullable
    private final NodeAddress refundAgent;

    public OfferAvailabilityResponse(String offerId,
                                     AvailabilityResult availabilityResult,
                                     NodeAddress arbitrator,
                                     NodeAddress mediator,
                                     NodeAddress refundAgent) {
        this(offerId,
                availabilityResult,
                Capabilities.app,
                Version.getP2PMessageVersion(),
                UUID.randomUUID().toString(),
                arbitrator,
                mediator,
                refundAgent);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private OfferAvailabilityResponse(String offerId,
                                      AvailabilityResult availabilityResult,
                                      @Nullable Capabilities supportedCapabilities,
                                      int messageVersion,
                                      @Nullable String uid,
                                      NodeAddress arbitrator,
                                      @Nullable NodeAddress mediator,
                                      @Nullable NodeAddress refundAgent) {
        super(messageVersion, offerId, uid);
        this.availabilityResult = availabilityResult;
        this.supportedCapabilities = supportedCapabilities;
        this.arbitrator = arbitrator;
        this.mediator = mediator;
        this.refundAgent = refundAgent;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        final protobuf.OfferAvailabilityResponse.Builder builder = protobuf.OfferAvailabilityResponse.newBuilder()
                .setOfferId(offerId)
                .setAvailabilityResult(protobuf.AvailabilityResult.valueOf(availabilityResult.name()));

        Optional.ofNullable(supportedCapabilities).ifPresent(e -> builder.addAllSupportedCapabilities(Capabilities.toIntList(supportedCapabilities)));
        Optional.ofNullable(uid).ifPresent(e -> builder.setUid(uid));
        Optional.ofNullable(mediator).ifPresent(e -> builder.setMediator(mediator.toProtoMessage()));
        Optional.ofNullable(refundAgent).ifPresent(e -> builder.setRefundAgent(refundAgent.toProtoMessage()));
        Optional.ofNullable(arbitrator).ifPresent(e -> builder.setArbitrator(arbitrator.toProtoMessage()));

        return getNetworkEnvelopeBuilder()
                .setOfferAvailabilityResponse(builder)
                .build();
    }

    public static OfferAvailabilityResponse fromProto(protobuf.OfferAvailabilityResponse proto, int messageVersion) {
        return new OfferAvailabilityResponse(proto.getOfferId(),
                ProtoUtil.enumFromProto(AvailabilityResult.class, proto.getAvailabilityResult().name()),
                Capabilities.fromIntList(proto.getSupportedCapabilitiesList()),
                messageVersion,
                proto.getUid().isEmpty() ? null : proto.getUid(),
                proto.hasArbitrator() ? NodeAddress.fromProto(proto.getArbitrator()) : null,
                proto.hasMediator() ? NodeAddress.fromProto(proto.getMediator()) : null,
                proto.hasRefundAgent() ? NodeAddress.fromProto(proto.getRefundAgent()) : null);
    }
}
