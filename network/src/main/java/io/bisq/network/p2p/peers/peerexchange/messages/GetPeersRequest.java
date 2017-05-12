package io.bisq.network.p2p.peers.peerexchange.messages;

import io.bisq.common.app.Capabilities;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.SendersNodeAddressMessage;
import io.bisq.network.p2p.SupportedCapabilitiesMessage;
import io.bisq.network.p2p.peers.peerexchange.Peer;
import lombok.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@Value
public final class GetPeersRequest extends PeerExchangeMessage implements SendersNodeAddressMessage, SupportedCapabilitiesMessage {
    private final NodeAddress senderNodeAddress;
    private final int nonce;
    private final HashSet<Peer> reportedPeers;
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public GetPeersRequest(NodeAddress senderNodeAddress, int nonce, HashSet<Peer> reportedPeers) {
        checkNotNull(senderNodeAddress, "senderNodeAddress must not be null at GetPeersRequest");
        this.senderNodeAddress = senderNodeAddress;
        this.nonce = nonce;
        this.reportedPeers = reportedPeers;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.NetworkEnvelope.Builder envelopeBuilder = NetworkEnvelope.getDefaultBuilder();
        PB.GetPeersRequest.Builder msgBuilder = envelopeBuilder.getGetPeersRequestBuilder();
        msgBuilder.setNonce(nonce)
                .setSenderNodeAddress(
                        msgBuilder.getSenderNodeAddressBuilder()
                                .setHostName(senderNodeAddress.getHostName())
                                .setPort(senderNodeAddress.getPort()));
        msgBuilder.addAllSupportedCapabilities(supportedCapabilities);
        msgBuilder.addAllReportedPeers(reportedPeers.stream()
                .map(peer -> PB.Peer.newBuilder()
                        .setDate(peer.date.getTime())
                        .setNodeAddress(PB.NodeAddress.newBuilder()
                                .setHostName(peer.nodeAddress.getHostName())
                                .setPort(peer.nodeAddress.getPort())).build())
                .collect(Collectors.toList()));
        return envelopeBuilder.setGetPeersRequest(msgBuilder).build();
    }
}
