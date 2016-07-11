package io.bitsquare.p2p.network.connection;


import io.bitsquare.p2p.network.connection.CloseConnectionReason;
import io.bitsquare.p2p.network.connection.Connection;

public interface ConnectionListener {
    void onConnection(Connection connection);

    void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection);

    void onError(Throwable throwable);
}
