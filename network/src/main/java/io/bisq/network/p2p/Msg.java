package io.bisq.network.p2p;


import io.bisq.common.Marshaller;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;

public interface Msg extends Marshaller {
    static PB.Envelope.Builder getBaseEnvelope() {
        return PB.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);
    }

    int getMessageVersion();

    PB.Envelope toProto();
}
