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

package io.bisq.core.btc.wallet;

import io.bisq.core.dao.blockchain.TxOutputMap;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.TransactionOutput;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches our address of our address.
 */
@Slf4j
class BsqCoinSelector extends BisqDefaultCoinSelector {
    private TxOutputMap txOutputMap;

    public BsqCoinSelector(boolean permitForeignPendingTx) {
        super(permitForeignPendingTx);
    }

    public void setTxoMap(TxOutputMap txOutputMap) {
        this.txOutputMap = txOutputMap;
    }

    @Override
    protected boolean isTxOutputSpendable(TransactionOutput output) {
        return output.getParentTransaction() != null &&
                txOutputMap.isTxOutputUnSpent(output.getParentTransaction().getHashAsString(), output.getIndex());
    }
}
