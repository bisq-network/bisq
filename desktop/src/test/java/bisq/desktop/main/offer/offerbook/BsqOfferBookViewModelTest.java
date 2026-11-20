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

import bisq.desktop.maker.OfferMaker;
import bisq.desktop.maker.PreferenceMakers;

import bisq.core.dao.state.DaoStateService;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferFilterService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.PaymentAccount;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.PriceUtil;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.ImmutableCoinFormatter;

import bisq.common.config.Config;

import org.bitcoinj.core.Coin;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static bisq.desktop.maker.TradeCurrencyMakers.usd;
import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BsqOfferBookViewModelTest {
    private static final long AVERAGE_PRICE = 2000;

    private final CoinFormatter coinFormatter = new ImmutableCoinFormatter(Config.baseCurrencyNetworkParameters().getMonetaryFormat());
    private final DaoStateService daoStateService = new DaoStateService(null, null, null);
    private final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
    private final OpenOfferManager openOfferManager = mock(OpenOfferManager.class);
    private Preferences preferences;

    @BeforeEach
    public void setUp() {
        GlobalSettings.setDefaultTradeCurrency(usd);
        Res.setBaseCurrencyCode(usd.getCode());
        Res.setBaseCurrencyName(usd.getName());
        // A new instance, as tests change the factor
        preferences = make(a(PreferenceMakers.Preferences));
    }

    // The maker of an offer with the direction BUY sells BSQ, so a price above the range is worse for the taker, who
    // buys BSQ. A price below the range is better for the taker and shown.
    @Test
    public void offersWhereTheTakerBuysBsqAreHiddenAboveTheRange() {
        offerBookListItems.addAll(item("at-average", AVERAGE_PRICE),
                item("at-upper-bound", 2 * AVERAGE_PRICE),
                item("above-upper-bound", 2 * AVERAGE_PRICE + 1),
                item("below-lower-bound", AVERAGE_PRICE / 2 - 1));

        BsqOfferBookViewModel model = activatedModel(bsqStatisticAt(AVERAGE_PRICE));

        assertEquals(Set.of("at-average", "at-upper-bound", "below-lower-bound"), Set.copyOf(shownOfferIds(model)));
        assertFalse(model.allOffersOutsidePriceRange.get());
    }

    // The maker of an offer with the direction SELL buys BSQ, so a price below the range is worse for the taker, who
    // sells BSQ. A price above the range is better for the taker and shown.
    @Test
    public void offersWhereTheTakerSellsBsqAreHiddenBelowTheRange() {
        offerBookListItems.addAll(item("at-average", AVERAGE_PRICE, OfferDirection.SELL),
                item("at-lower-bound", AVERAGE_PRICE / 2, OfferDirection.SELL),
                item("below-lower-bound", AVERAGE_PRICE / 2 - 1, OfferDirection.SELL),
                item("above-upper-bound", 2 * AVERAGE_PRICE + 1, OfferDirection.SELL));

        BsqOfferBookViewModel model = activatedModel(bsqStatisticAt(AVERAGE_PRICE), OfferDirection.SELL);

        assertEquals(Set.of("at-average", "at-lower-bound", "above-upper-bound"), Set.copyOf(shownOfferIds(model)));
        assertFalse(model.allOffersOutsidePriceRange.get());
    }

    // One invented statistic at a very low price moves the average far below the market. The fair offers where the
    // taker sells BSQ are then far above the range, and an offer of the attacker near the moved average must not be
    // the only one shown.
    @Test
    public void anAverageMovedFarBelowTheMarketDoesNotHideFairOffers() {
        offerBookListItems.addAll(item("fair", 1300, OfferDirection.SELL),
                item("attacker", 50, OfferDirection.SELL));

        BsqOfferBookViewModel model = activatedModel(bsqStatisticAt(29), OfferDirection.SELL);

        assertEquals(Set.of("fair", "attacker"), Set.copyOf(shownOfferIds(model)));
    }

    @Test
    public void rangeUsesTheFactorFromThePreferences() {
        preferences.setBsqOfferPriceRangeFactor(4);
        offerBookListItems.addAll(item("at-upper-bound", 4 * AVERAGE_PRICE),
                item("above-upper-bound", 4 * AVERAGE_PRICE + 1),
                item("at-lower-bound", AVERAGE_PRICE / 4, OfferDirection.SELL),
                item("below-lower-bound", AVERAGE_PRICE / 4 - 1, OfferDirection.SELL));

        assertEquals(List.of("at-upper-bound"), shownOfferIds(activatedModel(bsqStatisticAt(AVERAGE_PRICE))));
        assertEquals(List.of("at-lower-bound"),
                shownOfferIds(activatedModel(bsqStatisticAt(AVERAGE_PRICE), OfferDirection.SELL)));
    }

    // The maker sees their own offers at any price
    @Test
    public void ownOffersAreNotHidden() {
        OfferBookListItem ownOffer = item("own", 10 * AVERAGE_PRICE);
        when(openOfferManager.isMyOffer(ownOffer.getOffer())).thenReturn(true);
        offerBookListItems.addAll(ownOffer, item("other", 10 * AVERAGE_PRICE));

        BsqOfferBookViewModel model = activatedModel(bsqStatisticAt(AVERAGE_PRICE));

        assertEquals(List.of("own"), shownOfferIds(model));
    }

    // Without BSQ trade statistics of the last 90 days there is no average, and all offers are shown
    @Test
    public void allOffersAreShownWithoutAnAverage() {
        offerBookListItems.addAll(item("low", 1), item("high", 1_000_000));
        TradeStatistics3 olderThan90Days = new TradeStatistics3("BSQ", AVERAGE_PRICE, Coin.parseCoin("0.1").getValue(),
                "BLOCK_CHAINS", System.currentTimeMillis() - TimeUnit.DAYS.toMillis(91), null, null,
                (TreeMap<String, String>) null);

        BsqOfferBookViewModel model = activatedModel(olderThan90Days);

        assertEquals(List.of("low", "high"), shownOfferIds(model));
        assertFalse(model.allOffersOutsidePriceRange.get());
    }

    @Test
    public void allOffersOutsidePriceRangeOnlyWhenTheRangeHidesEveryOffer() {
        offerBookListItems.addAll(item("above", 3 * AVERAGE_PRICE), item("far-above", 4 * AVERAGE_PRICE));

        BsqOfferBookViewModel model = activatedModel(bsqStatisticAt(AVERAGE_PRICE));

        assertTrue(shownOfferIds(model).isEmpty());
        assertTrue(model.allOffersOutsidePriceRange.get());

        // An offer within the range arrives
        OfferBookListItem inRange = item("in-range", AVERAGE_PRICE);
        offerBookListItems.add(inRange);
        assertFalse(model.allOffersOutsidePriceRange.get());

        offerBookListItems.remove(inRange);
        assertTrue(model.allOffersOutsidePriceRange.get());

        model.onTabSelected(false);
        assertFalse(model.allOffersOutsidePriceRange.get());
    }

    // An empty offer book is not caused by the range
    @Test
    public void noOffersIsNotAllOffersOutsidePriceRange() {
        BsqOfferBookViewModel model = activatedModel(bsqStatisticAt(AVERAGE_PRICE));

        assertTrue(shownOfferIds(model).isEmpty());
        assertFalse(model.allOffersOutsidePriceRange.get());
    }

    // Offers of the other direction are not in this offer book, so they do not count as hidden by the range
    @Test
    public void offersHiddenByOtherFiltersDoNotCount() {
        offerBookListItems.add(item("other-direction", 3 * AVERAGE_PRICE, OfferDirection.SELL));

        BsqOfferBookViewModel model = activatedModel(bsqStatisticAt(AVERAGE_PRICE));

        assertFalse(model.allOffersOutsidePriceRange.get());
    }

    // With "Show offers matching my accounts" and no BSQ account, that filter hides every offer, not the range. On the
    // first click on the tab, the tab is selected before the model is activated, and only the activation applies that
    // filter. The state after the activation must not report the offers as hidden by the range.
    @Test
    public void offersHiddenByTheMatchingAccountsFilterDoNotCountAfterTheFirstLoad() {
        offerBookListItems.add(item("above", 3 * AVERAGE_PRICE));
        preferences.setShowOffersMatchingMyAccounts(true);
        User user = mock(User.class);
        when(user.getPaymentAccounts()).thenReturn(Set.of(mock(PaymentAccount.class)));
        OfferFilterService offerFilterService = mock(OfferFilterService.class);
        when(offerFilterService.canTakeOffer(any(), eq(false)))
                .thenReturn(OfferFilterService.Result.HAS_NO_PAYMENT_ACCOUNT_VALID_FOR_OFFER);

        BsqOfferBookViewModel model = activatedModel(bsqStatisticAt(AVERAGE_PRICE), user, offerFilterService);

        assertTrue(shownOfferIds(model).isEmpty());
        assertFalse(model.allOffersOutsidePriceRange.get());
    }

    private BsqOfferBookViewModel activatedModel(TradeStatistics3 statistic) {
        return activatedModel(statistic, OfferDirection.BUY);
    }

    private BsqOfferBookViewModel activatedModel(TradeStatistics3 statistic, OfferDirection direction) {
        return activatedModel(statistic, mock(User.class), null, direction);
    }

    private BsqOfferBookViewModel activatedModel(TradeStatistics3 statistic,
                                                 User user,
                                                 OfferFilterService offerFilterService) {
        return activatedModel(statistic, user, offerFilterService, OfferDirection.BUY);
    }

    // In the order of the first click on the tab: the tab is selected before the model is activated
    private BsqOfferBookViewModel activatedModel(TradeStatistics3 statistic,
                                                 User user,
                                                 OfferFilterService offerFilterService,
                                                 OfferDirection direction) {
        OfferBook offerBook = mock(OfferBook.class);
        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        TradeStatisticsManager tradeStatisticsManager = mock(TradeStatisticsManager.class);
        when(tradeStatisticsManager.getNavigableTradeStatisticsSet()).thenReturn(new TreeSet<>(List.of(statistic)));
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        PriceUtil priceUtil = new PriceUtil(priceFeedService, tradeStatisticsManager, preferences);

        BsqOfferBookViewModel model = new BsqOfferBookViewModel(user, openOfferManager, offerBook,
                preferences, null, null, priceFeedService, null, null, null, null, priceUtil, offerFilterService,
                coinFormatter, new BsqFormatter(), null, null, daoStateService);
        model.initWithDirection(direction);
        model.onTabSelected(true);
        model.activate();
        return model;
    }

    private static TradeStatistics3 bsqStatisticAt(long price) {
        return new TradeStatistics3("BSQ", price, Coin.parseCoin("0.1").getValue(), "BLOCK_CHAINS",
                System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1), null, null, (TreeMap<String, String>) null);
    }

    private static OfferBookListItem item(String id, long price) {
        return item(id, price, OfferDirection.BUY);
    }

    private static OfferBookListItem item(String id, long price, OfferDirection direction) {
        return new OfferBookListItem(make(OfferMaker.btcUsdOffer.but(
                with(OfferMaker.id, id),
                with(OfferMaker.baseCurrencyCode, "BSQ"),
                with(OfferMaker.counterCurrencyCode, "BTC"),
                with(OfferMaker.paymentMethodId, "BLOCK_CHAINS"),
                with(OfferMaker.direction, direction),
                with(OfferMaker.price, price))));
    }

    private static List<String> shownOfferIds(BsqOfferBookViewModel model) {
        return model.getOfferList().stream()
                .map(item -> item.getOffer().getId())
                .collect(Collectors.toList());
    }
}
