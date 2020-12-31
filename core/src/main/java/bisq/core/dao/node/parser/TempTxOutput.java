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

import bisq.core.dao.node.full.RawTxOutput;
import bisq.core.dao.state.model.blockchain.BaseTxOutput;
import bisq.core.dao.state.model.blockchain.PubKeyScript;
import bisq.core.dao.state.model.blockchain.TxOutputType;

import java.util.Objects;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

/**
 * Contains mutable BSQ specific data (TxOutputType) and used only during tx parsing.
 * Will get converted to immutable TxOutput after tx parsing is completed.
 */
@Getter
@Setter
public class TempTxOutput extends BaseTxOutput {
    static TempTxOutput fromRawTxOutput(RawTxOutput txOutput) {
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

    // The lockTime is stored in the first output of the LOCKUP tx.
    // If not set it is -1, 0 is a valid value.
    private int lockTime;
    // The unlockBlockHeight is stored in the first output of the UNLOCK tx.
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

    public boolean isOpReturnOutput() {
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        return getOpReturnData() != null;
    }

    @Override
    public String toString() {
        return "TempTxOutput{" +
                "\n     txOutputType=" + txOutputType +
                "\n     lockTime=" + lockTime +
                "\n     unlockBlockHeight=" + unlockBlockHeight +
                "\n} " + super.toString();
    }

    // Enums must not be used directly for hashCode or equals as it delivers the Object.hashCode (internal address)!
    // The equals and hashCode methods cannot be overwritten in Enums.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TempTxOutput)) return false;
        if (!super.equals(o)) return false;
        TempTxOutput that = (TempTxOutput) o;
        return lockTime == that.lockTime &&
                unlockBlockHeight == that.unlockBlockHeight &&
                txOutputType.name().equals(that.txOutputType.name());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), txOutputType.name(), lockTime, unlockBlockHeight);
    }
}
