package io.bitsquare.p2p.network.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;

public final class CloseConnectionMessage implements Message {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    private final int networkId = Version.getNetworkId();
    public NodeAddress peerNodeAddress;

    public CloseConnectionMessage() {
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return "CloseConnectionMessage{" +
                ", networkId=" + networkId +
                '}';
    }
}
