package io.bisq.network.p2p.network;

import io.bisq.wire.message.Message;

public interface MessageListener {
    void onMessage(Message message, Connection connection);
}
