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

@Data
public class TxInput implements PersistablePayload, Serializable {
    private static final long serialVersionUID = 1;

    @Delegate
    private final TxInputVo txInputVo;

    private TxOutput connectedTxOutput;

    public TxInput(TxInputVo txInputVo) {
        this.txInputVo = txInputVo;
    }

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
}
