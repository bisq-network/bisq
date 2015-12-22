package io.bitsquare.p2p.network;


public interface ConnectionListener {
    enum Reason {
        SOCKET_CLOSED,
        RESET,
        TIMEOUT,
        SHUT_DOWN,
        PEER_DISCONNECTED,
        UNKNOWN
    }

    void onConnection(Connection connection);

    void onDisconnect(Reason reason, Connection connection);

    void onError(Throwable throwable);
}
