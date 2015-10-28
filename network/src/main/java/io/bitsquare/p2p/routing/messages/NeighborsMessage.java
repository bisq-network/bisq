package io.bitsquare.p2p.routing.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.routing.Neighbor;

import java.util.HashMap;

public final class NeighborsMessage implements AuthenticationMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final HashMap<Address, Neighbor> neighbors;

    public NeighborsMessage(HashMap<Address, Neighbor> neighbors) {
        this.neighbors = neighbors;
    }

    @Override
    public String toString() {
        return "NeighborsMessage{" + "neighbors=" + neighbors + '}';
    }

}
