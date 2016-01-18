package io.bitsquare.p2p.storage.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

public abstract class DataBroadcastMessage implements Message {
    private final int networkId = Version.getNetworkId();

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return "DataBroadcastMessage{" +
                "networkId=" + networkId +
                '}';
    }
}