package io.bisq.network.p2p.storage.payload;

/**
 * Marker interface for payload which gets delayed processed at startup so we don't hit performance too much.
 * Used for TradeStatistics.
 */
public interface LazyProcessedStoragePayload extends StoragePayload {
}
