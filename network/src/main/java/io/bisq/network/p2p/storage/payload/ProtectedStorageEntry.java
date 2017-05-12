package io.bisq.network.p2p.storage.payload;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.crypto.Sig;
import io.bisq.common.network.NetworkPayload;
import io.bisq.common.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

@Getter
@EqualsAndHashCode
public class ProtectedStorageEntry implements NetworkPayload, PersistablePayload {
    private final StoragePayload storagePayload;
    private final byte[] ownerPubKeyBytes;
    private final PublicKey ownerPubKey;

    private int sequenceNumber;
    private byte[] signature;
    private long creationTimeStamp;

    public ProtectedStorageEntry(StoragePayload storagePayload, PublicKey ownerPubKey, int sequenceNumber, byte[] signature) {
        this.storagePayload = storagePayload;
        this.ownerPubKey = ownerPubKey;
        this.sequenceNumber = sequenceNumber;
        this.signature = signature;
        this.creationTimeStamp = System.currentTimeMillis();
        this.ownerPubKeyBytes = new X509EncodedKeySpec(this.ownerPubKey.getEncoded()).getEncoded();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ProtectedStorageEntry(long creationTimeStamp, StoragePayload storagePayload, byte[] ownerPubKeyBytes,
                                 int sequenceNumber, byte[] signature) {
        this.storagePayload = storagePayload;
        this.sequenceNumber = sequenceNumber;
        this.signature = signature;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        this.creationTimeStamp = creationTimeStamp;
        ownerPubKey = Sig.getSigPublicKeyFromBytes(ownerPubKeyBytes);
        checkCreationTimeStamp();
    }

    public Message toProtoMessage() {
        return PB.ProtectedStorageEntry.newBuilder()
                .setStoragePayload((PB.StoragePayload) storagePayload.toProtoMessage())
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes))
                .setSequenceNumber(sequenceNumber)
                .setSignature(ByteString.copyFrom(signature))
                .setCreationTimeStamp(creationTimeStamp).build();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkCreationTimeStamp() {
        // We don't allow creation date in the future, but we cannot be too strict as clocks are not synced
        if (creationTimeStamp > System.currentTimeMillis())
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
}
