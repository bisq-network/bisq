package io.bitsquare.p2p.peers.messages.auth;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;

public abstract class AuthenticationMessage implements Message {
    private final int networkId = Version.NETWORK_ID;

    public final Address address;

    public AuthenticationMessage(Address address) {
        this.address = address;
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return "AuthenticationMessage{" +
                "networkId=" + networkId +
                '}';
    }
}
