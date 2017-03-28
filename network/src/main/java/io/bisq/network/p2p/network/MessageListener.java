package io.bisq.network.p2p.network;

import io.bisq.network.p2p.Msg;

public interface MessageListener {
    void onMessage(Msg msg, Connection connection);
}
