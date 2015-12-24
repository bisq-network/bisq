package io.bitsquare.p2p.peers.messages.peers;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.peers.ReportedPeer;

import java.util.HashSet;

public final class GetPeersRequest extends PeerExchangeMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final Address senderAddress;
    public final HashSet<ReportedPeer> reportedPeers;

    public GetPeersRequest(Address senderAddress, HashSet<ReportedPeer> reportedPeers) {
        this.senderAddress = senderAddress;
        this.reportedPeers = reportedPeers;
    }

    @Override
    public String toString() {
        return "GetPeersRequest{" +
                "senderAddress=" + senderAddress +
                ", reportedPeers=" + reportedPeers +
                super.toString() + "} ";
    }
}
