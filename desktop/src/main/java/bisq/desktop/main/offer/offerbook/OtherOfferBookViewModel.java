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
import bisq.desktop.util.GUIUtil;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.api.CoreApi;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.locale.CryptoCurrency;
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

import org.jetbrains.annotations.NotNull;

public class OtherOfferBookViewModel extends OfferBookViewModel {

    public static final TradeCurrency DEFAULT_ALTCOIN = CurrencyUtil.getTradeCurrency("ETH").get();

    @Inject
    public OtherOfferBookViewModel(User user,
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
                                   BsqWalletService bsqWalletService, CoreApi coreApi) {
        super(user, openOfferManager, offerBook, preferences, walletsSetup, p2PService, priceFeedService, closedTradableManager, bsqSwapTradeManager, accountAgeWitnessService, navigation, priceUtil, offerFilterService, btcFormatter, bsqFormatter, bsqWalletService, coreApi);
    }

    @Override
    void saveSelectedCurrencyCodeInPreferences(OfferDirection direction, String code) {
        if (direction == OfferDirection.BUY) {
            preferences.setBuyScreenCryptoCurrencyCode(code);
        } else {
            preferences.setSellScreenCryptoCurrencyCode(code);
        }
    }

    @Override
    protected ObservableList<PaymentMethod> filterPaymentMethods(ObservableList<PaymentMethod> list,
                                                                 TradeCurrency selectedTradeCurrency) {
        return FXCollections.observableArrayList(list.stream().filter(PaymentMethod::isBlockchain).collect(Collectors.toList()));
    }

    @Override
    void fillCurrencies(ObservableList<TradeCurrency> tradeCurrencies,
                        ObservableList<TradeCurrency> allCurrencies) {

        tradeCurrencies.add(new CryptoCurrency(GUIUtil.SHOW_ALL_FLAG, ""));
        tradeCurrencies.addAll(preferences.getCryptoCurrenciesAsObservable().stream()
                .filter(withoutBSQAndTopAltcoin())
                .collect(Collectors.toList()));
        tradeCurrencies.add(new CryptoCurrency(GUIUtil.EDIT_FLAG, ""));

        allCurrencies.add(new CryptoCurrency(GUIUtil.SHOW_ALL_FLAG, ""));
        allCurrencies.addAll(CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                .filter(withoutBSQAndTopAltcoin())
                .collect(Collectors.toList()));
        allCurrencies.add(new CryptoCurrency(GUIUtil.EDIT_FLAG, ""));
    }

    @Override
    Predicate<OfferBookListItem> getCurrencyAndMethodPredicate(OfferDirection direction,
                                                               TradeCurrency selectedTradeCurrency) {
        return offerBookListItem -> {
            Offer offer = offerBookListItem.getOffer();
            // BUY Altcoin is actually SELL Bitcoin
            boolean directionResult = offer.getDirection() == direction;
            boolean currencyResult = CurrencyUtil.isCryptoCurrency(offer.getCurrencyCode()) &&
                    ((showAllTradeCurrenciesProperty.get() &&
                            !offer.getCurrencyCode().equals(TopAltcoinOfferBookViewModel.TOP_ALTCOIN.getCode()) &&
                            !offer.getCurrencyCode().equals(BsqOfferBookViewModel.BSQ.getCode())) ||
                            offer.getCurrencyCode().equals(selectedTradeCurrency.getCode()));
            boolean paymentMethodResult = showAllPaymentMethods ||
                    offer.getPaymentMethod().equals(selectedPaymentMethod);
            boolean notMyOfferOrShowMyOffersActivated = !isMyOffer(offerBookListItem.getOffer()) || preferences.isShowOwnOffersInOfferBook();
            return directionResult && currencyResult && paymentMethodResult && notMyOfferOrShowMyOffersActivated;
        };
    }

    @Override
    TradeCurrency getDefaultTradeCurrency() {
        // select first currency in list, otherwise if view is not initialized add Ether as default one
        return !getTradeCurrencies().isEmpty() ? getTradeCurrencies().get(1) :
                DEFAULT_ALTCOIN;
    }

    @Override
    String getCurrencyCodeFromPreferences(OfferDirection direction) {
        return direction == OfferDirection.BUY ? preferences.getBuyScreenCryptoCurrencyCode() : preferences.getSellScreenCryptoCurrencyCode();
    }

    @NotNull
    private Predicate<CryptoCurrency> withoutBSQAndTopAltcoin() {
        return cryptoCurrency ->
                !cryptoCurrency.equals(BsqOfferBookViewModel.BSQ) &&
                        !cryptoCurrency.equals(TopAltcoinOfferBookViewModel.TOP_ALTCOIN);
    }
}
