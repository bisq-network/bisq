package io.bisq.protobuffer.payload.p2p.storage;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.p2p.PrefixedSealedAndSignedMessage;
import io.bisq.protobuffer.payload.StoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nullable;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Envelope message which support a time to live and sender and receiver's pub keys for storage operations.
 * It  differs from the ProtectedExpirableMessage in the way that the sender is permitted to do an add operation
 * but only the receiver is permitted to remove the data.
 * That is the typical requirement for a mailbox like system.
 * <p>
 * Typical payloads are trade or dispute network_messages to be stored when the peer is offline.
 */
@EqualsAndHashCode
@Slf4j
public final class MailboxStoragePayload implements StoragePayload {
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
     */
    public transient PublicKey senderPubKeyForAddOperation;
    private final byte[] senderPubKeyForAddOperationBytes;
    /**
     * Used for check if the remove operation is permitted.
     * senderStoragePublicKey has to be equal to the ownerPubKey of the ProtectedData
     */
    public transient PublicKey receiverPubKeyForRemoveOperation;
    private final byte[] receiverPubKeyForRemoveOperationBytes;
    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility 
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new 
    // field in a class would break that hash and therefore break the storage mechanism.
    @Getter
    @Nullable
    private Map<String, String> extraDataMap;

    // Called from domain
    public MailboxStoragePayload(PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage,
                                 PublicKey senderPubKeyForAddOperation,
                                 PublicKey receiverPubKeyForRemoveOperation) {
        this(prefixedSealedAndSignedMessage,
                new X509EncodedKeySpec(senderPubKeyForAddOperation.getEncoded()).getEncoded(),
                new X509EncodedKeySpec(receiverPubKeyForRemoveOperation.getEncoded()).getEncoded(),
                null);
    }

    // Called from PB
    public MailboxStoragePayload(PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage,
                                 byte[] senderPubKeyForAddOperationBytes,
                                 byte[] receiverPubKeyForRemoveOperationBytes,
                                 @Nullable Map<String, String> extraDataMap) {
        this.prefixedSealedAndSignedMessage = prefixedSealedAndSignedMessage;
        this.senderPubKeyForAddOperationBytes = senderPubKeyForAddOperationBytes;
        this.receiverPubKeyForRemoveOperationBytes = receiverPubKeyForRemoveOperationBytes;
        this.extraDataMap = extraDataMap;

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
    public PB.StoragePayload toProto() {
        final PB.MailboxStoragePayload.Builder builder = PB.MailboxStoragePayload.newBuilder()
                .setTTL(TTL)
                .setPrefixedSealedAndSignedMessage(prefixedSealedAndSignedMessage.toProto().getPrefixedSealedAndSignedMessage())
                .setSenderPubKeyForAddOperationBytes(ByteString.copyFrom(senderPubKeyForAddOperationBytes))
                .setReceiverPubKeyForRemoveOperationBytes(ByteString.copyFrom(receiverPubKeyForRemoveOperationBytes));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraDataMap);
        return PB.StoragePayload.newBuilder().setMailboxStoragePayload(builder).build();
    }

    @Override
    public String toString() {
        return "MailboxStoragePayload{" +
                "prefixedSealedAndSignedMessage=" + prefixedSealedAndSignedMessage +
                ", senderPubKeyForAddOperation=" + Hex.toHexString(senderPubKeyForAddOperation.getEncoded()) +
                ", receiverPubKeyForRemoveOperation=" + Hex.toHexString(receiverPubKeyForRemoveOperation.getEncoded()) +
                ", extraDataMap=" + extraDataMap +
                '}';
    }
}
