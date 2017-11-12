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

import io.bisq.common.app.Version;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import lombok.Value;

@Value
public class TxVo implements PersistablePayload {
    private final String txVersion;
    private final String id;
    private final int blockHeight;
    private final String blockHash;
    private final long time;

    public TxVo(String id, int blockHeight, String blockHash, long time) {
        this(Version.BSQ_TX_VERSION, id, blockHeight, blockHash, time);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TxVo(String txVersion, String id, int blockHeight, String blockHash, long time) {
        this.txVersion = txVersion;
        this.id = id;
        this.blockHeight = blockHeight;
        this.blockHash = blockHash;
        this.time = time;
    }

    public static TxVo fromProto(PB.TxVo proto) {
        return new TxVo(proto.getTxVersion(),
                proto.getId(),
                proto.getBlockHeight(),
                proto.getBlockHash(),
                proto.getTime());
    }

    public PB.TxVo toProtoMessage() {
        return PB.TxVo.newBuilder()
                .setTxVersion(txVersion)
                .setId(id)
                .setBlockHeight(blockHeight)
                .setBlockHash(blockHash)
                .setTime(time)
                .build();
    }
}
