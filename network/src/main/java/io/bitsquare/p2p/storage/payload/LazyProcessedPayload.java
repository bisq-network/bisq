package io.bitsquare.p2p.storage.payload;

import io.bitsquare.common.wire.Payload;

/**
 * Marker interface for payload which gets delayed processed at startup so we don't hit performance too much.
 * Used for TradeStatistics.
 */
public interface LazyProcessedPayload extends Payload {
}
