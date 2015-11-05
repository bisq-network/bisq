package io.bitsquare.p2p.peer.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;

import java.util.ArrayList;

public final class PeersMessage implements AuthenticationMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final Address address;
    public final ArrayList<Address> peerAddresses;

    public PeersMessage(Address address, ArrayList<Address> peerAddresses) {
        this.address = address;
        this.peerAddresses = peerAddresses;
    }

    @Override
    public String toString() {
        return "PeersMessage{" + "peerAddresses=" + peerAddresses + '}';
    }

}
