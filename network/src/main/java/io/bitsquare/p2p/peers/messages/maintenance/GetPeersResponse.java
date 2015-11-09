package io.bitsquare.p2p.peers.messages.maintenance;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;

import java.util.HashSet;

public final class GetPeersResponse extends MaintenanceMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final HashSet<Address> peerAddresses;

    public GetPeersResponse(HashSet<Address> peerAddresses) {
        this.peerAddresses = peerAddresses;
    }

    @Override
    public String toString() {
        return "GetPeersResponse{" +
                ", peerAddresses=" + peerAddresses +
                '}';
    }
}
