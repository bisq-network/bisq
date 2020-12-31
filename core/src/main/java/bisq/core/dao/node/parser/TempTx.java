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

package bisq.core.dao.node.parser;

import bisq.core.dao.node.full.RawTx;
import bisq.core.dao.state.model.blockchain.BaseTx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxType;

import com.google.common.collect.ImmutableList;

import java.util.Objects;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

/**
 * Used only temporary during the transaction parsing process to support mutable data while parsing.
 * After parsing it will get cloned to the immutable Tx.
 * We don't need to implement the ProtoBuffer methods as it is not persisted or sent over the wire.
 */
@Getter
@Setter
public class TempTx extends BaseTx {
    static TempTx fromRawTx(RawTx rawTx) {
        return new TempTx(rawTx.getTxVersion(),
                rawTx.getId(),
                rawTx.getBlockHeight(),
                rawTx.getBlockHash(),
                rawTx.getTime(),
                rawTx.getTxInputs(),
                ImmutableList.copyOf(rawTx.getRawTxOutputs().stream().map(TempTxOutput::fromRawTxOutput).collect(Collectors.toList())),
                null,
                0);
    }

    private final ImmutableList<TempTxOutput> tempTxOutputs;

    // Mutable data
    @Nullable
    private TxType txType;
    private long burntBsq;

    private TempTx(String txVersion,
                   String id,
                   int blockHeight,
                   String blockHash,
                   long time,
                   ImmutableList<TxInput> txInputs,
                   ImmutableList<TempTxOutput> tempTxOutputs,
                   @Nullable TxType txType,
                   long burntBsq) {
        super(txVersion,
                id,
                blockHeight,
                blockHash,
                time,
                txInputs);
        this.tempTxOutputs = tempTxOutputs;
        this.txType = txType;
        this.burntBsq = burntBsq;
    }

    @Override
    public String toString() {
        return "TempTx{" +
                "\n     txOutputs=" + tempTxOutputs +
                ",\n     txType=" + txType +
                ",\n     burntBsq=" + burntBsq +
                "\n} " + super.toString();
    }

    // Enums must not be used directly for hashCode or equals as it delivers the Object.hashCode (internal address)!
    // The equals and hashCode methods cannot be overwritten in Enums.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TempTx)) return false;
        if (!super.equals(o)) return false;
        TempTx tempTx = (TempTx) o;

        String name = txType != null ? txType.name() : "";
        String name1 = tempTx.txType != null ? tempTx.txType.name() : "";
        boolean isTxTypeEquals = name.equals(name1);
        return burntBsq == tempTx.burntBsq &&
                Objects.equals(tempTxOutputs, tempTx.tempTxOutputs) &&
                isTxTypeEquals;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), tempTxOutputs, txType, burntBsq);
    }
}
