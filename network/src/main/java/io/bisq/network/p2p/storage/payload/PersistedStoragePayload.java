package io.bisq.network.p2p.storage.payload;

import io.bisq.common.proto.persistable.PersistablePayload;

/**
 * Marker interface for payload which gets persisted.
 * Used for TradeStatistics.
 */
public interface PersistedStoragePayload extends StoragePayload, PersistablePayload {
}
