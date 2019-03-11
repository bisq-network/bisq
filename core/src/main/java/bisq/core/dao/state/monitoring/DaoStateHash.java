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

package bisq.core.dao.state.monitoring;

import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import java.util.Arrays;

import lombok.Value;

/**
 * Contains the blockHeight, the hash and the previous hash of the dao state.
 * As the hash is created from the dao state at the particular height including the previous hash we get the history of
 * the full chain included and we know if the hash matches at a particular height that all the past blocks need to match
 * as well.
 */
@Value
public class DaoStateHash implements PersistablePayload, NetworkPayload {
    private final int blockHeight;
    // Hash includes prev hash as well as dao state hash. With adding prev hash we can ensure the all the history is
    // matching if our hash matches.
    private final byte[] hash;
    // For first block the prevHash is an empty byte array
    private final byte[] prevHash;

    DaoStateHash(int blockHeight, byte[] hash, byte[] prevHash) {
        this.blockHeight = blockHeight;
        this.hash = hash;
        this.prevHash = prevHash;
    }

    public boolean hasEqualHash(DaoStateHash other) {
        return Arrays.equals(hash, other.getHash());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.DaoStateHash toProtoMessage() {
        final PB.DaoStateHash.Builder builder = PB.DaoStateHash.newBuilder()
                .setBlockHeight(blockHeight)
                .setHash(ByteString.copyFrom(hash))
                .setPrevHash(ByteString.copyFrom(prevHash));
        return builder.build();
    }

    public static DaoStateHash fromProto(PB.DaoStateHash proto) {
        return new DaoStateHash(proto.getBlockHeight(),
                proto.getHash().toByteArray(),
                proto.getPrevHash().toByteArray());
    }

    @Override
    public String toString() {
        return "DaoStateHash{" +
                "\n     blockHeight=" + blockHeight +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                ",\n     prevHash=" + Utilities.bytesAsHexString(prevHash) +
                "\n}";
    }
}
