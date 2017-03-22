package io.bisq.protobuffer.message.p2p.peers.peerexchange;

import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.p2p.SupportedCapabilitiesMessage;
import io.bisq.protobuffer.payload.p2p.peers.peerexchange.Peer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

public final class GetPeersResponse extends PeerExchangeMessage implements SupportedCapabilitiesMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final int requestNonce;
    public final HashSet<Peer> reportedPeers;

    @Nullable
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public GetPeersResponse(int requestNonce, HashSet<Peer> reportedPeers) {
        this.requestNonce = requestNonce;
        this.reportedPeers = reportedPeers;
    }


    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public String toString() {
        return "GetPeersResponse{" +
                "requestNonce=" + requestNonce +
                ", reportedPeers.size()=" + reportedPeers.size() +
                ", supportedCapabilities=" + supportedCapabilities +
                "} " + super.toString();
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder envelopeBuilder = PB.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);

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
