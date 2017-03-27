package io.bisq.protobuffer.message.p2p.peers.peerexchange;

import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.Marshaller;
import io.bisq.protobuffer.message.SendersNodeAddressMessage;
import io.bisq.protobuffer.message.p2p.SupportedCapabilitiesMessage;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.protobuffer.payload.p2p.peers.peerexchange.Peer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public final class GetPeersRequest extends PeerExchangeMessage implements SendersNodeAddressMessage, SupportedCapabilitiesMessage, Marshaller {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final NodeAddress senderNodeAddress;
    public final int nonce;
    public final HashSet<Peer> reportedPeers;
    @Nullable
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

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
    public PB.Envelope toProto() {
        PB.Envelope.Builder envelopeBuilder = PB.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);

        PB.GetPeersRequest.Builder msgBuilder = envelopeBuilder.getGetPeersRequestBuilder();
        msgBuilder
                .setNonce(nonce)
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
