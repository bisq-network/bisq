package io.bisq.protobuffer.payload;

import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.Marshaller;

/**
 * Marker interface for data which is sent over the wire
 */
public interface Payload extends Marshaller {
    static PB.Envelope.Builder getBaseEnvelope() {
        return PB.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);
    }
}
