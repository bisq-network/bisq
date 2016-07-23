package io.bitsquare.p2p.peers.keepalive.messages;

import io.bitsquare.app.Version;

import javax.annotation.Nullable;
import java.util.ArrayList;

public final class Pong extends KeepAliveMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    @Nullable
    private ArrayList<Integer> supportedCapabilities = Version.getCapabilities();

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    public final int requestNonce;

    public Pong(int requestNonce) {
        this.requestNonce = requestNonce;
    }

    @Override
    public String toString() {
        return "Pong{" +
                "requestNonce=" + requestNonce +
                "} " + super.toString();
    }
}
