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
import lombok.Data;

import javax.annotation.Nullable;
import java.util.Optional;

@Data
public class TxInput implements PersistablePayload {
    private final TxInputVo txInputVo;
    @Nullable
    private TxOutput connectedTxOutput;

    public TxInput(TxInputVo txInputVo) {
        this(txInputVo, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TxInput(TxInputVo txInputVo, @Nullable TxOutput connectedTxOutput) {
        this.txInputVo = txInputVo;
        this.connectedTxOutput = connectedTxOutput;
    }

    public PB.TxInput toProtoMessage() {
        final PB.TxInput.Builder builder = PB.TxInput.newBuilder()
                .setTxInputVo(txInputVo.toProtoMessage());
        Optional.ofNullable(connectedTxOutput).ifPresent(e -> builder.setConnectedTxOutput(e.toProtoMessage()));
        return builder.build();
    }

    public static TxInput fromProto(PB.TxInput proto) {
        return new TxInput(TxInputVo.fromProto(proto.getTxInputVo()),
                proto.hasConnectedTxOutput() ? TxOutput.fromProto(proto.getConnectedTxOutput()) : null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void reset() {
        connectedTxOutput = null;
    }

    @Override
    public String toString() {
        return "TxInput{" +
                "\n     txId=" + getTxId() +
                ",\n     txOutputIndex=" + getTxOutputIndex() +
                ",\n     txOutput='" + connectedTxOutput + '\'' +
                "\n}";
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegates
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getTxId() {
        return txInputVo.getTxId();
    }

    public int getTxOutputIndex() {
        return txInputVo.getTxOutputIndex();
    }

    public TxIdIndexTuple getTxIdIndexTuple() {
        return txInputVo.getTxIdIndexTuple();
    }
}
