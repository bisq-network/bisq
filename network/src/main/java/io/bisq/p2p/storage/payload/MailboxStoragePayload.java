package io.bisq.p2p.storage.payload;

import com.google.protobuf.ByteString;
import io.bisq.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.p2p.NodeAddress;
import io.bisq.p2p.messaging.PrefixedSealedAndSignedMessage;
import io.bisq.p2p.peers.BroadcastHandler;
import io.bisq.p2p.storage.P2PDataStorage;
import io.bisq.p2p.storage.storageentry.ProtectedStorageEntry;
import io.bitsquare.common.wire.proto.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.TimeUnit;

/**
 * Envelope message which support a time to live and sender and receiver's pub keys for storage operations.
 * It  differs from the ProtectedExpirableMessage in the way that the sender is permitted to do an add operation
 * but only the receiver is permitted to remove the data.
 * That is the typical requirement for a mailbox like system.
 * <p>
 * Typical payloads are trade or dispute messages to be stored when the peer is offline.
 */
public final class MailboxStoragePayload implements StoragePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    private static final Logger log = LoggerFactory.getLogger(MailboxStoragePayload.class);

    private static final long TTL = TimeUnit.DAYS.toMillis(10);

    /**
     * The encrypted and signed payload message
     */
    public final PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage;

    /**
     * Used for check if the add operation is permitted.
     * senderStoragePublicKey has to be equal to the ownerPubKey of the ProtectedData
     *
     * @see ProtectedStorageEntry#ownerPubKey
     * @see P2PDataStorage#add(ProtectedStorageEntry, NodeAddress, BroadcastHandler.Listener, boolean)
     */
    public transient PublicKey senderPubKeyForAddOperation;
    private final byte[] senderPubKeyForAddOperationBytes;
    /**
     * Used for check if the remove operation is permitted.
     * senderStoragePublicKey has to be equal to the ownerPubKey of the ProtectedData
     *
     * @see ProtectedStorageEntry#ownerPubKey
     * @see P2PDataStorage#remove(ProtectedStorageEntry, NodeAddress, boolean)
     */
    public transient PublicKey receiverPubKeyForRemoveOperation;
    private final byte[] receiverPubKeyForRemoveOperationBytes;

    public MailboxStoragePayload(PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage, PublicKey senderPubKeyForAddOperation, PublicKey receiverPubKeyForRemoveOperation) {
        this(prefixedSealedAndSignedMessage, new X509EncodedKeySpec(senderPubKeyForAddOperation.getEncoded()).getEncoded(),
                new X509EncodedKeySpec(receiverPubKeyForRemoveOperation.getEncoded()).getEncoded());
    }

    public MailboxStoragePayload(PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage, byte[] senderPubKeyForAddOperationBytes, byte[] receiverPubKeyForRemoveOperationBytes) {
        this.prefixedSealedAndSignedMessage = prefixedSealedAndSignedMessage;
        this.senderPubKeyForAddOperationBytes = senderPubKeyForAddOperationBytes;
        this.receiverPubKeyForRemoveOperationBytes = receiverPubKeyForRemoveOperationBytes;
        init();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            init();
        } catch (Throwable t) {
            log.warn("Exception at readObject: " + t.getMessage() + "\nThis= " + this.toString());
        }
    }

    private void init() {
        try {
            senderPubKeyForAddOperation = KeyFactory.getInstance(Sig.KEY_ALGO, "BC").generatePublic(new X509EncodedKeySpec(senderPubKeyForAddOperationBytes));
            receiverPubKeyForRemoveOperation = KeyFactory.getInstance(Sig.KEY_ALGO, "BC").generatePublic(new X509EncodedKeySpec(receiverPubKeyForRemoveOperationBytes));
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            log.error("Couldn't create the  public keys", e);
        }
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return receiverPubKeyForRemoveOperation;
    }

    @Override
    public Messages.StoragePayload toProtoBuf() {
        return Messages.StoragePayload.newBuilder().setMailboxStoragePayload(Messages.MailboxStoragePayload.newBuilder()
                .setTTL(TTL)
                .setPrefixedSealedAndSignedMessage(prefixedSealedAndSignedMessage.toProtoBuf().getPrefixedSealedAndSignedMessage())
                .setSenderPubKeyForAddOperationBytes(ByteString.copyFrom(senderPubKeyForAddOperationBytes))
                .setReceiverPubKeyForRemoveOperationBytes(ByteString.copyFrom(receiverPubKeyForRemoveOperationBytes))).build();
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MailboxStoragePayload)) return false;

        MailboxStoragePayload that = (MailboxStoragePayload) o;

        return !(prefixedSealedAndSignedMessage != null ? !prefixedSealedAndSignedMessage.equals(that.prefixedSealedAndSignedMessage) : that.prefixedSealedAndSignedMessage != null);

    }

    @Override
    public int hashCode() {
        return prefixedSealedAndSignedMessage != null ? prefixedSealedAndSignedMessage.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MailboxStoragePayload{" +
                "prefixedSealedAndSignedMessage=" + prefixedSealedAndSignedMessage +
                ", senderPubKeyForAddOperation.hashCode()=" + (senderPubKeyForAddOperation != null ? senderPubKeyForAddOperation.hashCode() : "null") +
                ", receiverPubKeyForRemoveOperation.hashCode()=" + (receiverPubKeyForRemoveOperation != null ? receiverPubKeyForRemoveOperation.hashCode() : "null") +
                '}';
    }
}
