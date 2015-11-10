package io.bitsquare.p2p.peers.messages.auth;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;

public final class AuthenticationRequest extends AuthenticationMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final Address address;
    public final long requesterNonce;

    public AuthenticationRequest(Address address, long requesterNonce) {
        this.address = address;
        this.requesterNonce = requesterNonce;
    }

    @Override
    public String toString() {
        return "AuthenticationRequest{" +
                "address=" + address +
                ", nonce=" + requesterNonce +
                "} " + super.toString();
    }
}
