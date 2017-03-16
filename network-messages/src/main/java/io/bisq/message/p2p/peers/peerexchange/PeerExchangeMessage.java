package io.bisq.message.p2p.peers.peerexchange;

import io.bisq.app.Version;
import io.bisq.message.Message;

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
