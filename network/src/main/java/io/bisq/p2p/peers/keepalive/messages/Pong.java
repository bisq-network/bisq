package io.bisq.p2p.peers.keepalive.messages;

import io.bisq.app.Version;
import io.bisq.common.wire.proto.Messages;
import io.bisq.messages.util.ProtoBufferUtils;

public final class Pong extends KeepAliveMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

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

    @Override
    public Messages.Envelope toProtoBuf() {
        Messages.Envelope.Builder baseEnvelope = ProtoBufferUtils.getBaseEnvelope();
        return baseEnvelope.setPong(Messages.Pong.newBuilder().setRequestNonce(requestNonce)).build();
    }
}
