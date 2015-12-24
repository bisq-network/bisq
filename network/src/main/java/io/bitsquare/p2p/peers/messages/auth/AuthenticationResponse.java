package io.bitsquare.p2p.peers.messages.auth;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.peers.ReportedPeer;

import java.util.HashSet;

public final class AuthenticationResponse extends AuthenticationMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final long responderNonce;
    public final HashSet<ReportedPeer> reportedPeers;

    public AuthenticationResponse(Address senderAddress, long responderNonce, HashSet<ReportedPeer> reportedPeers) {
        super(senderAddress);
        this.responderNonce = responderNonce;
        this.reportedPeers = reportedPeers;
    }

    @Override
    public String toString() {
        return "AuthenticationResponse{" +
                "address=" + senderAddress +
                ", responderNonce=" + responderNonce +
                ", reportedPeers=" + reportedPeers +
                super.toString() + "} ";
    }
}
