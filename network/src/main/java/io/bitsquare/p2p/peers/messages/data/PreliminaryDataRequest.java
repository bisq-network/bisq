package io.bitsquare.p2p.peers.messages.data;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.network.messages.AnonymousMessage;

public final class PreliminaryDataRequest implements AnonymousMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    private final int networkId = Version.getNetworkId();

    public PreliminaryDataRequest() {
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return "PreliminaryDataRequest{" +
                "networkId=" + networkId +
                '}';
    }
}
