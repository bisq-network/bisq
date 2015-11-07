package io.bitsquare.p2p.peers;

import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;

public interface PeerListener {
    void onFirstAuthenticatePeer(Peer peer);

    // TODO never used
    void onPeerAdded(Peer peer);

    // TODO never used
    void onPeerRemoved(Address address);

    void onConnectionAuthenticated(Connection connection);
}
