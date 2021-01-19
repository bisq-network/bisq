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

package bisq.network.p2p.peers.peerexchange.messages;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendersNodeAddressMessage;
import bisq.network.p2p.SupportedCapabilitiesMessage;
import bisq.network.p2p.peers.peerexchange.Peer;

import bisq.common.app.Capabilities;
import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode(callSuper = true)
@Value
public final class GetPeersRequest extends NetworkEnvelope implements PeerExchangeMessage, SendersNodeAddressMessage, SupportedCapabilitiesMessage {
    private final NodeAddress senderNodeAddress;
    private final int nonce;
    private final Set<Peer> reportedPeers;
    @Nullable
    private final Capabilities supportedCapabilities;

    public GetPeersRequest(NodeAddress senderNodeAddress,
                           int nonce,
                           Set<Peer> reportedPeers) {
        this(senderNodeAddress,
                nonce,
                reportedPeers,
                Capabilities.app,
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetPeersRequest(NodeAddress senderNodeAddress,
                            int nonce,
                            Set<Peer> reportedPeers,
                            @Nullable Capabilities supportedCapabilities,
                            int messageVersion) {
        super(messageVersion);
        checkNotNull(senderNodeAddress, "senderNodeAddress must not be null at GetPeersRequest");
        this.senderNodeAddress = senderNodeAddress;
        this.nonce = nonce;
        this.reportedPeers = reportedPeers;
        this.supportedCapabilities = supportedCapabilities;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        // We clone to avoid ConcurrentModificationExceptions
        Set<Peer> clone = new HashSet<>(reportedPeers);
        protobuf.GetPeersRequest.Builder builder = protobuf.GetPeersRequest.newBuilder()
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setNonce(nonce)
                .addAllReportedPeers(clone.stream()
                        .map(Peer::toProtoMessage)
                        .collect(Collectors.toList()));

        Optional.ofNullable(supportedCapabilities).ifPresent(e -> builder.addAllSupportedCapabilities(Capabilities.toIntList(supportedCapabilities)));

        return getNetworkEnvelopeBuilder()
                .setGetPeersRequest(builder)
                .build();
    }

    public static GetPeersRequest fromProto(protobuf.GetPeersRequest proto, int messageVersion) {
        return new GetPeersRequest(NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getNonce(),
                new HashSet<>(proto.getReportedPeersList().stream()
                        .map(Peer::fromProto)
                        .collect(Collectors.toSet())),
                Capabilities.fromIntList(proto.getSupportedCapabilitiesList()),
                messageVersion);
    }
}
