package io.bitsquare.p2p.routing;

import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;

public interface RoutingListener {
    void onFirstNeighborAdded(Neighbor neighbor);

    void onNeighborAdded(Neighbor neighbor);

    void onNeighborRemoved(Address address);

    // TODO remove
    void onConnectionAuthenticated(Connection connection);
}
