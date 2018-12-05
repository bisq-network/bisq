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

package bisq.core.btc.wallet;

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxOutputKey;

import org.bitcoinj.core.TransactionOutput;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches our address of our address.
 */
@Slf4j
public class BsqCoinSelector extends BisqDefaultCoinSelector {
    private DaoStateService daoStateService;

    @Inject
    public BsqCoinSelector(DaoStateService daoStateService) {
        super(true);
        this.daoStateService = daoStateService;
    }

    @Override
    protected boolean isTxOutputSpendable(TransactionOutput output) {
        // output.getParentTransaction() cannot be null as it is checked in calling method
        return output.getParentTransaction() != null &&
                daoStateService.isTxOutputSpendable(new TxOutputKey(output.getParentTransaction().getHashAsString(), output.getIndex()));
    }
}
