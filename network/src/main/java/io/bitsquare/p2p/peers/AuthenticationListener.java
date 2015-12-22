package io.bitsquare.p2p.peers;

import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;

public interface AuthenticationListener {
    void onPeerAddressAuthenticated(Address peerAddress, Connection connection);
}
