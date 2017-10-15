package io.bisq.network.p2p.storage.payload;


import io.bisq.common.proto.network.NetworkPayload;

/**
 * Messages which support a time to live
 * <p/>
 * Implementations:
 *
 * @see ProtectedStoragePayload
 * @see MailboxStoragePayload
 */
public interface ExpirablePayload extends NetworkPayload {
    /**
     * @return Time to live in milli seconds
     */
    long getTTL();
}
