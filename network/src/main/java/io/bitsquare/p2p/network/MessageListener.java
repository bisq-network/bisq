package io.bitsquare.p2p.network;

import io.bitsquare.messages.Message;

public interface MessageListener {
    void onMessage(Message message, Connection connection);
}
