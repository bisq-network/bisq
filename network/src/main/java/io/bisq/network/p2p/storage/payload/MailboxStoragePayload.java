package io.bisq.network.p2p.storage.payload;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.Sig;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.PrefixedSealedAndSignedMessage;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Payload which supports a time to live and sender and receiver's pub keys for storage operations.
 * It  differs from the ProtectedExpirableMessage in the way that the sender is permitted to do an add operation
 * but only the receiver is permitted to remove the data.
 * That is the typical requirement for a mailbox like system.
 * <p/>
 * Typical payloads are trade or dispute network_messages to be stored when the peer is offline.
 */
@Getter
@EqualsAndHashCode
@Slf4j
public final class MailboxStoragePayload implements ProtectedStoragePayload {
    private final PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage;
    private PublicKey senderPubKeyForAddOperation;
    private final byte[] senderPubKeyForAddOperationBytes;
    private PublicKey ownerPubKey;
    private final byte[] ownerPubKeyBytes;
    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    public MailboxStoragePayload(PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage,
                                 PublicKey senderPubKeyForAddOperation,
                                 PublicKey ownerPubKey) {
        this.prefixedSealedAndSignedMessage = prefixedSealedAndSignedMessage;
        this.senderPubKeyForAddOperation = senderPubKeyForAddOperation;
        this.ownerPubKey = ownerPubKey;

        senderPubKeyForAddOperationBytes = Sig.getPublicKeyBytes(senderPubKeyForAddOperation);
        ownerPubKeyBytes = Sig.getPublicKeyBytes(ownerPubKey);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private MailboxStoragePayload(PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage,
                                  byte[] senderPubKeyForAddOperationBytes,
                                  byte[] ownerPubKeyBytes,
                                  @Nullable Map<String, String> extraDataMap) {
        this.prefixedSealedAndSignedMessage = prefixedSealedAndSignedMessage;
        this.senderPubKeyForAddOperationBytes = senderPubKeyForAddOperationBytes;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        this.extraDataMap = extraDataMap;

        senderPubKeyForAddOperation = Sig.getPublicKeyFromBytes(senderPubKeyForAddOperationBytes);
        ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyBytes);
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        final PB.MailboxStoragePayload.Builder builder = PB.MailboxStoragePayload.newBuilder()
                .setPrefixedSealedAndSignedMessage(prefixedSealedAndSignedMessage.toProtoNetworkEnvelope().getPrefixedSealedAndSignedMessage())
                .setSenderPubKeyForAddOperationBytes(ByteString.copyFrom(senderPubKeyForAddOperationBytes))
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return PB.StoragePayload.newBuilder().setMailboxStoragePayload(builder).build();
    }

    public static MailboxStoragePayload fromProto(PB.MailboxStoragePayload proto) {
        return new MailboxStoragePayload(
                PrefixedSealedAndSignedMessage.fromPayloadProto(proto.getPrefixedSealedAndSignedMessage()),
                proto.getSenderPubKeyForAddOperationBytes().toByteArray(),
                proto.getOwnerPubKeyBytes().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TimeUnit.DAYS.toMillis(15);
    }
}
