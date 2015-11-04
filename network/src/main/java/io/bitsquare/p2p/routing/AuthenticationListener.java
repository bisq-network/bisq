package io.bitsquare.p2p.routing;

import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;

public abstract class AuthenticationListener implements RoutingListener {
    public void onFirstPeerAdded(Peer peer) {
    }

    public void onPeerAdded(Peer peer) {
    }

    public void onPeerRemoved(Address address) {
    }

    abstract public void onConnectionAuthenticated(Connection connection);
}
