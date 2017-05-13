package io.bisq.network.p2p.peers.keepalive.messages;

import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.Value;

@Value
public final class Ping implements KeepAliveMessage {
    private final int nonce;
    private final int lastRoundTripTime;

    public Ping(int nonce, int lastRoundTripTime) {
        this.nonce = nonce;
        this.lastRoundTripTime = lastRoundTripTime;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setPing(PB.Ping.newBuilder()
                        .setNonce(nonce)
                        .setLastRoundTripTime(lastRoundTripTime))
                .build();
    }

    public static Ping fromProto(PB.Ping proto) {
        return new Ping(proto.getNonce(), proto.getLastRoundTripTime());
    }
}
