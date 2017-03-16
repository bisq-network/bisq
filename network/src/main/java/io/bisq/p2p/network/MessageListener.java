package io.bisq.p2p.network;

import io.bisq.message.Message;

public interface MessageListener {
    void onMessage(Message message, Connection connection);
}
