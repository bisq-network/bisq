package bisq.core.support;

import bisq.network.p2p.storage.payload.ProcessOncePersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.crypto.Sig;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.CollectionUtils;
import bisq.common.util.ExtraDataMapValidator;

import com.google.protobuf.ByteString;

import java.security.PublicKey;

import java.util.Map;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * DelayedPayoutRecoveryPayload
 */
//@Immutable
@Slf4j
@Getter
@EqualsAndHashCode
//@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class DelayedPayoutRecoveryPayload implements ProcessOncePersistableNetworkPayload, ProtectedStoragePayload,
        PersistablePayload {

    protected final int blockHeight;
    protected final byte[] encryptedTxBytes;
    protected final byte[] initializationVector;
    protected final byte[] ownerPubKeyEncoded;
    @Getter
    @Setter
    private transient byte[] decryptedTxBytes = null;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    protected final Map<String, String> extraDataMap;

    // Used just for caching. Don't persist.
    private final transient PublicKey ownerPubKey;

    public DelayedPayoutRecoveryPayload(int blockHeight,
                                        byte[] encryptedTxBytes,
                                        byte[] initializationVector,
                                        PublicKey ownerPublicKey,
                                        @Nullable Map<String, String> extraDataMap) {
        this(blockHeight, encryptedTxBytes, initializationVector, Sig.getPublicKeyBytes(ownerPublicKey), extraDataMap);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private DelayedPayoutRecoveryPayload(int blockHeight,
            byte[] encryptedTxBytes,
            byte[] initializationVector,
            byte[] ownerPubPubKeyEncoded,
            @Nullable Map<String, String> extraDataMap) {
        this.blockHeight = blockHeight;
        this.encryptedTxBytes = encryptedTxBytes;
        this.initializationVector = initializationVector;
        this.ownerPubKeyEncoded = ownerPubPubKeyEncoded;
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);

        ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyEncoded);
    }

    private protobuf.DelayedPayoutRecoveryPayload.Builder getDelayedPayoutRecoveryPayloadBuilder() {
        final protobuf.DelayedPayoutRecoveryPayload.Builder builder = protobuf.DelayedPayoutRecoveryPayload.newBuilder()
                .setBlockHeight(blockHeight)
                .setEncryptedTxBytes(ByteString.copyFrom(encryptedTxBytes))
                .setInitializationVector(ByteString.copyFrom(initializationVector))
                .setOwnerPubKeyEncoded(ByteString.copyFrom(ownerPubKeyEncoded));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return builder;
    }

    @Override
    public protobuf.StoragePayload toProtoMessage() {
        return protobuf.StoragePayload.newBuilder().setDelayedPayoutRecoveryPayload(getDelayedPayoutRecoveryPayloadBuilder()).build();
    }

    public static DelayedPayoutRecoveryPayload fromProto(protobuf.DelayedPayoutRecoveryPayload proto) {
        return new DelayedPayoutRecoveryPayload(
                proto.getBlockHeight(),
                proto.getEncryptedTxBytes().toByteArray(),
                proto.getInitializationVector().toByteArray(),
                proto.getOwnerPubKeyEncoded().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DelayedPayoutRecoveryPayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PublicKey getOwnerPubKey() {
        return ownerPubKey;
    }
}
