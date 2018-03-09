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

import io.bisq.core.dao.blockchain.ReadModel;
import io.bisq.core.dao.blockchain.WriteModel;
import io.bisq.core.dao.blockchain.vo.SpentInfo;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxInput;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.Optional;

/**
 * Provide spendable TxOutput and apply state change.
 */

@Slf4j
public class TxInputVerification {

    private final WriteModel writeModel;
    private final ReadModel readModel;

    @Inject
    public TxInputVerification(WriteModel writeModel, ReadModel readModel) {
        this.writeModel = writeModel;
        this.readModel = readModel;
    }

    Optional<TxOutput> getOptionalSpendableTxOutput(TxInput input) {
        // TODO check if Tuple indexes of inputs outputs are not messed up...
        // Get spendable BSQ output for txIdIndexTuple... (get output used as input in tx if it's spendable BSQ)
        return readModel.getSpendableTxOutput(input.getTxIdIndexTuple());
    }

    void applyStateChange(TxInput input, TxOutput spendableTxOutput, int blockHeight, Tx tx, int inputIndex) {
        // The output is BSQ, set it as spent, update bsqBlockChain and add to available BSQ for this tx
        spendableTxOutput.setUnspent(false);
        writeModel.removeUnspentTxOutput(spendableTxOutput);
        spendableTxOutput.setSpentInfo(new SpentInfo(blockHeight, tx.getId(), inputIndex));
        input.setConnectedTxOutput(spendableTxOutput);
    }
}
