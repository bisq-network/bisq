package io.bitsquare.p2p.routing;

import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;

public abstract class AuthenticationListener implements RoutingListener {
    public void onFirstNeighborAdded(Neighbor neighbor) {
    }

    public void onNeighborAdded(Neighbor neighbor) {
    }

    public void onNeighborRemoved(Address address) {
    }

    abstract public void onConnectionAuthenticated(Connection connection);
}
