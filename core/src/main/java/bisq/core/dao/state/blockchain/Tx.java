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

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;

/**
 * Immutable class for a Bsq transaction.
 * Gets persisted.
 */
@EqualsAndHashCode(callSuper = true)
@Value
public final class Tx extends BaseTx implements PersistablePayload {
    // Created after parsing of a tx is completed. We store only the immutable tx in the block.
    public static Tx fromTempTx(TempTx tempTx) {
        ImmutableList<TxOutput> txOutputs = ImmutableList.copyOf(tempTx.getTempTxOutputs().stream()
                .map(TxOutput::fromTempOutput)
                .collect(Collectors.toList()));

        return new Tx(tempTx.getTxVersion(),
                tempTx.getId(),
                tempTx.getBlockHeight(),
                tempTx.getBlockHash(),
                tempTx.getTime(),
                tempTx.getTxInputs(),
                txOutputs,
                tempTx.getTxType(),
                tempTx.getBurntFee());
    }

    private final ImmutableList<TxOutput> txOutputs;
    @Nullable
    private final TxType txType;
    private final long burntFee;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Tx(String txVersion,
               String id,
               int blockHeight,
               String blockHash,
               long time,
               ImmutableList<TxInput> txInputs,
               ImmutableList<TxOutput> txOutputs,
               @Nullable TxType txType,
               long burntFee) {
        super(txVersion,
                id,
                blockHeight,
                blockHash,
                time,
                txInputs);
        this.txOutputs = txOutputs;
        this.txType = txType;
        this.burntFee = burntFee;

    }

    @Override
    public PB.BaseTx toProtoMessage() {
        final PB.Tx.Builder builder = PB.Tx.newBuilder()
                .addAllTxOutputs(txOutputs.stream()
                        .map(TxOutput::toProtoMessage)
                        .collect(Collectors.toList()))
                .setBurntFee(burntFee);
        Optional.ofNullable(txType).ifPresent(txType -> builder.setTxType(txType.toProtoMessage()));
        return getBaseTxBuilder().setTx(builder).build();
    }

    public static Tx fromProto(PB.BaseTx protoBaseTx) {
        ImmutableList<TxInput> txInputs = protoBaseTx.getTxInputsList().isEmpty() ?
                ImmutableList.copyOf(new ArrayList<>()) :
                ImmutableList.copyOf(protoBaseTx.getTxInputsList().stream()
                        .map(TxInput::fromProto)
                        .collect(Collectors.toList()));
        PB.Tx protoTx = protoBaseTx.getTx();
        ImmutableList<TxOutput> outputs = protoTx.getTxOutputsList().isEmpty() ?
                ImmutableList.copyOf(new ArrayList<>()) :
                ImmutableList.copyOf(protoTx.getTxOutputsList().stream()
                        .map(TxOutput::fromProto)
                        .collect(Collectors.toList()));
        return new Tx(protoBaseTx.getTxVersion(),
                protoBaseTx.getId(),
                protoBaseTx.getBlockHeight(),
                protoBaseTx.getBlockHash(),
                protoBaseTx.getTime(),
                txInputs,
                outputs,
                TxType.fromProto(protoTx.getTxType()),
                protoTx.getBurntFee());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxOutput getLastTxOutput() {
        return txOutputs.get(txOutputs.size() - 1);
    }


    /**
     * The lockTime is stored in the first output of the LOCKUP tx.
     */
    public int getLockTime() {
        return txOutputs.get(0).getLockTime();
    }

    /**
     * The unlockBlockHeight is stored in the first output of the LOCKUP tx.
     */
    public int getUnlockBlockHeight() {
        return txOutputs.get(0).getUnlockBlockHeight();
    }

    @Override
    public String toString() {
        return "Tx{" +
                "\n     txOutputs=" + txOutputs +
                ",\n     txType=" + txType +
                ",\n     burntFee=" + burntFee +
                "\n} " + super.toString();
    }

}
