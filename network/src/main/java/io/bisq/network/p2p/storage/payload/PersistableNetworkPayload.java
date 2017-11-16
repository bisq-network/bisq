package io.bisq.network.p2p.storage.payload;

import io.bisq.common.proto.ProtoResolver;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;

/**
 * Marker interface for NetworkPayload which gets persisted in PersistableNetworkPayloadMap.
 * We store it as a list in PB to keep storage size small (map would use hash as key which is in data object anyway).
 * Not using a map also give more tolerance with data structure changes.
 * This data structure does not use a verification of the owners signature. ProtectedStoragePayload is used if that is required.
 * Currently we use it only for the AccountAgeWitness and TradeStatistics data.
 * It is used for an append only data storage because removal would require owner verification.
 */
public interface PersistableNetworkPayload extends NetworkPayload, PersistablePayload {

    static PersistableNetworkPayload fromProto(PB.PersistableNetworkPayload payload, ProtoResolver resolver) {
        return (PersistableNetworkPayload) resolver.fromProto(payload);
    }

    PB.PersistableNetworkPayload toProtoMessage();

    // Hash which will be used as key in the in-memory hashMap
    byte [] getHash();

    boolean verifyHashSize();
}
