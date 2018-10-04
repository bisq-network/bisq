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

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.annotation.Nullable;

/**
 * Contains mutable BSQ specific data (TxOutputType) and used only during tx parsing.
 * Will get converted to immutable TxOutput after tx parsing is completed.
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class TempTxOutput extends BaseTxOutput {
    public static TempTxOutput fromRawTxOutput(RawTxOutput txOutput) {
        return new TempTxOutput(txOutput.getIndex(),
                txOutput.getValue(),
                txOutput.getTxId(),
                txOutput.getPubKeyScript(),
                txOutput.getAddress(),
                txOutput.getOpReturnData(),
                txOutput.getBlockHeight(),
                TxOutputType.UNDEFINED_OUTPUT,
                -1,
                0);
    }

    private TxOutputType txOutputType;
    // If not set it is -1, 0 is a valid value.
    private int lockTime;
    private int unlockBlockHeight;

    private TempTxOutput(int index,
                         long value,
                         String txId,
                         @Nullable PubKeyScript pubKeyScript,
                         @Nullable String address,
                         @Nullable byte[] opReturnData,
                         int blockHeight,
                         TxOutputType txOutputType,
                         int lockTime,
                         int unlockBlockHeight) {
        super(index,
                value,
                txId,
                pubKeyScript,
                address,
                opReturnData,
                blockHeight);

        this.txOutputType = txOutputType;
        this.lockTime = lockTime;
        this.unlockBlockHeight = unlockBlockHeight;
    }


    @Override
    public String toString() {
        return "TempTxOutput{" +
                "\n     txOutputType=" + txOutputType +
                "\n     lockTime=" + lockTime +
                "\n     unlockBlockHeight=" + unlockBlockHeight +
                "\n} " + super.toString();
    }
}
