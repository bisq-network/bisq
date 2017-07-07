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
import lombok.Value;

@Value
public class TxInputVo implements PersistablePayload {
    private final String txId;
    private final int txOutputIndex;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.TxInputVo toProtoMessage() {
        return PB.TxInputVo.newBuilder()
                .setTxId(txId)
                .setTxOutputIndex(txOutputIndex)
                .build();
    }

    public static TxInputVo fromProto(PB.TxInputVo proto) {
        return new TxInputVo(proto.getTxId(),
                proto.getTxOutputIndex());
    }

    public TxIdIndexTuple getTxIdIndexTuple() {
        return new TxIdIndexTuple(txId, txOutputIndex);
    }
}
