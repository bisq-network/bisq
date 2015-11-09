package io.bitsquare.p2p.peers.messages.maintenance;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

public abstract class MaintenanceMessage implements Message {
    @Override
    public int networkId() {
        return Version.NETWORK_ID;
    }
}
