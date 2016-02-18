package io.bitsquare.p2p.storage.messages;

import java.io.Serializable;

/**
 * Messages which support a time to live
 * <p>
 * Implementations:
 *
 * @see StoragePayload
 * @see MailboxPayload
 */
public interface ExpirablePayload extends Serializable {
    /**
     * @return Time to live in milli seconds
     */
    long getTTL();
}
