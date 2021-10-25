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

package bisq.core.dao.monitoring.model;


import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;

// About 134 693 items with 48 bytes/item =  6.4 MB
@EqualsAndHashCode(callSuper = true)
public final class DaoStateHash extends StateHash {
    public DaoStateHash(int height, byte[] hash) {
        super(height, hash);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.DaoStateHash toProtoMessage() {
        return protobuf.DaoStateHash.newBuilder()
                .setHeight(height)
                .setHash(ByteString.copyFrom(hash))
                .build();
    }

    public static DaoStateHash fromProto(protobuf.DaoStateHash proto) {
        return new DaoStateHash(proto.getHeight(), proto.getHash().toByteArray());
    }
}
