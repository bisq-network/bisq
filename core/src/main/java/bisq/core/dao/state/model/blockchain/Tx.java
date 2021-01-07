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

import bisq.core.dao.node.parser.TempTx;
import bisq.core.dao.state.model.ImmutableDaoStateModel;

import bisq.common.proto.persistable.PersistablePayload;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.Getter;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable class for a Bsq transaction.
 * Gets persisted.
 */
@Immutable
@Getter
public final class Tx extends BaseTx implements PersistablePayload, ImmutableDaoStateModel {
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
                tempTx.getBurntBsq());
    }

    private final ImmutableList<TxOutput> txOutputs;
    @Nullable
    private final TxType txType;
    // Can be burned fee or in case of an invalid tx the burned BSQ from all BSQ inputs
    private final long burntBsq;


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
               long burntBsq) {
        super(txVersion,
                id,
                blockHeight,
                blockHash,
                time,
                txInputs);
        this.txOutputs = txOutputs;
        this.txType = txType;
        this.burntBsq = burntBsq;

    }

    @Override
    public protobuf.BaseTx toProtoMessage() {
        final protobuf.Tx.Builder builder = protobuf.Tx.newBuilder()
                .addAllTxOutputs(txOutputs.stream()
                        .map(TxOutput::toProtoMessage)
                        .collect(Collectors.toList()))
                .setBurntBsq(burntBsq);
        Optional.ofNullable(txType).ifPresent(txType -> builder.setTxType(txType.toProtoMessage()));
        return getBaseTxBuilder().setTx(builder).build();
    }

    public static Tx fromProto(protobuf.BaseTx protoBaseTx) {
        ImmutableList<TxInput> txInputs = protoBaseTx.getTxInputsList().isEmpty() ?
                ImmutableList.copyOf(new ArrayList<>()) :
                ImmutableList.copyOf(protoBaseTx.getTxInputsList().stream()
                        .map(TxInput::fromProto)
                        .collect(Collectors.toList()));
        protobuf.Tx protoTx = protoBaseTx.getTx();
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
                protoTx.getBurntBsq());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    public TxOutput getLastTxOutput() {
        return txOutputs.get(txOutputs.size() - 1);
    }


    public long getBurntBsq() {
        return burntBsq;
    }

    public long getBurntFee() {
        return txType == TxType.INVALID ? 0 : burntBsq;
    }

    public long getInvalidatedBsq() {
        return txType == TxType.INVALID ? burntBsq : 0;
    }

    public int getLockTime() {
        return getLockupOutput().getLockTime();
    }

    public long getLockedAmount() {
        return getLockupOutput().getValue();
    }

    // The lockTime is stored in the first output of the LOCKUP tx.
    public TxOutput getLockupOutput() {
        return txOutputs.get(0);
    }

    // The unlockBlockHeight is stored in the first output of the UNLOCK tx.
    public int getUnlockBlockHeight() {
        return getLockupOutput().getUnlockBlockHeight();
    }

    @Override
    public String toString() {
        return "Tx{" +
                "\n     txOutputs=" + txOutputs +
                ",\n     txType=" + txType +
                ",\n     burntBsq=" + burntBsq +
                "\n} " + super.toString();
    }

    // Enums must not be used directly for hashCode or equals as it delivers the Object.hashCode (internal address)!
    // The equals and hashCode methods cannot be overwritten in Enums.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Tx)) return false;
        if (!super.equals(o)) return false;
        Tx tx = (Tx) o;

        String name = txType != null ? txType.name() : "";
        String name1 = tx.txType != null ? tx.txType.name() : "";
        boolean isTxTypeEquals = name.equals(name1);

        return burntBsq == tx.burntBsq &&
                Objects.equals(txOutputs, tx.txOutputs) &&
                isTxTypeEquals;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), txOutputs, txType, burntBsq);
    }
}
