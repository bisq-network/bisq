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

package bisq.desktop.main.portfolio.duplicateoffer;


import bisq.desktop.Navigation;
import bisq.desktop.main.offer.MutableOfferDataModel;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.CreateOfferService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import com.google.inject.Inject;

import javax.inject.Named;

class DuplicateOfferDataModel extends MutableOfferDataModel {

    @Inject
    DuplicateOfferDataModel(CreateOfferService createOfferService,
                       OpenOfferManager openOfferManager,
                       OfferUtil offerUtil,
                       BtcWalletService btcWalletService,
                       BsqWalletService bsqWalletService,
                       Preferences preferences,
                       User user,
                       P2PService p2PService,
                       PriceFeedService priceFeedService,
                       AccountAgeWitnessService accountAgeWitnessService,
                       FeeService feeService,
                       @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                       TradeStatisticsManager tradeStatisticsManager,
                       Navigation navigation) {

        super(createOfferService,
                openOfferManager,
                offerUtil,
                btcWalletService,
                bsqWalletService,
                preferences,
                user,
                p2PService,
                priceFeedService,
                accountAgeWitnessService,
                feeService,
                btcFormatter,
                tradeStatisticsManager,
                navigation);
    }

    public void populateData(Offer offer) {
        if (offer == null)
            return;
        paymentAccount = user.getPaymentAccount(offer.getMakerPaymentAccountId());
        setMinAmount(offer.getMinAmount());
        setAmount(offer.getAmount());
        setPrice(offer.getPrice());
        setVolume(offer.getVolume());
        setUseMarketBasedPrice(offer.isUseMarketBasedPrice());
        if (offer.isUseMarketBasedPrice()) {
            setMarketPriceMargin(offer.getMarketPriceMargin());
        }
    }
}
