package io.bitsquare.p2p.peers.keepalive.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

public abstract class KeepAliveMessage implements Message {
    //TODO add serialVersionUID also in superclasses as changes would break compatibility

    // Should be like that code
   /* private final int messageVersion = Version.getP2PMessageVersion();

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }*/

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
