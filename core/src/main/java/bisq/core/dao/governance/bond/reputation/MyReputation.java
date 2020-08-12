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

package bisq.core.dao.governance.bond.reputation;

import bisq.core.dao.governance.bond.BondedAsset;

import bisq.common.crypto.Hash;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * MyReputation is persisted locally and carries the private salt data. In contrast to Reputation which is the public
 * data everyone can derive from the blockchain data (hash in opReturn).
 */
@Immutable
@Value
@Slf4j
public final class MyReputation implements PersistablePayload, NetworkPayload, BondedAsset {
    // Uid is needed to be sure that 2 objects with the same salt are kept separate.
    private final String uid;
    private final byte[] salt;
    private final transient byte[] hash; // Not persisted as it is derived from salt. Stored for caching purpose only.

    public MyReputation(byte[] salt) {
        this(UUID.randomUUID().toString(), salt);
        checkArgument(salt.length <= 20, "salt must not be longer then 20 bytes");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private MyReputation(String uid, byte[] salt) {
        this.uid = uid;
        this.salt = salt;
        this.hash = Hash.getSha256Ripemd160hash(salt);
    }

    @Override
    public protobuf.MyReputation toProtoMessage() {
        return protobuf.MyReputation.newBuilder()
                .setUid(uid)
                .setSalt(ByteString.copyFrom(salt))
                .build();
    }

    public static MyReputation fromProto(protobuf.MyReputation proto) {
        return new MyReputation(proto.getUid(), proto.getSalt().toByteArray());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BondedAsset implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public byte[] getHash() {
        return hash;
    }

    @Override
    public String getDisplayString() {
        return Utilities.bytesAsHexString(hash);
    }

    @Override
    public String getUid() {
        return Utilities.bytesAsHexString(hash);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MyReputation)) return false;
        if (!super.equals(o)) return false;
        MyReputation that = (MyReputation) o;
        return Objects.equals(uid, that.uid) &&
                Arrays.equals(salt, that.salt);
    }

    @Override
    public int hashCode() {

        int result = Objects.hash(super.hashCode(), uid);
        result = 31 * result + Arrays.hashCode(salt);
        return result;
    }
///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public String toString() {
        return "MyReputation{" +
                "\n     uid=" + uid +
                "\n     salt=" + Utilities.bytesAsHexString(salt) +
                "\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n}";
    }
}
