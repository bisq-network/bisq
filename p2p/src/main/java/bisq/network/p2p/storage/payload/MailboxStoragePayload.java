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
import bisq.network.p2p.storage.messages.AddOncePayload;

import bisq.common.crypto.Sig;
import bisq.common.util.CollectionUtils;
import bisq.common.util.ExtraDataMapValidator;

import com.google.protobuf.ByteString;

import java.security.PublicKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Payload which supports a time to live and sender and receiver's pub keys for storage operations.
 * It  differs from the ProtectedExpirableMessage in the way that the sender is permitted to do an add operation
 * but only the receiver is permitted to remove the data.
 * That is the typical requirement for a mailbox like system.
 * <p/>
 * Typical payloads are trade or dispute network_messages to be stored when the peer is offline.
 * Size depends on payload but typical size is 2000-3000 bytes
 */
@Getter
@EqualsAndHashCode
@Slf4j
public final class MailboxStoragePayload implements ProtectedStoragePayload, ExpirablePayload, AddOncePayload {
    public static final long TTL = TimeUnit.DAYS.toMillis(15);

    // Added in 1.5.5
    public static final String EXTRA_MAP_KEY_TTL = "ttl";

    private final PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage;
    private PublicKey senderPubKeyForAddOperation;
    private final byte[] senderPubKeyForAddOperationBytes;
    private PublicKey ownerPubKey;
    private final byte[] ownerPubKeyBytes;
    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.

    // We add optional TTL entry in v 1.5.5 so we can support different TTL for trade messages and for AckMessages
    @Nullable
    private Map<String, String> extraDataMap;

    public MailboxStoragePayload(PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage,
                                 @NotNull PublicKey senderPubKeyForAddOperation,
                                 PublicKey ownerPubKey,
                                 long ttl) {
        this.prefixedSealedAndSignedMessage = prefixedSealedAndSignedMessage;
        this.senderPubKeyForAddOperation = senderPubKeyForAddOperation;
        this.ownerPubKey = ownerPubKey;

        senderPubKeyForAddOperationBytes = Sig.getPublicKeyBytes(senderPubKeyForAddOperation);
        ownerPubKeyBytes = Sig.getPublicKeyBytes(ownerPubKey);

        // We do not permit longer TTL as the default one
        if (ttl < TTL) {
            extraDataMap = new HashMap<>();
            extraDataMap.put(EXTRA_MAP_KEY_TTL, String.valueOf(ttl));
        }
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
    public protobuf.StoragePayload toProtoMessage() {
        final protobuf.MailboxStoragePayload.Builder builder = protobuf.MailboxStoragePayload.newBuilder()
                .setPrefixedSealedAndSignedMessage(prefixedSealedAndSignedMessage.toProtoNetworkEnvelope().getPrefixedSealedAndSignedMessage())
                .setSenderPubKeyForAddOperationBytes(ByteString.copyFrom(senderPubKeyForAddOperationBytes))
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return protobuf.StoragePayload.newBuilder().setMailboxStoragePayload(builder).build();
    }

    public static MailboxStoragePayload fromProto(protobuf.MailboxStoragePayload proto) {
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
        if (extraDataMap != null && extraDataMap.containsKey(EXTRA_MAP_KEY_TTL)) {
            try {
                long ttl = Long.parseLong(extraDataMap.get(EXTRA_MAP_KEY_TTL));
                if (ttl < TTL) {
                    return ttl;
                }
            } catch (Throwable ignore) {
            }
        }
        // If not set in extraDataMap or value is invalid or too large we return default TTL
        return TTL;
    }
}
