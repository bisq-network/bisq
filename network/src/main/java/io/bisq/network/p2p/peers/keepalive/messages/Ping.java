package io.bisq.network.p2p.peers.keepalive.messages;

import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class Ping extends NetworkEnvelope implements KeepAliveMessage {
    private final int nonce;
    private final int lastRoundTripTime;

    public Ping(int nonce, int lastRoundTripTime) {
        this(nonce, lastRoundTripTime, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Ping(int nonce, int lastRoundTripTime, int messageVersion) {
        super(messageVersion);
        this.nonce = nonce;
        this.lastRoundTripTime = lastRoundTripTime;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setPing(PB.Ping.newBuilder()
                        .setNonce(nonce)
                        .setLastRoundTripTime(lastRoundTripTime))
                .build();
    }

    public static Ping fromProto(PB.Ping proto, int messageVersion) {
        return new Ping(proto.getNonce(), proto.getLastRoundTripTime(), messageVersion);
    }
}
