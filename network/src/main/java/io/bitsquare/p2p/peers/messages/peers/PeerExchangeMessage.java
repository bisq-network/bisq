package io.bitsquare.p2p.peers.messages.peers;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

public abstract class PeerExchangeMessage implements Message {
    private final int networkId = Version.getNetworkId();

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return "PeerExchangeMessage{" +
                "networkId=" + networkId +
                '}';
    }
}
