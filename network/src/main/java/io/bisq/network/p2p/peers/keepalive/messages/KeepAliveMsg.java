package io.bisq.network.p2p.peers.keepalive.messages;

import io.bisq.common.app.Version;
import io.bisq.common.network.Msg;

public abstract class KeepAliveMsg implements Msg {
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
