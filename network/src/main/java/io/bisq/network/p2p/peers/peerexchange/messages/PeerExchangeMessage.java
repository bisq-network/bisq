package io.bisq.network.p2p.peers.peerexchange.messages;

import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;

abstract class PeerExchangeMessage implements NetworkEnvelope {
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
