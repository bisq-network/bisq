package io.bitsquare.p2p.peers.messages.peers;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.peers.ReportedPeer;

import java.util.HashSet;

public final class GetPeersRequest extends PeerExchangeMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final NodeAddress senderNodeAddress;
    public final HashSet<ReportedPeer> reportedPeers;

    public GetPeersRequest(NodeAddress senderNodeAddress, HashSet<ReportedPeer> reportedPeers) {
        this.senderNodeAddress = senderNodeAddress;
        this.reportedPeers = reportedPeers;
    }

    @Override
    public String toString() {
        return "GetPeersRequest{" +
                "senderAddress=" + senderNodeAddress +
                ", reportedPeers=" + reportedPeers +
                super.toString() + "} ";
    }
}
