package io.bitsquare.p2p.peers.peerexchange.messages;

import io.bitsquare.messages.app.Version;
import io.bitsquare.p2p.Message;

abstract class PeerExchangeMessage implements Message {
    //TODO add serialVersionUID also in superclasses as changes would break compatibility
    private final int messageVersion = Version.getP2PMessageVersion();

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "PeerExchangeMessage{" +
                "messageVersion=" + messageVersion +
                '}';
    }
}
