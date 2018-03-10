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

import io.bisq.core.dao.blockchain.WritableBsqBlockChain;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxInput;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.Optional;

/**
 * Calculate the available BSQ balance from all inputs and apply state change.
 */
@Slf4j
public class TxInputsController {

    private final WritableBsqBlockChain writableBsqBlockChain;
    private final TxInputController txInputController;

    @Inject
    public TxInputsController(WritableBsqBlockChain writableBsqBlockChain, TxInputController txInputController) {
        this.writableBsqBlockChain = writableBsqBlockChain;
        this.txInputController = txInputController;
    }

    BsqTxController.BsqInputBalance getBsqInputBalance(Tx tx, int blockHeight) {
        BsqTxController.BsqInputBalance bsqInputBalance = new BsqTxController.BsqInputBalance();
        for (int inputIndex = 0; inputIndex < tx.getInputs().size(); inputIndex++) {
            TxInput input = tx.getInputs().get(inputIndex);
            final Optional<TxOutput> optionalSpendableTxOutput = txInputController.getOptionalSpendableTxOutput(input);
            if (optionalSpendableTxOutput.isPresent()) {
                bsqInputBalance.add(optionalSpendableTxOutput.get().getValue());
                txInputController.applyStateChange(input, optionalSpendableTxOutput.get(), blockHeight, tx, inputIndex);
            }
        }
        return bsqInputBalance;
    }

    void applyStateChange(Tx tx) {
        writableBsqBlockChain.addTxToMap(tx);
    }
}
