package io.bitsquare.p2p.peers.messages.auth;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

public abstract class AuthenticationMessage implements Message {
    private final int networkId = Version.NETWORK_ID;

    @Override
    public int networkId() {
        return networkId;
    }
}
