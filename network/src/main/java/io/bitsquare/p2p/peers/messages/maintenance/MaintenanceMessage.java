package io.bitsquare.p2p.peers.messages.maintenance;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

public abstract class MaintenanceMessage implements Message {
    private final int messageVersion = Version.getP2PMessageVersion();

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "MaintenanceMessage{" +
                "messageVersion=" + messageVersion +
                '}';
    }
}
