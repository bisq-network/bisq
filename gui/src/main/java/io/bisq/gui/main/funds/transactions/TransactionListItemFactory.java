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

package io.bisq.gui.main.funds.transactions;

import io.bisq.core.btc.wallet.BsqWalletService;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.blockchain.parse.BsqBlockChain;
import io.bisq.core.trade.Tradable;
import io.bisq.gui.util.BSFormatter;
import org.bitcoinj.core.Transaction;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Optional;

public class TransactionListItemFactory {
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final BsqBlockChain bsqBlockChain;
    private final BSFormatter formatter;

    @Inject
    TransactionListItemFactory(BtcWalletService btcWalletService, BsqWalletService bsqWalletService,
                               BsqBlockChain bsqBlockChain, BSFormatter formatter) {
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.bsqBlockChain = bsqBlockChain;
        this.formatter = formatter;
    }

    TransactionsListItem create(Transaction transaction, @Nullable TransactionAwareTradable tradable) {
        Optional<Tradable> maybeTradable = Optional.ofNullable(tradable)
                .map(TransactionAwareTradable::asTradable);

        return new TransactionsListItem(transaction, btcWalletService, bsqWalletService, maybeTradable, bsqBlockChain, formatter);
    }
}
