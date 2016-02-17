package io.bitsquare.p2p.peers.keepalive.messages;

import io.bitsquare.app.Version;

public final class Ping extends KeepAliveMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final int nonce;

    public Ping(int nonce) {
        this.nonce = nonce;
    }

    @Override
    public String toString() {
        return "Ping{" +
                ", nonce=" + nonce +
                "} " + super.toString();
    }
}
