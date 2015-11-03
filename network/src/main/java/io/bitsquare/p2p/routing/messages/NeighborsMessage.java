package io.bitsquare.p2p.routing.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;

import java.util.ArrayList;

public final class NeighborsMessage implements AuthenticationMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final ArrayList<Address> neighborAddresses;

    public NeighborsMessage(ArrayList<Address> neighborAddresses) {
        this.neighborAddresses = neighborAddresses;
    }

    @Override
    public String toString() {
        return "NeighborsMessage{" + "neighborAddresses=" + neighborAddresses + '}';
    }

}
