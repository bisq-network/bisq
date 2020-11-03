/*
 * This file is part of Bisq.
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

package bisq.core.account.witness;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.DateTolerantPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProcessOncePersistableNetworkPayload;

import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.time.Clock;
import java.time.Instant;

import java.util.concurrent.TimeUnit;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

// Object has 28 raw bytes (33 bytes is size of ProtoBuffer object in storage list, 5 byte extra for list -> totalBytes = 5 + n*33)
// With 1 000 000 entries we get about 33 MB of data. Old entries will be shipped with the resource file,
// so only the newly added objects since the last release will be retrieved over the P2P network.
@Slf4j
@Value
public class AccountAgeWitness implements ProcessOncePersistableNetworkPayload, PersistableNetworkPayload, DateTolerantPayload {
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
    public protobuf.PersistableNetworkPayload toProtoMessage() {
        final protobuf.AccountAgeWitness.Builder builder = protobuf.AccountAgeWitness.newBuilder()
                .setHash(ByteString.copyFrom(hash))
                .setDate(date);
        return protobuf.PersistableNetworkPayload.newBuilder().setAccountAgeWitness(builder).build();
    }

    protobuf.AccountAgeWitness toProtoAccountAgeWitness() {
        return toProtoMessage().getAccountAgeWitness();
    }

    public static AccountAgeWitness fromProto(protobuf.AccountAgeWitness proto) {
        byte[] hash = proto.getHash().toByteArray();
        if (hash.length != 20) {
            log.warn("We got a a hash which is not 20 bytes");
            hash = new byte[0];
        }
        return new AccountAgeWitness(
                hash,
                proto.getDate());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isDateInTolerance(Clock clock) {
        // We don't allow older or newer than 1 day.
        // Preventing forward dating is also important to protect against a sophisticated attack
        return Math.abs(clock.millis() - date) <= TOLERANCE;
    }

    @Override
    public boolean verifyHashSize() {
        return hash.length == 20;
    }

    @Override
    public byte[] getHash() {
        return hash;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    P2PDataStorage.ByteArray getHashAsByteArray() {
        return new P2PDataStorage.ByteArray(hash);
    }

    @Override
    public String toString() {
        return "AccountAgeWitness{" +
                "\n     hash=" + Utilities.bytesAsHexString(hash) +
                ",\n     date=" + Instant.ofEpochMilli(date) +
                "\n}";
    }
}
