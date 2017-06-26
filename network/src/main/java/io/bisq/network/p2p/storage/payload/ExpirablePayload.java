package io.bisq.network.p2p.storage.payload;


import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.proto.persistable.PersistablePayload;

/**
 * Messages which support a time to live
 * <p/>
 * Implementations:
 *
 * @see StoragePayload
 * @see MailboxStoragePayload
 */
public interface ExpirablePayload extends NetworkPayload, PersistablePayload {
    /**
     * @return Time to live in milli seconds
     */
    long getTTL();
}
