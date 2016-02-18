package io.bitsquare.p2p.storage.data;

import io.bitsquare.common.wire.Payload;

/**
 * Messages which support a time to live
 * <p>
 * Implementations:
 *
 * @see StoragePayload
 * @see MailboxPayload
 */
public interface ExpirablePayload extends Payload {
    /**
     * @return Time to live in milli seconds
     */
    long getTTL();
}
