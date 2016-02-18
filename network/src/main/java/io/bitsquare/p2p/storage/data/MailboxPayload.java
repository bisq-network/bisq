package io.bitsquare.p2p.storage.data;

import io.bitsquare.app.Version;
import io.bitsquare.crypto.PrefixedSealedAndSignedMessage;
import io.bitsquare.p2p.NodeAddress;

import java.security.PublicKey;
import java.util.concurrent.TimeUnit;

/**
 * Envelope message which support a time to live and sender and receivers pub keys for storage operations.
 * It  differs from the ProtectedExpirableMessage in the way that the sender is permitted to do an add operation
 * but only the receiver is permitted to remove the data.
 * That is the typical requirement for a mailbox like system.
 * <p>
 * Typical payloads are trade or dispute messages to be stored when the peer is offline.
 */
public final class MailboxPayload implements ExpirablePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final long TTL = TimeUnit.DAYS.toMillis(10);

    /**
     * The encrypted and signed payload message
     */
    public final PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage;

    /**
     * Used for check if the add operation is permitted.
     * senderStoragePublicKey has to be equal to the ownerPubKey of the ProtectedData
     *
     * @see ProtectedData#ownerPubKey
     * @see io.bitsquare.p2p.storage.P2PDataStorage#add(ProtectedData, NodeAddress)
     */
    public final PublicKey senderPubKeyForAddOperation;

    /**
     * Used for check if the remove operation is permitted.
     * senderStoragePublicKey has to be equal to the ownerPubKey of the ProtectedData
     *
     * @see ProtectedData#ownerPubKey
     * @see io.bitsquare.p2p.storage.P2PDataStorage#remove(ProtectedData, NodeAddress)
     */
    public final PublicKey receiverPubKeyForRemoveOperation;

    public MailboxPayload(PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage, PublicKey senderPubKeyForAddOperation, PublicKey receiverPubKeyForRemoveOperation) {
        this.prefixedSealedAndSignedMessage = prefixedSealedAndSignedMessage;
        this.senderPubKeyForAddOperation = senderPubKeyForAddOperation;
        this.receiverPubKeyForRemoveOperation = receiverPubKeyForRemoveOperation;
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MailboxPayload)) return false;

        MailboxPayload that = (MailboxPayload) o;

        return !(prefixedSealedAndSignedMessage != null ? !prefixedSealedAndSignedMessage.equals(that.prefixedSealedAndSignedMessage) : that.prefixedSealedAndSignedMessage != null);

    }

    @Override
    public int hashCode() {
        return prefixedSealedAndSignedMessage != null ? prefixedSealedAndSignedMessage.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "ExpirableMailboxPayload{" +
                "prefixedSealedAndSignedMessage=" + prefixedSealedAndSignedMessage +
                ", senderStoragePublicKey.hashCode()=" + senderPubKeyForAddOperation.hashCode() +
                ", receiverStoragePublicKey.hashCode()=" + receiverPubKeyForRemoveOperation.hashCode() +
                '}';
    }
}
