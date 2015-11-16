package io.bitsquare.p2p.peers.messages.auth;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.peers.ReportedPeer;

import java.util.HashSet;

public final class GetPeersAuthResponse extends AuthenticationMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final Address address;
    public final HashSet<ReportedPeer> reportedPeers;

    public GetPeersAuthResponse(Address address, HashSet<ReportedPeer> reportedPeers) {
        this.address = address;
        this.reportedPeers = reportedPeers;
    }

    @Override
    public String toString() {
        return "GetPeersAuthResponse{" +
                "address=" + address +
                ", reportedPeers=" + reportedPeers +
                "} " + super.toString();
    }
}
