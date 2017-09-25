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

import com.google.protobuf.Message;
import io.bisq.common.crypto.Sig;
import io.bisq.network.p2p.storage.payload.LazyProcessedStoragePayload;
import io.bisq.network.p2p.storage.payload.PersistedStoragePayload;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Object has 118 raw bytes (not PB size)
@Slf4j
@EqualsAndHashCode(exclude = {"signaturePubKey"})
@Value
public class AccountAgeWitness implements LazyProcessedStoragePayload, PersistedStoragePayload {

    private final byte[] hash;                      // 32 bytes
    private final byte[] hashOfPubKey;              // 32 bytes
    private final byte[] signature;                 // 46 bytes
    private final long tradeDate;                   // 8 byte


    // Only used as cache for getOwnerPubKey
    transient private final PublicKey signaturePubKey;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;

    public AccountAgeWitness(byte[] hash,
                             byte[] hashOfPubKey,
                             byte[] signature,
                             long tradeDate) {

        this(hash,
                hashOfPubKey,
                signature,
                tradeDate,
                null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    AccountAgeWitness(byte[] hash,
                      byte[] hashOfPubKey,
                      byte[] signature,
                      long tradeDate,
                      @Nullable Map<String, String> extraDataMap) {
        this.hash = hash;
        this.hashOfPubKey = hashOfPubKey;
        this.signature = signature;
        this.tradeDate = tradeDate;
        this.extraDataMap = extraDataMap;

        signaturePubKey = Sig.getPublicKeyFromBytes(signature);
    }


    // TODO
    @Override
    public Message toProtoMessage() {
        return null;
    }
    
   /* @Override
    public PB.StoragePayload toProtoMessage() {
        final PB.PaymentAccountAgeWitness.Builder builder = PB.PaymentAccountAgeWitness.newBuilder()
                .setSignaturePubKeyBytes(ByteString.copyFrom(hash))
                .setSignaturePubKeyBytes(ByteString.copyFrom(pubKeyBytes))
                .setSignaturePubKeyBytes(ByteString.copyFrom(signaturePubKeyBytes));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return PB.StoragePayload.newBuilder().setPaymentAccountAgeWitness(builder).build();
    }

    public PB.PaymentAccountAgeWitness toProtoPaymentAccountAgeWitness() {
        return toProtoMessage().getPaymentAccountAgeWitness();
    }

    public static PaymentAccountAgeWitness fromProto(PB.PaymentAccountAgeWitness proto) {
        return new PaymentAccountAgeWitness(
                OfferPayload.Direction.fromProto(proto.getDirection()),
                proto.getHash().toByteArray(),
                proto.getPubKeyBytes().toByteArray(),
                proto.getSignaturePubKeyBytes().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }*/


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public long getTTL() {
        return TimeUnit.DAYS.toMillis(30);
    }

    @Override
    public PublicKey getOwnerPubKey() {
        return signaturePubKey;
    }

    //TODO impl. here or in caller?
    // We allow max 1 day time difference
    public boolean isTradeDateValid() {
        return new Date().getTime() - tradeDate < TimeUnit.DAYS.toMillis(1);
    }
}
