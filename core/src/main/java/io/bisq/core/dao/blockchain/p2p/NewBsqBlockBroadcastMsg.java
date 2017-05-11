package io.bisq.core.dao.blockchain.p2p;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
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
    public PB.Msg toProtoMsg() {
        final PB.NewBsqBlockBroadcastMsg.Builder builder = PB.NewBsqBlockBroadcastMsg.newBuilder()
                .setBsqBlockBytes(ByteString.copyFrom(bsqBlockBytes));
        return Msg.getMsgBuilder().setNewBsqBlockBroadcastMsg(builder).build();
    }

    @Override
    public Message toProtoMessage() {
        return toProtoMsg().getNewBsqBlockBroadcastMsg();
    }

    public static Msg fromProto(PB.Msg envelope) {
        PB.NewBsqBlockBroadcastMsg msg = envelope.getNewBsqBlockBroadcastMsg();
        return new NewBsqBlockBroadcastMsg(msg.getBsqBlockBytes().toByteArray());
    }

}
