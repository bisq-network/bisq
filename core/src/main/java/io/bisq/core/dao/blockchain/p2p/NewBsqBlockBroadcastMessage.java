package io.bisq.core.dao.blockchain.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
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
        final PB.NewBsqBlockBroadcastMessage.Builder builder = PB.NewBsqBlockBroadcastMessage.newBuilder()
                .setBsqBlockBytes(ByteString.copyFrom(bsqBlockBytes));
        return NetworkEnvelope.getDefaultBuilder().setNewBsqBlockBroadcastMessage(builder).build();
    }

    public static NetworkEnvelope fromProto(PB.NetworkEnvelope envelope) {
        PB.NewBsqBlockBroadcastMessage msg = envelope.getNewBsqBlockBroadcastMessage();
        return new NewBsqBlockBroadcastMessage(msg.getBsqBlockBytes().toByteArray());
    }

}
