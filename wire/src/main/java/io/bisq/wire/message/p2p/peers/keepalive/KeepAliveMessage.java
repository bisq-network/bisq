package io.bisq.wire.message.p2p.peers.keepalive;

import io.bisq.common.app.Version;
import io.bisq.wire.message.Message;

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
