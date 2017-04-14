package io.bisq.common.persistance;


import io.bisq.common.Marshaller;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;

public interface Msg extends Marshaller {
    static PB.Envelope.Builder getBaseEnvelope() {
        return PB.Envelope.newBuilder().setP2PMessageVersion(Version.getP2PMessageVersion());
    }

    int getMessageVersion();

    PB.Envelope toProto();
}
