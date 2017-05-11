package io.bisq.common.network;


import io.bisq.common.Marshaller;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;

public interface Msg extends Marshaller {
    static PB.Msg.Builder getEnv() {
        return PB.Msg.newBuilder().setMsgVersion(Version.getP2PMessageVersion());
    }

    int getMessageVersion();

    PB.Msg toEnvelopeProto();
}
