package io.bitsquare.p2p.peers.messages.data;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.network.messages.AnonymousMessage;

public final class PreliminaryGetDataRequest implements AnonymousMessage, GetDataRequest {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    private final int networkId = Version.getNetworkId();
    private final long nonce;

    public PreliminaryGetDataRequest(long nonce) {
        this.nonce = nonce;
    }

    @Override
    public long getNonce() {
        return nonce;
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return "PreliminaryGetDataRequest{" +
                "networkId=" + networkId +
                ", nonce=" + nonce +
                '}';
    }
}
