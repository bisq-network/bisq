package io.bisq.network_messages.payload;

import java.security.PublicKey;

/**
 * Messages which support ownership protection (using signatures) and a time to live
 * <p>
 * Implementations:
 * io.bisq.alert.Alert
 * io.bisq.arbitration.Arbitrator
 * io.bisq.trade.offer.OfferPayload
 */
public interface StoragePayload extends ExpirablePayload {
    /**
     * Used for check if the add or remove operation is permitted.
     * Only data owner can add or remove the data.
     * OwnerPubKey has to be equal to the ownerPubKey of the ProtectedData
     *
     * @return The public key of the data owner.
     * @see ProtectedStorageEntry#ownerPubKey
     * @see P2PDataStorage#add(ProtectedStorageEntry, NodeAddress)
     * @see P2PDataStorage#remove(ProtectedStorageEntry, NodeAddress)
     */
    PublicKey getOwnerPubKey();
}
