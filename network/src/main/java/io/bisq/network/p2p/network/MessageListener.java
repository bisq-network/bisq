package io.bisq.network.p2p.network;

import io.bisq.network.p2p.Message;

public interface MessageListener {
    void onMessage(Message message, Connection connection);
}
