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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.user.Preferences;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Transaction;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import javax.annotation.Nullable;


@Singleton
public class TransactionListItemFactory {
    private final BtcWalletService btcWalletService;
    private final BsqWalletService bsqWalletService;
    private final DaoFacade daoFacade;
    private final CoinFormatter formatter;
    private final Preferences preferences;

    @Inject
    TransactionListItemFactory(BtcWalletService btcWalletService,
                               BsqWalletService bsqWalletService,
                               DaoFacade daoFacade,
                               @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter formatter,
                               Preferences preferences) {
        this.btcWalletService = btcWalletService;
        this.bsqWalletService = bsqWalletService;
        this.daoFacade = daoFacade;
        this.formatter = formatter;
        this.preferences = preferences;
    }

    TransactionsListItem create(Transaction transaction, @Nullable TransactionAwareTradable tradable) {
        return new TransactionsListItem(transaction,
                btcWalletService,
                bsqWalletService,
                tradable,
                daoFacade,
                formatter,
                preferences.getIgnoreDustThreshold());
    }
}
