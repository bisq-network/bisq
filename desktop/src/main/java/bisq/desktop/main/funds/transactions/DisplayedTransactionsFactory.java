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

package bisq.desktop.main.funds.transactions;

import bisq.core.btc.wallet.BtcWalletService;

import javax.inject.Inject;

public class DisplayedTransactionsFactory {
    private final BtcWalletService btcWalletService;
    private final TradableRepository tradableRepository;
    private final TransactionListItemFactory transactionListItemFactory;
    private final TransactionAwareTradableFactory transactionAwareTradableFactory;

    @Inject
    DisplayedTransactionsFactory(BtcWalletService btcWalletService, TradableRepository tradableRepository,
                                 TransactionListItemFactory transactionListItemFactory,
                                 TransactionAwareTradableFactory transactionAwareTradableFactory) {
        this.btcWalletService = btcWalletService;
        this.tradableRepository = tradableRepository;
        this.transactionListItemFactory = transactionListItemFactory;
        this.transactionAwareTradableFactory = transactionAwareTradableFactory;
    }

    DisplayedTransactions create() {
        return new DisplayedTransactions(btcWalletService, tradableRepository, transactionListItemFactory,
                transactionAwareTradableFactory);
    }
}
