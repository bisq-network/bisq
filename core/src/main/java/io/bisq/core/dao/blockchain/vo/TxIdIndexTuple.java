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
public class TxIdIndexTuple implements PersistablePayload {
    private final String txId;
    private final int index;

    public TxIdIndexTuple(String txId, int index) {
        this.txId = txId;
        this.index = index;
    }

    public TxIdIndexTuple(String string) {
        this(string.split(":")[0], Integer.parseInt(string.split(":")[1]));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.TxIdIndexTuple toProtoMessage() {
        return PB.TxIdIndexTuple.newBuilder()
                .setTxId(txId)
                .setIndex(index)
                .build();
    }

    public static TxIdIndexTuple fromProto(PB.TxIdIndexTuple proto) {
        return new TxIdIndexTuple(proto.getTxId(),
                proto.getIndex());
    }

    public String getAsString() {
        return txId + ":" + index;
    }
}
