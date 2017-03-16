package io.bisq.p2p.network;

import io.bisq.network_messages.Message;

public interface MessageListener {
    void onMessage(Message message, Connection connection);
}
