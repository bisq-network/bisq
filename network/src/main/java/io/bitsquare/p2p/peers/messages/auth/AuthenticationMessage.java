package io.bitsquare.p2p.peers.messages.auth;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;

public abstract class AuthenticationMessage implements Message {
    private final int networkId = Version.getNetworkId();

    public final Address senderAddress;

    public AuthenticationMessage(Address senderAddress) {
        this.senderAddress = senderAddress;
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return ", address=" + (senderAddress != null ? senderAddress.toString() : "") +
                ", networkId=" + networkId +
                '}';
    }
}
