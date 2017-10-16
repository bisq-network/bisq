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
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.util.Utilities;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.P2PDataStorage;
import io.bisq.network.p2p.storage.payload.LazyProcessedPayload;
import io.bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.concurrent.TimeUnit;

// Object has about 94 raw bytes (about 101 bytes is size of PB object)
// With 100 000 entries we get 53.5 MB of data. Old entries will be shipped with the MapEntry resource file, 
// so only the newly added objects since the last release will not be loaded over the P2P network.
// TODO Get rid of sigPubKey and replace by hash of sigPubKey. That will reduce the data size to 118 bytes.
// Using EC signatures would produce longer signatures (71 bytes)
@Slf4j
@Value
public class AccountAgeWitness implements LazyProcessedPayload, PersistableNetworkPayload, PersistableEnvelope {

    private final byte[] hash;                      // Ripemd160(Sha256(data)) hash 20 bytes
    private final byte[] sigPubKeyHash;             // Ripemd160(Sha256(sigPubKey)) hash 20 bytes
    private final byte[] signature;                 // about 46 bytes
    private final long date;                        // 8 byte

    public AccountAgeWitness(byte[] hash,
                             byte[] sigPubKeyHash,
                             byte[] signature,
                             long date) {
        this.hash = hash;
        this.sigPubKeyHash = sigPubKeyHash;
        this.signature = signature;
        this.date = date;

        log.info("new AccountAgeWitness: hash={}, date={} ", Utilities.bytesAsHexString(hash), new Date(date));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public PB.PersistableNetworkPayload toProtoMessage() {
        final PB.AccountAgeWitness.Builder builder = PB.AccountAgeWitness.newBuilder()
                .setHash(ByteString.copyFrom(hash))
                .setSigPubKeyHash(ByteString.copyFrom(sigPubKeyHash))
                .setSignature(ByteString.copyFrom(signature))
                .setDate(date);
        return PB.PersistableNetworkPayload.newBuilder().setAccountAgeWitness(builder).build();
    }

    public PB.AccountAgeWitness toProtoAccountAgeWitness() {
        return toProtoMessage().getAccountAgeWitness();
    }

    public static AccountAgeWitness fromProto(PB.AccountAgeWitness proto) {
        return new AccountAgeWitness(
                proto.getHash().toByteArray(),
                proto.getSigPubKeyHash().toByteArray(),
                proto.getSignature().toByteArray(),
                proto.getDate());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    //TODO impl. here or in caller?
    // We allow max 1 day time difference
    public boolean isDateValid() {
        return new Date().getTime() - date < TimeUnit.DAYS.toMillis(1);
    }

    public P2PDataStorage.ByteArray getHashAsByteArray() {
        return new P2PDataStorage.ByteArray(hash);
    }

    @Override
    public String toString() {
        return "AccountAgeWitness{" +
                "\n     hash=" + Utilities.bytesAsHexString(hash) +
                ",\n     sigPubKeyHash=" + Utilities.bytesAsHexString(sigPubKeyHash) +
                ",\n     signature=" + Utilities.bytesAsHexString(signature) +
                ",\n     date=" + new Date(date) +
                "\n}";
    }
}
