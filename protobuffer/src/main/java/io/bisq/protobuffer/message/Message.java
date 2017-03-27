package io.bisq.protobuffer.message;


import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.Marshaller;

import java.io.Serializable;

public interface Message extends Serializable, Marshaller {
    static PB.Envelope.Builder getBaseEnvelope() {
        return PB.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);
    }
    
    int getMessageVersion();

    PB.Envelope toProto();
}
