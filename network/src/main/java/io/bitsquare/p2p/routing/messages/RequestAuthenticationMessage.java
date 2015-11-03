package io.bitsquare.p2p.routing.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;

public final class RequestAuthenticationMessage implements AuthenticationMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final Address address;
    public final long nonce;

    public RequestAuthenticationMessage(Address address, long nonce) {
        this.address = address;
        this.nonce = nonce;
    }

    @Override
    public String toString() {
        return "RequestAuthenticationMessage{" +
                "address=" + address +
                ", nonce=" + nonce +
                '}';
    }
}
