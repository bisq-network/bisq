/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.storage.payload;

import bisq.network.p2p.PrefixedSealedAndSignedMessage;

import bisq.common.crypto.Sig;
import bisq.common.util.ExtraDataMapValidator;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import org.springframework.util.CollectionUtils;

import java.security.PublicKey;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

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
public final class MailboxStoragePayload implements ProtectedStoragePayload, ExpirablePayload {
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
        this.extraDataMap = ExtraDataMapValidator.getValidatedExtraDataMap(extraDataMap);

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
