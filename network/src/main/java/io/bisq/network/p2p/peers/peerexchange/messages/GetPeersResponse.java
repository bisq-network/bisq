package io.bisq.network.p2p.peers.peerexchange.messages;

import io.bisq.common.app.Capabilities;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.SupportedCapabilitiesMessage;
import io.bisq.network.p2p.peers.peerexchange.Peer;
import lombok.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

@Value
public final class GetPeersResponse implements PeerExchangeMessage, SupportedCapabilitiesMessage {
    private final int requestNonce;
    private final HashSet<Peer> reportedPeers;
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public GetPeersResponse(int requestNonce, HashSet<Peer> reportedPeers) {
        this.requestNonce = requestNonce;
        this.reportedPeers = reportedPeers;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setGetPeersResponse(PB.GetPeersResponse.newBuilder()
                        .setRequestNonce(requestNonce)
                        .addAllReportedPeers(reportedPeers.stream()
                                .map(Peer::toProtoMessage)
                                .collect(Collectors.toList()))
                        .addAllSupportedCapabilities(supportedCapabilities))
                .build();
    }

    public static GetPeersResponse fromProto(PB.GetPeersResponse getPeersResponse) {
        HashSet<Peer> reportedPeers = new HashSet<>(
                getPeersResponse.getReportedPeersList()
                        .stream()
                        .map(peer -> new Peer(new NodeAddress(peer.getNodeAddress().getHostName(),
                                peer.getNodeAddress().getPort())))
                        .collect(Collectors.toList()));
        return new GetPeersResponse(getPeersResponse.getRequestNonce(), reportedPeers);
    }
}
