package io.bitsquare.p2p.storage.data;

import java.io.Serializable;

/**
 * Messages which support a time to live
 * <p>
 * Implementations:
 *
 * @see StorageMessage
 * @see MailboxMessage
 */
public interface ExpirableMessage extends Serializable {
    /**
     * @return Time to live in milli seconds
     */
    long getTTL();
}
