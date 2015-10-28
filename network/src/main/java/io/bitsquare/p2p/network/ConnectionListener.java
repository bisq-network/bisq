package io.bitsquare.p2p.network;


import io.bitsquare.p2p.Address;

public interface ConnectionListener {

    enum Reason {
        SOCKET_CLOSED,
        RESET,
        TIMEOUT,
        SHUT_DOWN,
        PEER_DISCONNECTED,
        ALREADY_CLOSED,
        UNKNOWN
    }

    void onConnection(Connection connection);

    void onPeerAddressAuthenticated(Address peerAddress, Connection connection);

    void onDisconnect(Reason reason, Connection connection);

    void onError(Throwable throwable);
}
