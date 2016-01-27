package io.bitsquare.p2p.peers.messages.peers;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.messages.SendersNodeAddressMessage;
import io.bitsquare.p2p.peers.ReportedPeer;

import java.util.HashSet;

public final class GetPeersRequest extends PeerExchangeMessage implements SendersNodeAddressMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    private final NodeAddress senderNodeAddress;
    public long nonce;
    public final HashSet<ReportedPeer> reportedPeers;

    public GetPeersRequest(NodeAddress senderNodeAddress, long nonce, HashSet<ReportedPeer> reportedPeers) {
        this.senderNodeAddress = senderNodeAddress;
        this.nonce = nonce;
        this.reportedPeers = reportedPeers;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return senderNodeAddress;
    }

    @Override
    public String toString() {
        return "GetPeersRequest{" +
                "senderNodeAddress=" + senderNodeAddress +
                ", requestNonce=" + nonce +
                ", reportedPeers.size()=" + reportedPeers.size() +
                super.toString() + "} ";
    }

}
