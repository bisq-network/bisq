/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.payment;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.Sig;
import io.bisq.common.util.Utilities;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.LazyProcessedStoragePayload;
import io.bisq.network.p2p.storage.payload.PersistedStoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

// Object has 529 raw bytes (535 bytes is size of PB.AccountAgeWitness -> extraDataMap is null)
@Slf4j
@EqualsAndHashCode(exclude = {"signaturePubKey"})
@Value
public class AccountAgeWitness implements LazyProcessedStoragePayload, PersistedStoragePayload {

    private final byte[] hash;                      // 32 bytes
    private final byte[] sigPubKey;                 // about 443 bytes
    private final byte[] signature;                 // 46 bytes
    private final long date;                        // 8 byte


    // Only used as cache for getOwnerPubKey
    transient private final PublicKey signaturePubKey;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    public AccountAgeWitness(byte[] hash,
                             byte[] sigPubKey,
                             byte[] signature) {

        this(hash,
                sigPubKey,
                signature,
                new Date().getTime(),
                null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AccountAgeWitness(byte[] hash,
                              byte[] sigPubKey,
                              byte[] signature,
                              long date,
                              @Nullable Map<String, String> extraDataMap) {
        this.hash = hash;
        this.sigPubKey = sigPubKey;
        this.signature = signature;
        this.date = date;
        this.extraDataMap = extraDataMap;

        signaturePubKey = Sig.getPublicKeyFromBytes(sigPubKey);
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        final PB.AccountAgeWitness.Builder builder = PB.AccountAgeWitness.newBuilder()
                .setHash(ByteString.copyFrom(hash))
                .setSigPubKey(ByteString.copyFrom(sigPubKey))
                .setSignature(ByteString.copyFrom(signature))
                .setDate(date);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return PB.StoragePayload.newBuilder().setAccountAgeWitness(builder).build();
    }

    public PB.AccountAgeWitness toProtoAccountAgeWitness() {
        return toProtoMessage().getAccountAgeWitness();
    }

    public static AccountAgeWitness fromProto(PB.AccountAgeWitness proto) {
        return new AccountAgeWitness(
                proto.getHash().toByteArray(),
                proto.getSigPubKey().toByteArray(),
                proto.getSignature().toByteArray(),
                proto.getDate(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    //TODO needed?
    @Override
    public long getTTL() {
        return TimeUnit.SECONDS.toMillis(1);
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return signaturePubKey;
    }

    //TODO impl. here or in caller?
    // We allow max 1 day time difference
    public boolean isDateValid() {
        return new Date().getTime() - date < TimeUnit.DAYS.toMillis(1);
    }

    public String getHashAsHex() {
        return Utilities.encodeToHex(hash);
    }
}
