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

package io.bisq.core.dao.blockchain.vo;

import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import lombok.Getter;
import lombok.Value;

@Value
@Getter
public class BsqBlockVo implements PersistablePayload {
    private final int height;
    private final String hash;
    private final String previousBlockHash;

    public BsqBlockVo(int height, String hash, String previousBlockHash) {
        this.height = height;
        this.hash = hash;
        this.previousBlockHash = previousBlockHash;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////


    public PB.BsqBlockVo toProtoMessage() {
        return PB.BsqBlockVo.newBuilder()
                .setHeight(height)
                .setHash(hash)
                .setPreviousBlockHash(previousBlockHash).build();
    }

    public static BsqBlockVo fromProto(PB.BsqBlockVo proto) {
        return new BsqBlockVo(proto.getHeight(),
                proto.getHash(),
                proto.getPreviousBlockHash());
    }
}
