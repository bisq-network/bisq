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

package bisq.core.offer.placeoffer.atomic;

import bisq.core.dao.DaoFacade;
import bisq.core.filter.FilterManager;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.User;

import bisq.common.taskrunner.Model;

import org.bitcoinj.core.Transaction;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class AtomicPlaceOfferModel implements Model {
    // Immutable
    private final Offer offer;
    private final OfferBookService offerBookService;
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

    public AtomicPlaceOfferModel(Offer offer,
                                 OfferBookService offerBookService,
                                 TradeStatisticsManager tradeStatisticsManager,
                                 DaoFacade daoFacade,
                                 User user,
                                 FilterManager filterManager) {
        this.offer = offer;
        this.offerBookService = offerBookService;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.daoFacade = daoFacade;
        this.user = user;
        this.filterManager = filterManager;
    }

    @Override
    public void onComplete() {
    }
}
