package io.bisq.core.dao.blockchain.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.network.Msg;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.messages.BroadcastMsg;
import lombok.Getter;

@Getter
public final class NewBsqBlockBroadcastMsg extends BroadcastMsg {
    private final int messageVersion = Version.getP2PMessageVersion();

    private final byte[] bsqBlockBytes;

    public NewBsqBlockBroadcastMsg(byte[] bsqBlockBytes) {
        this.bsqBlockBytes = bsqBlockBytes;
    }

    @Override
    public PB.Envelope toProto() {
        final PB.NewBsqBlockBroadcastMsg.Builder builder = PB.NewBsqBlockBroadcastMsg.newBuilder()
                .setBsqBlockBytes(ByteString.copyFrom(bsqBlockBytes));
        return Msg.getEnv().setNewBsqBlockBroadcastMsg(builder).build();
    }

    public static Msg fromProto(PB.Envelope envelope) {
        PB.NewBsqBlockBroadcastMsg msg = envelope.getNewBsqBlockBroadcastMsg();
        return new NewBsqBlockBroadcastMsg(msg.getBsqBlockBytes().toByteArray());
    }

}
