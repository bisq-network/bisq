package io.bisq.common.network;


import io.bisq.common.Marshaller;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;

public interface Msg extends Marshaller {
    static PB.Envelope.Builder getEnv() {
        return PB.Envelope.newBuilder().setP2PMessageVersion(Version.getP2PMessageVersion());
    }

    int getMessageVersion();

    PB.Envelope toProto();
}
