package io.bitsquare.p2p.network;

import io.bitsquare.p2p.Message;

public interface MessageListener {
    void onMessage(Message message, Connection connection);
}
