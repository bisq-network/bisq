package io.bitsquare.p2p.peer;

import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.Connection;

public abstract class AuthenticationListener implements PeerListener {
    public void onFirstAuthenticatePeer(Peer peer) {
    }

    public void onPeerAdded(Peer peer) {
    }

    public void onPeerRemoved(Address address) {
    }

    abstract public void onConnectionAuthenticated(Connection connection);
}
