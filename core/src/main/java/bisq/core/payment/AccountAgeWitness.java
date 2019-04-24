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

package bisq.core.payment;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.DateTolerantPayload;
import bisq.network.p2p.storage.payload.LazyProcessedPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

// Object has 28 raw bytes (33 bytes is size of ProtoBuffer object in storage list, 5 byte extra for list -> totalBytes = 5 + n*33)
// With 1 000 000 entries we get about 33 MB of data. Old entries will be shipped with the resource file,
// so only the newly added objects since the last release will be retrieved over the P2P network.
@Slf4j
@Value
public class AccountAgeWitness implements LazyProcessedPayload, PersistableNetworkPayload, PersistableEnvelope, DateTolerantPayload, CapabilityRequiringPayload {
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
    public boolean isDateInTolerance() {
        // We don't allow older or newer then 1 day.
        // Preventing forward dating is also important to protect against a sophisticated attack
        return Math.abs(new Date().getTime() - date) <= TOLERANCE;
    }

    @Override
    public boolean verifyHashSize() {
        return hash.length == 20;
    }

    // Pre 0.6 version don't know the new message type and throw an error which leads to disconnecting the peer.
    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.ACCOUNT_AGE_WITNESS);
    }

    @Override
    public byte[] getHash() {
        return hash;
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
