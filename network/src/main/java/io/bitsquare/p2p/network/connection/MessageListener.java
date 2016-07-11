package io.bitsquare.p2p.network.connection;

import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.network.connection.Connection;

public interface MessageListener {
    void onMessage(Message message, Connection connection);
}
