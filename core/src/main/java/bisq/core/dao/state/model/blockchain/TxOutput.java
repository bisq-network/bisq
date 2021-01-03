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

package bisq.core.dao.state.model.blockchain;

import bisq.core.dao.node.parser.TempTxOutput;
import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.persistable.PersistablePayload;

import java.util.Objects;

import lombok.Getter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Contains immutable BSQ specific data (TxOutputType) and is used to get
 * stored in the list of immutable Transactions (Tx) in a block.
 * TempTxOutput get converted to immutable TxOutput after tx parsing is completed.
 * Gets persisted.
 */
@Immutable
@Getter
public class TxOutput extends BaseTxOutput implements PersistablePayload, ImmutableDaoStateModel {
    public static TxOutput fromTempOutput(TempTxOutput tempTxOutput) {
        return new TxOutput(tempTxOutput.getIndex(),
                tempTxOutput.getValue(),
                tempTxOutput.getTxId(),
                tempTxOutput.getPubKeyScript(),
                tempTxOutput.getAddress(),
                tempTxOutput.getOpReturnData(),
                tempTxOutput.getBlockHeight(),
                tempTxOutput.getTxOutputType(),
                tempTxOutput.getLockTime(),
                tempTxOutput.getUnlockBlockHeight());
    }

    private final TxOutputType txOutputType;

    // The lockTime is stored in the first output of the LOCKUP tx.
    // If not set it is -1, 0 is a valid value.
    private final int lockTime;
    // The unlockBlockHeight is stored in the first output of the UNLOCK tx.
    private final int unlockBlockHeight;

    private TxOutput(int index,
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public protobuf.BaseTxOutput toProtoMessage() {
        protobuf.TxOutput.Builder builder = protobuf.TxOutput.newBuilder()
                .setTxOutputType(txOutputType.toProtoMessage())
                .setLockTime(lockTime)
                .setUnlockBlockHeight(unlockBlockHeight);
        return getRawTxOutputBuilder().setTxOutput(builder).build();
    }

    public static TxOutput fromProto(protobuf.BaseTxOutput proto) {
        protobuf.TxOutput protoTxOutput = proto.getTxOutput();
        return new TxOutput(proto.getIndex(),
                proto.getValue(),
                proto.getTxId(),
                proto.hasPubKeyScript() ? PubKeyScript.fromProto(proto.getPubKeyScript()) : null,
                proto.getAddress().isEmpty() ? null : proto.getAddress(),
                proto.getOpReturnData().isEmpty() ? null : proto.getOpReturnData().toByteArray(),
                proto.getBlockHeight(),
                TxOutputType.fromProto(protoTxOutput.getTxOutputType()),
                protoTxOutput.getLockTime(),
                protoTxOutput.getUnlockBlockHeight());
    }


    @Override
    public String toString() {
        return "TxOutput{" +
                "\n     txOutputType=" + txOutputType +
                "\n     lockTime=" + lockTime +
                ",\n     unlockBlockHeight=" + unlockBlockHeight +
                "\n} " + super.toString();
    }

    // Enums must not be used directly for hashCode or equals as it delivers the Object.hashCode (internal address)!
    // The equals and hashCode methods cannot be overwritten in Enums.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TxOutput)) return false;
        if (!super.equals(o)) return false;
        TxOutput txOutput = (TxOutput) o;
        return lockTime == txOutput.lockTime &&
                unlockBlockHeight == txOutput.unlockBlockHeight &&
                txOutputType.name().equals(txOutput.txOutputType.name());
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), txOutputType.name(), lockTime, unlockBlockHeight);
    }
}
