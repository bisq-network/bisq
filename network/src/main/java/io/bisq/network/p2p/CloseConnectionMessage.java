package io.bisq.network.p2p;

import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class CloseConnectionMessage extends NetworkEnvelope {
    private final String reason;

    public CloseConnectionMessage(String reason) {
        this(reason, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CloseConnectionMessage(String reason, int messageVersion) {
        super(messageVersion);
        this.reason = reason;
    }


    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setCloseConnectionMessage(PB.CloseConnectionMessage.newBuilder()
                        .setReason(reason))
                .build();
    }

    public static CloseConnectionMessage fromProto(PB.CloseConnectionMessage proto, int messageVersion) {
        return new CloseConnectionMessage(proto.getReason(), messageVersion);
    }
}
