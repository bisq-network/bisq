package io.bitsquare.p2p.peers;

import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.Connection;

public interface AuthenticationListener {
    void onPeerAuthenticated(NodeAddress peerNodeAddress, Connection connection);
}
