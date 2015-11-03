package io.bitsquare.p2p.routing.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;

import java.util.ArrayList;

public final class GetNeighborsMessage implements AuthenticationMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final Address address;
    public final long challengerNonce;
    public final ArrayList<Address> neighborAddresses;

    public GetNeighborsMessage(Address address, long challengerNonce, ArrayList<Address> neighborAddresses) {
        this.address = address;
        this.challengerNonce = challengerNonce;
        this.neighborAddresses = neighborAddresses;
    }

    @Override
    public String toString() {
        return "GetNeighborsMessage{" +
                "address=" + address +
                ", challengerNonce=" + challengerNonce +
                ", neighborAddresses=" + neighborAddresses +
                '}';
    }
}
