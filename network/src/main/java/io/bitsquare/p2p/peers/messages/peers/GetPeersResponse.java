package io.bitsquare.p2p.peers.messages.peers;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.peers.ReportedPeer;

import java.util.HashSet;

public final class GetPeersResponse extends PeerExchangeMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final long requestNonce;
    public final HashSet<ReportedPeer> reportedPeers;

    public GetPeersResponse(long requestNonce, HashSet<ReportedPeer> reportedPeers) {
        this.requestNonce = requestNonce;
        this.reportedPeers = reportedPeers;
    }

    @Override
    public String toString() {
        return "GetPeersResponse{" +
                "requestNonce=" + requestNonce +
                ", reportedPeers=" + reportedPeers +
                super.toString() + "} ";
    }
}
