package io.bisq.network.p2p.storage.messages;

import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;

public abstract class BroadcastMessage implements NetworkEnvelope {
    private final int messageVersion = Version.getP2PMessageVersion();

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "DataBroadcastMessage{" +
                "messageVersion=" + messageVersion +
                '}';
    }
}