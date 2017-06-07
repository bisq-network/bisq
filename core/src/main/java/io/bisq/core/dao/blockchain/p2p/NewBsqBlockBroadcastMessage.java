package io.bisq.core.dao.blockchain.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.messages.BroadcastMessage;
import lombok.Getter;

@Getter
public final class NewBsqBlockBroadcastMessage extends BroadcastMessage {
    private final int messageVersion = Version.getP2PMessageVersion();

    private final byte[] bsqBlockBytes;

    public NewBsqBlockBroadcastMessage(byte[] bsqBlockBytes) {
        this.bsqBlockBytes = bsqBlockBytes;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setNewBsqBlockBroadcastMessage(PB.NewBsqBlockBroadcastMessage.newBuilder()
                        .setBsqBlockBytes(ByteString.copyFrom(bsqBlockBytes)))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.NewBsqBlockBroadcastMessage proto) {
        return new NewBsqBlockBroadcastMessage(proto.getBsqBlockBytes().toByteArray());
    }

}
