package io.bitsquare.p2p.peers;

import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;

public interface PeerListener {
    void onFirstAuthenticatePeer(Peer peer);

    void onPeerAdded(Peer peer);

    void onPeerRemoved(Address address);

    // TODO remove
    void onConnectionAuthenticated(Connection connection);
}
