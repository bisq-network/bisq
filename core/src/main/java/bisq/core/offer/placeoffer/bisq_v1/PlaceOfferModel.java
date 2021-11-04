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

package bisq.core.offer.placeoffer.bisq_v1;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.filter.FilterManager;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.common.taskrunner.Model;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class PlaceOfferModel implements Model {
    // Immutable
    private final Offer offer;
    private final Coin reservedFundsForOffer;
    private final boolean useSavingsWallet;
    private final BtcWalletService walletService;
    private final TradeWalletService tradeWalletService;
    private final BsqWalletService bsqWalletService;
    private final OfferBookService offerBookService;
    private final ArbitratorManager arbitratorManager;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final DaoFacade daoFacade;
    private final User user;
    @Getter
    private final FilterManager filterManager;

    // Mutable
    @Setter
    private boolean offerAddedToOfferBook;
    @Setter
    private Transaction transaction;

    public PlaceOfferModel(Offer offer,
                           Coin reservedFundsForOffer,
                           boolean useSavingsWallet,
                           BtcWalletService walletService,
                           TradeWalletService tradeWalletService,
                           BsqWalletService bsqWalletService,
                           OfferBookService offerBookService,
                           ArbitratorManager arbitratorManager,
                           TradeStatisticsManager tradeStatisticsManager,
                           DaoFacade daoFacade,
                           User user,
                           FilterManager filterManager) {
        this.offer = offer;
        this.reservedFundsForOffer = reservedFundsForOffer;
        this.useSavingsWallet = useSavingsWallet;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.bsqWalletService = bsqWalletService;
        this.offerBookService = offerBookService;
        this.arbitratorManager = arbitratorManager;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.daoFacade = daoFacade;
        this.user = user;
        this.filterManager = filterManager;
    }

    @Override
    public void onComplete() {
    }
}
