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

package bisq.desktop.main.offer.offerbook;

import bisq.desktop.Navigation;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.api.CoreApi;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferFilterService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.FormattingUtils;
import bisq.core.util.PriceUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import com.google.inject.Inject;

import javax.inject.Named;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TopAltcoinOfferBookViewModel extends OfferBookViewModel {

    public static final TradeCurrency TOP_ALTCOIN = CurrencyUtil.getTradeCurrency("XMR").get();

    @Inject
    public TopAltcoinOfferBookViewModel(User user,
                                        OpenOfferManager openOfferManager,
                                        OfferBook offerBook,
                                        Preferences preferences,
                                        WalletsSetup walletsSetup,
                                        P2PService p2PService,
                                        PriceFeedService priceFeedService,
                                        ClosedTradableManager closedTradableManager,
                                        BsqSwapTradeManager bsqSwapTradeManager,
                                        AccountAgeWitnessService accountAgeWitnessService,
                                        Navigation navigation,
                                        PriceUtil priceUtil,
                                        OfferFilterService offerFilterService,
                                        @Named(FormattingUtils.BTC_FORMATTER_KEY) CoinFormatter btcFormatter,
                                        BsqFormatter bsqFormatter,
                                        BsqWalletService bsqWalletService,
                                        CoreApi coreApi) {
        super(user, openOfferManager, offerBook, preferences, walletsSetup, p2PService, priceFeedService, closedTradableManager, bsqSwapTradeManager, accountAgeWitnessService, navigation, priceUtil, offerFilterService, btcFormatter, bsqFormatter, bsqWalletService, coreApi);
    }

    @Override
    void saveSelectedCurrencyCodeInPreferences(OfferDirection direction, String code) {
        // No need to store anything as it is just one Altcoin offers anyway
    }

    @Override
    protected ObservableList<PaymentMethod> filterPaymentMethods(ObservableList<PaymentMethod> list,
                                                                 TradeCurrency selectedTradeCurrency) {
        return FXCollections.observableArrayList(list.stream().filter(PaymentMethod::isBlockchain).collect(Collectors.toList()));
    }

    @Override
    void fillCurrencies(ObservableList<TradeCurrency> tradeCurrencies,
                        ObservableList<TradeCurrency> allCurrencies) {
        tradeCurrencies.add(TOP_ALTCOIN);
        allCurrencies.add(TOP_ALTCOIN);
    }

    @Override
    Predicate<OfferBookListItem> getCurrencyAndMethodPredicate(OfferDirection direction,
                                                               TradeCurrency selectedTradeCurrency) {
        return offerBookListItem -> {
            Offer offer = offerBookListItem.getOffer();
            // BUY Altcoin is actually SELL Bitcoin
            boolean directionResult = offer.getDirection() == direction;
            boolean currencyResult = offer.getCurrencyCode().equals(TOP_ALTCOIN.getCode());
            boolean paymentMethodResult = showAllPaymentMethods ||
                    offer.getPaymentMethod().equals(selectedPaymentMethod);
            boolean notMyOfferOrShowMyOffersActivated = !isMyOffer(offerBookListItem.getOffer()) || preferences.isShowOwnOffersInOfferBook();
            return directionResult && currencyResult && paymentMethodResult && notMyOfferOrShowMyOffersActivated;
        };
    }

    @Override
    TradeCurrency getDefaultTradeCurrency() {
        return TOP_ALTCOIN;
    }

    @Override
    String getCurrencyCodeFromPreferences(OfferDirection direction) {
        return TOP_ALTCOIN.getCode();
    }
}
