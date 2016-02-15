package io.bitsquare.p2p.peers.getdata.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.network.messages.AnonymousMessage;

public final class PreliminaryGetDataRequest implements AnonymousMessage, GetDataRequest {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final int messageVersion = Version.getP2PMessageVersion();
    private final long nonce;

    public PreliminaryGetDataRequest(long nonce) {
        this.nonce = nonce;
    }

    @Override
    public long getNonce() {
        return nonce;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "PreliminaryGetDataRequest{" +
                "messageVersion=" + messageVersion +
                ", nonce=" + nonce +
                '}';
    }
}
