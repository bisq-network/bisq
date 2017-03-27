package io.bisq.protobuffer.payload;


import io.bisq.protobuffer.payload.p2p.storage.MailboxStoragePayload;

/**
 * Messages which support a time to live
 * <p>
 * Implementations:
 *
 * @see StoragePayload
 * @see MailboxStoragePayload
 */
public interface ExpirablePayload extends Payload {
    /**
     * @return Time to live in milli seconds
     */
    long getTTL();
}
