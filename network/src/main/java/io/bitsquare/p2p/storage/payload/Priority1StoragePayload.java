package io.bitsquare.p2p.storage.payload;

/**
 * Marker interface for payload which gets delayed processed at startup so we don't hit performance too much.
 * Used for Offer.
 */
public interface Priority1StoragePayload extends StoragePayload {
}
