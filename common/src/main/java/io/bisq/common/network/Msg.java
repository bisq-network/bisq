package io.bisq.common.network;


import com.google.protobuf.Message;
import io.bisq.common.Marshaller;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;

public interface Msg extends Marshaller {
    static PB.Msg.Builder getMsgBuilder() {
        return PB.Msg.newBuilder().setMsgVersion(Version.getP2PMessageVersion());
    }

    default Message toProtoMessage() {
        return toProtoMsg();
    }

    int getMsgVersion();

    PB.Msg toProtoMsg();
}
