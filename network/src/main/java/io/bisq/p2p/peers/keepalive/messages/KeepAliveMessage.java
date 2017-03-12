package io.bisq.p2p.peers.keepalive.messages;

import io.bisq.app.Version;
import io.bisq.messages.Message;

public abstract class KeepAliveMessage implements Message {
    //TODO add serialVersionUID also in superclasses as changes would break compatibility
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
