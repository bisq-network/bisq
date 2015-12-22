package io.bitsquare.p2p.peers.messages.auth;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;

public final class AuthenticationRejection extends AuthenticationMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final long requesterNonce;

    public AuthenticationRejection(Address address, long requesterNonce) {
        super(address);
        this.requesterNonce = requesterNonce;
    }

    @Override
    public String toString() {
        return "AuthenticationReject{" +
                "address=" + address +
                ", requesterNonce=" + requesterNonce +
                super.toString() + "} ";
    }
}
