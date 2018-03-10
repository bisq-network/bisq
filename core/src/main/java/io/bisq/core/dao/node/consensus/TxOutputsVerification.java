/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.node.consensus;

import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.List;

/**
 * Iterates all outputs and applies the verification for BSQ outputs.
 */

@Slf4j
public class TxOutputsVerification {

    private final TxOutputVerification txOutputVerification;

    @Inject
    public TxOutputsVerification(TxOutputVerification txOutputVerification) {
        this.txOutputVerification = txOutputVerification;
    }

    void iterate(Tx tx, int blockHeight, BsqTxVerification.BsqInputBalance bsqInputBalance) {
        // We use order of output index. An output is a BSQ utxo as long there is enough input value
        final List<TxOutput> outputs = tx.getOutputs();
        MutableState mutableState = new MutableState();
        for (int index = 0; index < outputs.size(); index++) {
            txOutputVerification.verify(tx, outputs.get(index), index, blockHeight, bsqInputBalance, mutableState);
        }
    }

    @Getter
    @Setter
    static class MutableState {
        private TxOutput compRequestIssuanceOutputCandidate;
        private TxOutput bsqOutput;

        MutableState() {
        }
    }
}
