package io.bitsquare.p2p.routing;

import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;

public interface RoutingListener {
    void onFirstPeerAdded(Peer peer);

    void onPeerAdded(Peer peer);

    void onPeerRemoved(Address address);

    // TODO remove
    void onConnectionAuthenticated(Connection connection);
}
