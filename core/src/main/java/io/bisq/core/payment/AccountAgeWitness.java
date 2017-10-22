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
import io.bisq.network.p2p.storage.payload.DateTolerantPayload;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.concurrent.TimeUnit;

// Object has 28 raw bytes (33 bytes is size of ProtoBuffer object in storage list, 5 byte extra for list -> totalBytes = 5 + n*33)
// With 1 000 000 entries we get about 33 MB of data. Old entries will be shipped with the resource file,
// so only the newly added objects since the last release will be retrieved over the P2P network.
@Slf4j
@Value
public class AccountAgeWitness implements LazyProcessedPayload, PersistableNetworkPayload, PersistableEnvelope, DateTolerantPayload {
    private static final long TOLERANCE = TimeUnit.DAYS.toMillis(1);

    private final byte[] hash;                      // Ripemd160(Sha256(concatenated accountHash, signature and sigPubKey)); 20 bytes
    private final long date;                        // 8 byte

    public AccountAgeWitness(byte[] hash,
                             long date) {
        this.hash = hash;
        this.date = date;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////


    @Override
    public PB.PersistableNetworkPayload toProtoMessage() {
        final PB.AccountAgeWitness.Builder builder = PB.AccountAgeWitness.newBuilder()
            .setHash(ByteString.copyFrom(hash))
            .setDate(date);
        return PB.PersistableNetworkPayload.newBuilder().setAccountAgeWitness(builder).build();
    }

    public PB.AccountAgeWitness toProtoAccountAgeWitness() {
        return toProtoMessage().getAccountAgeWitness();
    }

    public static AccountAgeWitness fromProto(PB.AccountAgeWitness proto) {
        return new AccountAgeWitness(
            proto.getHash().toByteArray(),
            proto.getDate());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isDateInTolerance() {
        return Math.abs(new Date().getTime() - date) <= TOLERANCE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public P2PDataStorage.ByteArray getHashAsByteArray() {
        return new P2PDataStorage.ByteArray(hash);
    }

    @Override
    public String toString() {
        return "AccountAgeWitness{" +
            "\n     hash=" + Utilities.bytesAsHexString(hash) +
            ",\n     date=" + new Date(date) +
            "\n}";
    }
}
