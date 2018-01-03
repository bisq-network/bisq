package io.bisq.network.p2p.peers.peerexchange.messages;

import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.SupportedCapabilitiesMessage;
import io.bisq.network.p2p.peers.peerexchange.Peer;
import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Value
public final class GetPeersResponse extends NetworkEnvelope implements PeerExchangeMessage, SupportedCapabilitiesMessage {
    private final int requestNonce;
    private final Set<Peer> reportedPeers;
    @Nullable
    private final List<Integer> supportedCapabilities;

    public GetPeersResponse(int requestNonce, Set<Peer> reportedPeers) {
        this(requestNonce, reportedPeers, Capabilities.getSupportedCapabilities(), Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetPeersResponse(int requestNonce,
                             Set<Peer> reportedPeers,
                             @Nullable List<Integer> supportedCapabilities,
                             int messageVersion) {
        super(messageVersion);
        this.requestNonce = requestNonce;
        this.reportedPeers = reportedPeers;
        this.supportedCapabilities = supportedCapabilities;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        final PB.GetPeersResponse.Builder builder = PB.GetPeersResponse.newBuilder()
                .setRequestNonce(requestNonce)
                .addAllReportedPeers(reportedPeers.stream()
                        .map(Peer::toProtoMessage)
                        .collect(Collectors.toList()));

        Optional.ofNullable(supportedCapabilities).ifPresent(e -> builder.addAllSupportedCapabilities(supportedCapabilities));

        return getNetworkEnvelopeBuilder()
                .setGetPeersResponse(builder)
                .build();
    }

    public static GetPeersResponse fromProto(PB.GetPeersResponse proto, int messageVersion) {
        HashSet<Peer> reportedPeers = new HashSet<>(
                proto.getReportedPeersList()
                        .stream()
                        .map(peer -> new Peer(new NodeAddress(peer.getNodeAddress().getHostName(),
                                peer.getNodeAddress().getPort())))
                        .collect(Collectors.toList()));
        return new GetPeersResponse(proto.getRequestNonce(),
                reportedPeers,
                proto.getSupportedCapabilitiesList().isEmpty() ? null : proto.getSupportedCapabilitiesList(),
                messageVersion);
    }
}
