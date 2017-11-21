package io.bisq.network.p2p.storage.payload;

import io.bisq.common.Payload;

/**
 * Marker interface for payload which gets delayed processed at startup so we don't hit performance too much.
 * Used for TradeStatistics and AccountAgeWitness.
 */
public interface LazyProcessedPayload extends Payload {
}
