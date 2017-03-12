package io.bisq.p2p.network;

import io.bisq.messages.Message;

public interface MessageListener {
    void onMessage(Message message, Connection connection);
}
