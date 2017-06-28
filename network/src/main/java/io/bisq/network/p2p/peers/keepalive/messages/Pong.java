package io.bisq.network.p2p.peers.keepalive.messages;

import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class Pong extends NetworkEnvelope implements KeepAliveMessage {
    private final int requestNonce;

    public Pong(int requestNonce) {
        this(requestNonce, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Pong(int requestNonce, int messageVersion) {
        super(messageVersion);
        this.requestNonce = requestNonce;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setPong(PB.Pong.newBuilder()
                        .setRequestNonce(requestNonce))
                .build();
    }

    public static Pong fromProto(PB.Pong proto, int messageVersion) {
        return new Pong(proto.getRequestNonce(), messageVersion);
    }
}
