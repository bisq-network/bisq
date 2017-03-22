package io.bisq.protobuffer.payload.p2p.storage;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.payload.Payload;
import io.bisq.protobuffer.payload.StoragePayload;
import lombok.EqualsAndHashCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

@EqualsAndHashCode
public class ProtectedStorageEntry implements Payload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(ProtectedStorageEntry.class);

    // Payload
    protected final StoragePayload storagePayload;
    private final byte[] ownerPubKeyBytes;
    public int sequenceNumber;
    public byte[] signature;
    public long creationTimeStamp;

    // Domain
    public transient PublicKey ownerPubKey;

    public ProtectedStorageEntry(StoragePayload storagePayload, PublicKey ownerPubKey, int sequenceNumber, byte[] signature) {
        this.storagePayload = storagePayload;
        this.ownerPubKey = ownerPubKey;
        this.sequenceNumber = sequenceNumber;
        this.signature = signature;
        this.creationTimeStamp = System.currentTimeMillis();
        this.ownerPubKeyBytes = new X509EncodedKeySpec(this.ownerPubKey.getEncoded()).getEncoded();
    }

    public ProtectedStorageEntry(long creationTimeStamp, StoragePayload storagePayload, byte[] ownerPubKeyBytes,
                                 int sequenceNumber, byte[] signature) {
        this.storagePayload = storagePayload;
        this.sequenceNumber = sequenceNumber;
        this.signature = signature;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        this.creationTimeStamp = creationTimeStamp;
        init();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            init();
        } catch (Throwable t) {
            log.warn("Exception at readObject: " + t.getMessage());
        }
    }

    private void init() {
        try {
            ownerPubKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC").generatePublic(new X509EncodedKeySpec(ownerPubKeyBytes));
            checkCreationTimeStamp();
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            log.error("Couldn't create the pubkey", e);
        }
    }

    public StoragePayload getStoragePayload() {
        return storagePayload;
    }

    public void checkCreationTimeStamp() {
        // We don't allow creation date in the future, but we cannot be too strict as clocks are not synced
        // The 0 test is needed to be backward compatible as creationTimeStamp (timeStamp) was transient before 0.4.7
        // TODO "|| creationTimeStamp == 0" can removed after we don't support 0.4.6 anymore
        if (creationTimeStamp > System.currentTimeMillis() || creationTimeStamp == 0)
            creationTimeStamp = System.currentTimeMillis();
    }

    public void refreshTTL() {
        creationTimeStamp = System.currentTimeMillis();
    }

    public void backDate() {
        creationTimeStamp -= storagePayload.getTTL() / 2;
    }

    public void updateSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public void updateSignature(byte[] signature) {
        this.signature = signature;
    }

    public boolean isExpired() {
        return (System.currentTimeMillis() - creationTimeStamp) > storagePayload.getTTL();
    }

    public Message toProto() {
        return PB.ProtectedStorageEntry.newBuilder().setStoragePayload((PB.StoragePayload) storagePayload.toProto())
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes)).setSequenceNumber(sequenceNumber)
                .setSignature(ByteString.copyFrom(signature)).setCreationTimeStamp(creationTimeStamp).build();
    }

    @Override
    public String toString() {
        return "ProtectedStorageEntry{" +
                "expirablePayload=" + storagePayload +
                ", creationTimeStamp=" + creationTimeStamp +
                ", sequenceNumber=" + sequenceNumber +
                ", ownerPubKey.hashCode()=" + (ownerPubKey != null ? ownerPubKey.hashCode() : "null") +
                ", signature.hashCode()=" + (signature != null ? Arrays.toString(signature).hashCode() : "null") +
                '}';
    }

}
