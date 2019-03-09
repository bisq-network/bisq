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

import lombok.Value;

@Value
public class DaoStateHash implements PersistablePayload, NetworkPayload {
    private final int blockHeight;
    private final byte[] hash;
    // For first block the prevHash is an empty byte array
    private final byte[] prevHash;

    DaoStateHash(int blockHeight, byte[] hash, byte[] prevHash) {
        this.blockHeight = blockHeight;
        this.hash = hash;
        this.prevHash = prevHash;
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
