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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Data
public class Tx implements PersistablePayload {
    private final TxVo txVo;
    private final List<TxInput> inputs;
    private final List<TxOutput> outputs;

    private long burntFee;
    private TxType txType;

    public Tx(TxVo txVo, List<TxInput> inputs, List<TxOutput> outputs) {
        this(txVo, inputs, outputs, 0, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Tx(TxVo txVo, List<TxInput> inputs, List<TxOutput> outputs, long burntFee, @Nullable TxType txType) {
        this.txVo = txVo;
        this.inputs = inputs;
        this.outputs = outputs;
        this.burntFee = burntFee;
        this.txType = txType;
    }

    public PB.Tx toProtoMessage() {
        final PB.Tx.Builder builder = PB.Tx.newBuilder()
                .setTxVo(txVo.toProtoMessage())
                .addAllInputs(inputs.stream()
                        .map(TxInput::toProtoMessage)
                        .collect(Collectors.toList()))
                .addAllOutputs(outputs.stream()
                        .map(TxOutput::toProtoMessage)
                        .collect(Collectors.toList()))
                .setBurntFee(burntFee);

        Optional.ofNullable(txType).ifPresent(e -> builder.setTxType(e.toProtoMessage()));

        return builder.build();
    }

    public static Tx fromProto(PB.Tx proto) {
        return new Tx(TxVo.fromProto(proto.getTxVo()),
                proto.getInputsList().isEmpty() ?
                        new ArrayList<>() :
                        proto.getInputsList().stream()
                                .map(TxInput::fromProto)
                                .collect(Collectors.toList()),
                proto.getOutputsList().isEmpty() ?
                        new ArrayList<>() :
                        proto.getOutputsList().stream()
                                .map(TxOutput::fromProto)
                                .collect(Collectors.toList()),
                proto.getBurntFee(),
                TxType.fromProto(proto.getTxType()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<TxOutput> getTxOutput(int index) {
        return outputs.size() > index ? Optional.of(outputs.get(index)) : Optional.<TxOutput>empty();
    }

    public void reset() {
        burntFee = 0;
        txType = TxType.UNDEFINED_TX_TYPE;
        inputs.stream().forEach(TxInput::reset);
        outputs.stream().forEach(TxOutput::reset);
    }

    @Override
    public String toString() {
        return "Tx{" +
                "\ntxVersion='" + getTxVersion() + '\'' +
                ",\nid='" + getId() + '\'' +
                ",\nblockHeight=" + getBlockHeight() +
                ",\nblockHash=" + getBlockHash() +
                ",\ntime=" + new Date(getTime()) +
                ",\ninputs=" + inputs +
                ",\noutputs=" + outputs +
                ",\nburntFee=" + burntFee +
                ",\ntxType=" + txType +
                "}\n";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegates
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String getTxVersion() {
        return txVo.getTxVersion();
    }

    public String getId() {
        return txVo.getId();
    }

    public int getBlockHeight() {
        return txVo.getBlockHeight();
    }

    public String getBlockHash() {
        return txVo.getBlockHash();
    }

    public long getTime() {
        return txVo.getTime();
    }
}
