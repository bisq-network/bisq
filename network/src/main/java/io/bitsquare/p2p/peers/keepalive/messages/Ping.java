package io.bitsquare.p2p.peers.keepalive.messages;

import io.bitsquare.app.Version;
import io.bitsquare.common.wire.proto.Messages;
import io.bitsquare.p2p.ProtoBufferMessage;

public final class Ping extends KeepAliveMessage implements ProtoBufferMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final int nonce;
    public final int lastRoundTripTime;

    public Ping(int nonce, int lastRoundTripTime) {
        this.nonce = nonce;
        this.lastRoundTripTime = lastRoundTripTime;
    }

    public Messages.Envelope toProtoBuf() {
        Messages.Envelope.Builder builder = Messages.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);
        return builder.setPing(builder.getPingBuilder()
                .setNonce(nonce)
                .setLastRoundTripTime(lastRoundTripTime)).build();
    }
    @Override
    public String toString() {
        return "Ping{" +
                ", nonce=" + nonce +
                "} " + super.toString();
    }
}
