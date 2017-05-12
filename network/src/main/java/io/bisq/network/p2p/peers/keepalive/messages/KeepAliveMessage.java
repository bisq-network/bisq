package io.bisq.network.p2p.peers.keepalive.messages;

import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;

public abstract class KeepAliveMessage implements NetworkEnvelope {
    @Override
    public int getMessageVersion() {
        return Version.getP2PMessageVersion();
    }

    @Override
    public String toString() {
        return "KeepAliveMessage{" +
                "messageVersion=" + getMessageVersion() +
                '}';
    }
}
