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
import lombok.Data;
import lombok.experimental.Delegate;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Data
public class Tx implements PersistablePayload, Serializable {
    private static final long serialVersionUID = 1;

    @Delegate
    private final TxVo txVo;

    private final List<TxInput> inputs;
    private final List<TxOutput> outputs;

    private long burntFee;
    private TxType txType;

    public Tx(TxVo txVo, List<TxInput> inputs, List<TxOutput> outputs) {
        this.txVo = txVo;
        this.inputs = inputs;
        this.outputs = outputs;
    }

    public Optional<TxOutput> getTxOutput(int index) {
        return outputs.size() > index ? Optional.of(outputs.get(index)) : Optional.<TxOutput>empty();
    }

    public void reset() {
        burntFee = 0;
        txType = null;
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
}
