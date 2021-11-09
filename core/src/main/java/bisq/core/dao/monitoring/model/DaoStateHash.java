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
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public final class DaoStateHash extends StateHash {
    // If we have built the hash by ourself opposed to that we got delivered the hash from seed nodes or resources
    private final boolean isSelfCreated;

    public DaoStateHash(int height, byte[] hash, boolean isSelfCreated) {
        super(height, hash);
        this.isSelfCreated = isSelfCreated;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.DaoStateHash toProtoMessage() {
        return protobuf.DaoStateHash.newBuilder()
                .setHeight(height)
                .setHash(ByteString.copyFrom(hash))
                .setIsSelfCreated(isSelfCreated)
                .build();
    }

    public static DaoStateHash fromProto(protobuf.DaoStateHash proto) {
        return new DaoStateHash(proto.getHeight(), proto.getHash().toByteArray(), proto.getIsSelfCreated());
    }

    @Override
    public String toString() {
        return "DaoStateHash{" +
                "\r\n     isSelfCreated=" + isSelfCreated +
                "\r\n} " + super.toString();
    }
}
