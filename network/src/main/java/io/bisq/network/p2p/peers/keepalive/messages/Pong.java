package io.bisq.network.p2p.peers.keepalive.messages;

import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.Value;

@Value
public final class Pong implements KeepAliveMessage {
    private final int requestNonce;

    public Pong(int requestNonce) {
        this.requestNonce = requestNonce;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setPong(PB.Pong.newBuilder()
                        .setRequestNonce(requestNonce))
                .build();
    }

    public static Pong fromProto(PB.Pong proto) {
        return new Pong(proto.getRequestNonce());
    }
}
