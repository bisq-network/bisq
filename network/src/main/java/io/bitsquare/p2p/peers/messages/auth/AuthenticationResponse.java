package io.bitsquare.p2p.peers.messages.auth;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;

public final class AuthenticationResponse extends AuthenticationMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final Address address;
    public final long requesterNonce;
    public final long responderNonce;

    public AuthenticationResponse(Address address, long requesterNonce, long responderNonce) {
        this.address = address;
        this.requesterNonce = requesterNonce;
        this.responderNonce = responderNonce;
    }

    @Override
    public String toString() {
        return "AuthenticationResponse{" +
                "address=" + address +
                ", requesterNonce=" + requesterNonce +
                ", challengerNonce=" + responderNonce +
                "} " + super.toString();
    }
}
