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

package io.bisq.core.btc.wallet;

import io.bisq.core.dao.blockchain.parse.BsqBlockChain;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.TransactionOutput;

import javax.inject.Inject;

/**
 * We use a specialized version of the CoinSelector based on the DefaultCoinSelector implementation.
 * We lookup for spendable outputs which matches our address of our address.
 */
@Slf4j
public class BsqCoinSelector extends BisqDefaultCoinSelector {
    private final BsqBlockChain bsqBlockChain;

    @Inject
    public BsqCoinSelector(BsqBlockChain bsqBlockChain) {
        super(true);
        this.bsqBlockChain = bsqBlockChain;
    }

    @Override
    protected boolean isTxOutputSpendable(TransactionOutput output) {
        // output.getParentTransaction() cannot be null as it is checked in calling method
        return output.getParentTransaction() != null &&
                bsqBlockChain.isTxOutputSpendable(output.getParentTransaction().getHashAsString(), output.getIndex());
    }
}
