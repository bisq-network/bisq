package io.bisq.network.p2p;

import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.Value;

@Value
public final class CloseConnectionMessage implements NetworkEnvelope {
    private final String reason;

    public CloseConnectionMessage(String reason) {
        this.reason = reason;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setCloseConnectionMessage(PB.CloseConnectionMessage.newBuilder()
                        .setReason(reason))
                .build();
    }

    public static CloseConnectionMessage fromProto(PB.CloseConnectionMessage proto) {
        return new CloseConnectionMessage(proto.getReason());
    }
}
