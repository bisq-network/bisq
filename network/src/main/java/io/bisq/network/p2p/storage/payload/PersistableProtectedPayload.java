package io.bisq.network.p2p.storage.payload;

import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;

/**
 * ProtectedStoragePayload which are persistable and removable
 * <p/>
 * Implementations:
 * io.bisq.alert.Alert
 */
public interface PersistableProtectedPayload extends ProtectedStoragePayload, PersistablePayload {

    static PersistableProtectedPayload fromProto(PB.StoragePayload storagePayload, NetworkProtoResolver networkProtoResolver) {
        return (PersistableProtectedPayload) networkProtoResolver.fromProto(storagePayload);
    }
}
