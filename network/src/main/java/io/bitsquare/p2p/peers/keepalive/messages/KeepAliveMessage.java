package io.bitsquare.p2p.peers.keepalive.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

public abstract class KeepAliveMessage implements Message {
    private final int messageVersion = Version.getP2PMessageVersion();

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "KeepAliveMessage{" +
                "messageVersion=" + messageVersion +
                '}';
    }
}
