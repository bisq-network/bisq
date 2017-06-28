package io.bisq.network.p2p.network;


public interface ConnectionListener {
    void onConnection(Connection connection);

    void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection);

    void onError(Throwable throwable);
}
