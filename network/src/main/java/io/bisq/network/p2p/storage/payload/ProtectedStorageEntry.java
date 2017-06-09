package io.bisq.network.p2p.storage.payload;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.crypto.Sig;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.security.PublicKey;

@Getter
@EqualsAndHashCode
public class ProtectedStorageEntry implements NetworkPayload, PersistablePayload {
    private final StoragePayload storagePayload;
    private final byte[] ownerPubKeyBytes;
    transient private final PublicKey ownerPubKey;
    private int sequenceNumber;
    private byte[] signature;
    private long creationTimeStamp;

    public ProtectedStorageEntry(StoragePayload storagePayload,
                                 PublicKey ownerPubKey,
                                 int sequenceNumber,
                                 byte[] signature) {
        this.storagePayload = storagePayload;
        ownerPubKeyBytes = Sig.getPublicKeyBytes(ownerPubKey);
        this.ownerPubKey = ownerPubKey;

        this.sequenceNumber = sequenceNumber;
        this.signature = signature;
        this.creationTimeStamp = System.currentTimeMillis();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected ProtectedStorageEntry(long creationTimeStamp,
                                    StoragePayload storagePayload,
                                    byte[] ownerPubKeyBytes,
                                    int sequenceNumber,
                                    byte[] signature) {
        this.storagePayload = storagePayload;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyBytes);

        this.sequenceNumber = sequenceNumber;
        this.signature = signature;
        this.creationTimeStamp = creationTimeStamp;

        maybeAdjustCreationTimeStamp();
    }

    public Message toProtoMessage() {
        return PB.ProtectedStorageEntry.newBuilder()
                .setStoragePayload((PB.StoragePayload) storagePayload.toProtoMessage())
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes))
                .setSequenceNumber(sequenceNumber)
                .setSignature(ByteString.copyFrom(signature))
                .setCreationTimeStamp(creationTimeStamp)
                .build();
    }

    public static ProtectedStorageEntry fromProto(PB.ProtectedStorageEntry proto,
                                                  NetworkProtoResolver resolver) {
        return new ProtectedStorageEntry(proto.getCreationTimeStamp(),
                StoragePayload.fromProto(proto.getStoragePayload(), resolver),
                proto.getOwnerPubKeyBytes().toByteArray(),
                proto.getSequenceNumber(),
                proto.getSignature().toByteArray());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void maybeAdjustCreationTimeStamp() {
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
