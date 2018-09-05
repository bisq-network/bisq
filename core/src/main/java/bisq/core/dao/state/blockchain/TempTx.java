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

package bisq.core.dao.state.blockchain;

import com.google.common.collect.ImmutableList;

import java.util.stream.Collectors;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.annotation.Nullable;

/**
 * Used only only temporary during the transaction parsing process to support mutable data while parsing.
 * After parsing it will get cloned to the immutable Tx.
 * We don't need to implement the ProtoBuffer methods as it is not persisted or sent over the wire.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class TempTx extends BaseTx {
    public static TempTx fromRawTx(RawTx rawTx) {
        return new TempTx(rawTx.getTxVersion(),
                rawTx.getId(),
                rawTx.getBlockHeight(),
                rawTx.getBlockHash(),
                rawTx.getTime(),
                rawTx.getTxInputs(),
                ImmutableList.copyOf(rawTx.getRawTxOutputs().stream().map(TempTxOutput::fromRawTxOutput).collect(Collectors.toList())),
                null,
                0,
                0);
    }

    private final ImmutableList<TempTxOutput> tempTxOutputs;

    // Mutable data
    @Nullable
    private TxType txType;
    private long burntFee;
    // If not set it is -1. LockTime of 0 is a valid value.
    private int unlockBlockHeight;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TempTx(String txVersion,
                   String id,
                   int blockHeight,
                   String blockHash,
                   long time,
                   ImmutableList<TxInput> txInputs,
                   ImmutableList<TempTxOutput> tempTxOutputs,
                   @Nullable TxType txType,
                   long burntFee,
                   int unlockBlockHeight) {
        super(txVersion,
                id,
                blockHeight,
                blockHash,
                time,
                txInputs);
        this.tempTxOutputs = tempTxOutputs;
        this.txType = txType;
        this.burntFee = burntFee;
        this.unlockBlockHeight = unlockBlockHeight;
    }


    @Override
    public String toString() {
        return "TempTx{" +
                "\n     txOutputs=" + tempTxOutputs +
                ",\n     txType=" + txType +
                ",\n     burntFee=" + burntFee +
                ",\n     unlockBlockHeight=" + unlockBlockHeight +
                "\n} " + super.toString();
    }
}
