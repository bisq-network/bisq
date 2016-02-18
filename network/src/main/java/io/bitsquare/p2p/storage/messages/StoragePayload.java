package io.bitsquare.p2p.storage.messages;

import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.storage.data.ProtectedData;

import java.security.PublicKey;

/**
 * Messages which support ownership protection (using signatures) and a time to live
 * <p>
 * Implementations:
 * io.bitsquare.alert.Alert
 * io.bitsquare.arbitration.Arbitrator
 * io.bitsquare.trade.offer.Offer
 */
public interface StoragePayload extends ExpirablePayload {
    /**
     * Used for check if the add or remove operation is permitted.
     * Only data owner can add or remove the data.
     * OwnerPubKey has to be equal to the ownerPubKey of the ProtectedData
     *
     * @return The public key of the data owner.
     * @see ProtectedData#ownerPubKey
     * @see io.bitsquare.p2p.storage.P2PDataStorage#add(ProtectedData, NodeAddress)
     * @see io.bitsquare.p2p.storage.P2PDataStorage#remove(ProtectedData, NodeAddress)
     */
    PublicKey getOwnerPubKey();
}
