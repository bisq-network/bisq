package io.bitsquare.p2p.peers.peerexchange.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.peers.peerexchange.ReportedPeer;

import java.util.HashSet;

public final class GetPeersResponse extends PeerExchangeMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final int requestNonce;
    public final HashSet<ReportedPeer> reportedPeers;

    public GetPeersResponse(int requestNonce, HashSet<ReportedPeer> reportedPeers) {
        this.requestNonce = requestNonce;
        this.reportedPeers = reportedPeers;
    }

    @Override
    public String toString() {
        return "GetPeersResponse{" +
                "requestNonce=" + requestNonce +
                ", reportedPeers.size()=" + reportedPeers.size() +
                "} " + super.toString();
    }
}
