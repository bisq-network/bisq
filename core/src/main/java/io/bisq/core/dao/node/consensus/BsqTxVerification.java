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
import io.bisq.core.dao.blockchain.vo.TxType;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

/**
 * Verifies if a given transaction is a BSQ transaction.
 */
@Slf4j
public class BsqTxVerification {

    private final TxInputsVerification txInputsVerification;
    private final TxOutputsVerification txOutputsVerification;

    @Inject
    public BsqTxVerification(TxInputsVerification txInputsVerification,
                             TxOutputsVerification txOutputsVerification) {
        this.txInputsVerification = txInputsVerification;
        this.txOutputsVerification = txOutputsVerification;
    }

    public boolean isBsqTx(int blockHeight, Tx tx) {
        BsqInputBalance bsqInputBalance = txInputsVerification.getBsqInputBalance(tx, blockHeight);

        final boolean bsqInputBalancePositive = bsqInputBalance.isPositive();
        if (bsqInputBalancePositive) {
            txInputsVerification.applyStateChange(tx);
            txOutputsVerification.iterate(tx, blockHeight, bsqInputBalance);
        }

        // Lets check if we have left over BSQ (burned fees)
        if (bsqInputBalance.isPositive()) {
            log.debug("BSQ have been left which was not spent. Burned BSQ amount={}, tx={}", bsqInputBalance.getValue(), tx.toString());
            tx.setBurntFee(bsqInputBalance.getValue());

            // Fees are used for all OP_RETURN transactions and for PAY_TRADE_FEE.
            // The TxType for a TRANSFER_BSQ will get overwritten if the tx has an OP_RETURN.
            // If it was not overwritten (still is TRANSFER_BSQ) we change the TxType to PAY_TRADE_FEE.
            if (tx.getTxType().equals(TxType.TRANSFER_BSQ))
                tx.setTxType(TxType.PAY_TRADE_FEE);
        }

        // Any tx with BSQ input is a BSQ tx (except genesis tx but that not handled in that class).
        return bsqInputBalancePositive;
    }

    @Getter
    @Setter
    static class BsqInputBalance {
        // Remaining BSQ from inputs
        private long value = 0;

        BsqInputBalance() {
        }

        public void add(long value) {
            this.value += value;
        }

        public void subtract(long value) {
            this.value -= value;
        }

        public boolean isPositive() {
            return value > 0;
        }

        public boolean isZero() {
            return value == 0;
        }
    }
}
