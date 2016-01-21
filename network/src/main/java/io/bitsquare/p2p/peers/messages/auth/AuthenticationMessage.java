package io.bitsquare.p2p.peers.messages.auth;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;

public abstract class AuthenticationMessage implements Message {
    private final int networkId = Version.getNetworkId();

    public final NodeAddress senderNodeAddress;

    public AuthenticationMessage(NodeAddress senderNodeAddress) {
        this.senderNodeAddress = senderNodeAddress;
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return ", address=" + (senderNodeAddress != null ? senderNodeAddress.toString() : "") +
                ", networkId=" + networkId +
                '}';
    }
}
