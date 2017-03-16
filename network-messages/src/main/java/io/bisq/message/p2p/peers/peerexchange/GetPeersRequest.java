package io.bisq.message.p2p.peers.peerexchange;

import io.bisq.NodeAddress;
import io.bisq.app.Capabilities;
import io.bisq.app.Version;
import io.bisq.common.wire.proto.Messages;
import io.bisq.message.SendersNodeAddressMessage;
import io.bisq.message.ToProtoBuffer;
import io.bisq.message.p2p.SupportedCapabilitiesMessage;
import io.bisq.payload.p2p.peers.peerexchange.Peer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public final class GetPeersRequest extends PeerExchangeMessage implements SendersNodeAddressMessage, SupportedCapabilitiesMessage, ToProtoBuffer {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final NodeAddress senderNodeAddress;
    public final int nonce;
    public final HashSet<Peer> reportedPeers;
    @Nullable
    private ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public GetPeersRequest(NodeAddress senderNodeAddress, int nonce, HashSet<Peer> reportedPeers) {
        checkNotNull(senderNodeAddress, "senderNodeAddress must not be null at GetPeersRequest");
        this.senderNodeAddress = senderNodeAddress;
        this.nonce = nonce;
        this.reportedPeers = reportedPeers;
    }

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return senderNodeAddress;
    }

    @Override
    public String toString() {
        return "GetPeersRequest{" +
                "senderNodeAddress=" + senderNodeAddress +
                ", nonce=" + nonce +
                ", reportedPeers.size()=" + reportedPeers.size() +
                ", supportedCapabilities=" + supportedCapabilities +
                "} " + super.toString();
    }

    @Override
    public Messages.Envelope toProtoBuf() {
        Messages.Envelope.Builder envelopeBuilder = Messages.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);

        Messages.GetPeersRequest.Builder msgBuilder = envelopeBuilder.getGetPeersRequestBuilder();
        msgBuilder
                .setNonce(nonce)
                .setSenderNodeAddress(
                        msgBuilder.getSenderNodeAddressBuilder()
                                .setHostName(senderNodeAddress.getHostName())
                                .setPort(senderNodeAddress.getPort()));
        msgBuilder.addAllSupportedCapabilities(supportedCapabilities);
        msgBuilder.addAllReportedPeers(reportedPeers.stream()
                .map(peer -> Messages.Peer.newBuilder()
                        .setDate(peer.date.getTime())
                        .setNodeAddress(Messages.NodeAddress.newBuilder()
                                .setHostName(peer.nodeAddress.getHostName())
                                .setPort(peer.nodeAddress.getPort())).build())
                .collect(Collectors.toList()));
        return envelopeBuilder.setGetPeersRequest(msgBuilder).build();
    }
}
