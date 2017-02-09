package io.bitsquare.p2p.peers.peerexchange.messages;

import io.bitsquare.messages.app.Capabilities;
import io.bitsquare.messages.app.Version;
import io.bitsquare.common.wire.proto.Messages;
import io.bitsquare.p2p.messaging.SupportedCapabilitiesMessage;
import io.bitsquare.p2p.peers.peerexchange.Peer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

public final class  GetPeersResponse extends PeerExchangeMessage implements SupportedCapabilitiesMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final int requestNonce;
    public final HashSet<Peer> reportedPeers;

    @Nullable
    private ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

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
    public Messages.Envelope toProtoBuf() {
        Messages.Envelope.Builder envelopeBuilder = Messages.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);

        Messages.GetPeersResponse.Builder msgBuilder = Messages.GetPeersResponse.newBuilder();
        msgBuilder.setRequestNonce(requestNonce);
        msgBuilder.addAllReportedPeers(reportedPeers.stream()
                .map(peer -> Messages.Peer.newBuilder()
                        .setDate(peer.date.getTime())
                        .setNodeAddress(Messages.NodeAddress.newBuilder()
                                .setHostName(peer.nodeAddress.getHostName())
                                .setPort(peer.nodeAddress.getPort())).build())
                .collect(Collectors.toList()));
        return envelopeBuilder.setGetPeersResponse(msgBuilder).build();
    }
}
