package io.bitsquare.p2p.routing.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;

import java.util.ArrayList;

public final class GetPeersMessage implements AuthenticationMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final Address address;
    public final long challengerNonce;
    public final ArrayList<Address> peerAddresses;

    public GetPeersMessage(Address address, long challengerNonce, ArrayList<Address> peerAddresses) {
        this.address = address;
        this.challengerNonce = challengerNonce;
        this.peerAddresses = peerAddresses;
    }

    @Override
    public String toString() {
        return "GetPeersMessage{" +
                "address=" + address +
                ", challengerNonce=" + challengerNonce +
                ", peerAddresses=" + peerAddresses +
                '}';
    }
}
