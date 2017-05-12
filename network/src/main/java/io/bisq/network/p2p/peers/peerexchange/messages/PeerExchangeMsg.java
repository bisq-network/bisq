package io.bisq.network.p2p.peers.peerexchange.messages;

import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;

abstract class PeerExchangeMsg implements NetworkEnvelope {
    //TODO add serialVersionUID also in superclasses as changes would break compatibility
    private final int messageVersion = Version.getP2PMessageVersion();

    @Override
    public int getMsgVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "PeerExchangeMessage{" +
                "messageVersion=" + messageVersion +
                '}';
    }
}
