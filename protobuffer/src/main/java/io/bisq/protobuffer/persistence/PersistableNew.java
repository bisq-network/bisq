package io.bisq.protobuffer.persistence;

import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.Marshaller;

/**
 * Marker interface for data which is sent over the wire
 */
//TODo rename after refactoring to Persistable
public interface PersistableNew extends Marshaller {
    static PB.Envelope.Builder getBaseEnvelope() {
        return PB.Envelope.newBuilder().setP2PNetworkVersion(Version.LOCAL_DB_VERSION);
    }
}
