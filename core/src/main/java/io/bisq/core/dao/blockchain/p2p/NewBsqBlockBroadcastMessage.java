package io.bisq.core.dao.blockchain.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.messages.BroadcastMessage;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class NewBsqBlockBroadcastMessage extends BroadcastMessage {
    private final byte[] bsqBlockBytes;

    public NewBsqBlockBroadcastMessage(byte[] bsqBlockBytes) {
        this(bsqBlockBytes, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private NewBsqBlockBroadcastMessage(byte[] bsqBlockBytes, int messageVersion) {
        super(messageVersion);
        this.bsqBlockBytes = bsqBlockBytes;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setNewBsqBlockBroadcastMessage(PB.NewBsqBlockBroadcastMessage.newBuilder()
                        .setBsqBlockBytes(ByteString.copyFrom(bsqBlockBytes)))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.NewBsqBlockBroadcastMessage proto, int messageVersion) {
        return new NewBsqBlockBroadcastMessage(proto.getBsqBlockBytes().toByteArray(), messageVersion);
    }

}
