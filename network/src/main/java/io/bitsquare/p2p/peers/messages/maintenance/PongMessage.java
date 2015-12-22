package io.bitsquare.p2p.peers.messages.maintenance;

import io.bitsquare.app.Version;

public final class PongMessage extends MaintenanceMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final long nonce;

    public PongMessage(long nonce) {
        this.nonce = nonce;
    }

    @Override
    public String toString() {
        return "PongMessage{" +
                "nonce=" + nonce +
                super.toString() + "} ";
    }
}
