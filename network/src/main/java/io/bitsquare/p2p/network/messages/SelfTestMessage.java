package io.bitsquare.p2p.network.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

public final class SelfTestMessage implements Message {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final long nonce;

    public SelfTestMessage(long nonce) {
        this.nonce = nonce;
    }
}
