package io.bitsquare.p2p.storage.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

public abstract class BroadcastMessage implements Message {
    //TODO add serialVersionUID also in superclasses as changes would break compatibility
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