package io.bisq.network.p2p.peers.peerexchange.messages;

import io.bisq.common.app.Capabilities;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.SupportedCapabilitiesMessage;
import io.bisq.network.p2p.peers.peerexchange.Peer;
import lombok.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

@Value
public final class GetPeersResponse extends PeerExchangeMessage implements SupportedCapabilitiesMessage {
    private final int requestNonce;
    private final HashSet<Peer> reportedPeers;
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public GetPeersResponse(int requestNonce, HashSet<Peer> reportedPeers) {
        this.requestNonce = requestNonce;
        this.reportedPeers = reportedPeers;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.NetworkEnvelope.Builder envelopeBuilder = NetworkEnvelope.getDefaultBuilder();

        PB.GetPeersResponse.Builder msgBuilder = PB.GetPeersResponse.newBuilder();
        msgBuilder.setRequestNonce(requestNonce);
        msgBuilder.addAllReportedPeers(reportedPeers.stream()
                .map(peer -> PB.Peer.newBuilder()
                        .setDate(peer.date.getTime())
                        .setNodeAddress(PB.NodeAddress.newBuilder()
                                .setHostName(peer.nodeAddress.getHostName())
                                .setPort(peer.nodeAddress.getPort())).build())
                .collect(Collectors.toList()));
        return envelopeBuilder.setGetPeersResponse(msgBuilder).build();
    }
}
