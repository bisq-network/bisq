package io.bisq.network.p2p.peers.keepalive.messages;

import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.Value;

@Value
public final class Pong extends KeepAliveMessage {
    private final int requestNonce;

    public Pong(int requestNonce) {
        this.requestNonce = requestNonce;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.NetworkEnvelope.Builder msgBuilder = NetworkEnvelope.getDefaultBuilder();
        return msgBuilder.setPong(PB.Pong.newBuilder().setRequestNonce(requestNonce)).build();
    }
}
