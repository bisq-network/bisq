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

package bisq.desktop.main.market.offerbook;

import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.offer.offerbook.OfferBookListItem;
import bisq.desktop.main.offer.offerbook.OfferBookListItemMaker;
import bisq.desktop.maker.OfferMaker;
import bisq.desktop.maker.PreferenceMakers;

import bisq.core.locale.GlobalSettings;
import bisq.core.offer.OfferDirection;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatistics3;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.util.PriceUtil;

import org.bitcoinj.core.Coin;

import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static bisq.desktop.main.offer.offerbook.OfferBookListItemMaker.btcBuyItem;
import static bisq.desktop.main.offer.offerbook.OfferBookListItemMaker.btcSellItem;
import static bisq.desktop.maker.PreferenceMakers.empty;
import static bisq.desktop.maker.TradeCurrencyMakers.usd;
import static com.natpryce.makeiteasy.MakeItEasy.a;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class OfferBookChartViewModelTest {
    private static final long AVERAGE_BSQ_PRICE = 2000;

    @BeforeEach
    public void setUp() {
        GlobalSettings.setDefaultTradeCurrency(usd);
    }

    @Test
    public void testMaxCharactersForBuyPriceWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, null, empty, null, null, null, null);
        assertEquals(0, model.maxPlacesForBuyPrice.intValue());
    }

    @Test
    public void testMaxCharactersForBuyPriceWithOfflinePriceFeedService() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);


        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        final OfferBookListItem item = make(OfferBookListItemMaker.btcBuyItem.but(with(OfferBookListItemMaker.useMarketBasedPrice, true)));
        item.getOffer().setPriceFeedService(priceFeedService);
        offerBookListItems.addAll(item);

        when(priceFeedService.getMarketPrice(anyString())).thenReturn(null);
        when(priceFeedService.updateCounterProperty()).thenReturn(new SimpleIntegerProperty());
        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, null, empty, priceFeedService, null, null, null);
        model.activate();
        assertEquals(0, model.maxPlacesForBuyPrice.intValue());
    }

    @Test
    public void testMaxCharactersForFiatBuyPrice() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcBuyItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, null, empty, service, null, null, null);
        model.activate();
        assertEquals(7, model.maxPlacesForBuyPrice.intValue());
        offerBookListItems.addAll(make(btcBuyItem.but(with(OfferBookListItemMaker.price, 94016475L))));
        assertEquals(9, model.maxPlacesForBuyPrice.intValue()); // 9401.6475
        offerBookListItems.addAll(make(btcBuyItem.but(with(OfferBookListItemMaker.price, 101016475L))));
        assertEquals(10, model.maxPlacesForBuyPrice.intValue()); //10101.6475
    }

    @Test
    public void testMaxCharactersForBuyVolumeWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, null, empty, null, null, null, null);
        assertEquals(0, model.maxPlacesForBuyVolume.intValue());
    }

    @Test
    public void testMaxCharactersForFiatBuyVolume() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcBuyItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, null, empty, service, null, null, null);
        model.activate();
        assertEquals(1, model.maxPlacesForBuyVolume.intValue()); //0
        offerBookListItems.addAll(make(btcBuyItem.but(with(OfferBookListItemMaker.amount, 100000000L))));
        assertEquals(2, model.maxPlacesForBuyVolume.intValue()); //10
        offerBookListItems.addAll(make(btcBuyItem.but(with(OfferBookListItemMaker.amount, 22128600000L))));
        assertEquals(4, model.maxPlacesForBuyVolume.intValue()); //2213
    }

    @Test
    public void testMaxCharactersForSellPriceWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, null, empty, null, null, null, null);
        assertEquals(0, model.maxPlacesForSellPrice.intValue());
    }

    @Test
    public void testMaxCharactersForSellPriceWithOfflinePriceFeedService() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);


        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        final OfferBookListItem item = make(OfferBookListItemMaker.btcSellItem.but(with(OfferBookListItemMaker.useMarketBasedPrice, true)));
        item.getOffer().setPriceFeedService(priceFeedService);
        offerBookListItems.addAll(item);

        when(priceFeedService.getMarketPrice(anyString())).thenReturn(null);
        when(priceFeedService.updateCounterProperty()).thenReturn(new SimpleIntegerProperty());
        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, null, empty, priceFeedService, null, null, null);
        model.activate();
        assertEquals(0, model.maxPlacesForSellPrice.intValue());
    }

    @Test
    public void testMaxCharactersForFiatSellPrice() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcSellItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, null, empty, service, null, null, null);
        model.activate();
        assertEquals(7, model.maxPlacesForSellPrice.intValue()); // 10.0000 default price
        offerBookListItems.addAll(make(btcSellItem.but(with(OfferBookListItemMaker.price, 94016475L))));
        assertEquals(9, model.maxPlacesForSellPrice.intValue()); // 9401.6475
        offerBookListItems.addAll(make(btcSellItem.but(with(OfferBookListItemMaker.price, 101016475L))));
        assertEquals(10, model.maxPlacesForSellPrice.intValue()); // 10101.6475
    }

    @Test
    public void testMaxCharactersForSellVolumeWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, null, empty, null, null, null, null);
        assertEquals(0, model.maxPlacesForSellVolume.intValue());
    }

    @Test
    public void testMaxCharactersForFiatSellVolume() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcSellItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, null, empty, service, null, null, null);
        model.activate();
        assertEquals(1, model.maxPlacesForSellVolume.intValue()); //0
        offerBookListItems.addAll(make(btcSellItem.but(with(OfferBookListItemMaker.amount, 100000000L))));
        assertEquals(2, model.maxPlacesForSellVolume.intValue()); //10
        offerBookListItems.addAll(make(btcSellItem.but(with(OfferBookListItemMaker.amount, 22128600000L))));
        assertEquals(4, model.maxPlacesForSellVolume.intValue()); //2213
    }

    // As in the BSQ offer book, BSQ offers of others with a price worse for the taker than the price range are left
    // out of the chart and the lists: offers to buy BSQ (direction SELL) below it, offers to sell BSQ (direction BUY)
    // above it.
    @Test
    public void bsqOffersOutsideThePriceRangeAreLeftOut() {
        OfferBook offerBook = mock(OfferBook.class);
        ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        OfferBookListItem ownOffer = bsqItem("own-below-lower-bound", AVERAGE_BSQ_PRICE / 2 - 100,
                OfferDirection.SELL);
        offerBookListItems.addAll(bsqItem("at-lower-bound", AVERAGE_BSQ_PRICE / 2, OfferDirection.SELL),
                bsqItem("below-lower-bound", AVERAGE_BSQ_PRICE / 2 - 1, OfferDirection.SELL),
                ownOffer,
                bsqItem("at-upper-bound", 2 * AVERAGE_BSQ_PRICE, OfferDirection.BUY),
                bsqItem("above-upper-bound", 2 * AVERAGE_BSQ_PRICE + 1, OfferDirection.BUY));
        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);
        OpenOfferManager openOfferManager = mock(OpenOfferManager.class);
        when(openOfferManager.isMyOffer(ownOffer.getOffer())).thenReturn(true);
        // A new instance, as the shared one must keep its currency
        Preferences preferences = make(a(PreferenceMakers.Preferences));
        preferences.setOfferBookChartScreenCurrencyCode("BSQ");

        OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, openOfferManager, preferences,
                mock(PriceFeedService.class), null, null, priceUtil(preferences));
        model.activate();

        // For an altcoin, the offers with the direction SELL are in the buy list
        assertEquals(List.of("at-lower-bound", "own-below-lower-bound"), offerIds(model.getTopBuyOfferList()));
        assertEquals(List.of("at-upper-bound"), offerIds(model.getTopSellOfferList()));
        assertEquals(2, model.getBuyData().size());
        assertEquals(1, model.getSellData().size());

        offerBookListItems.add(bsqItem("arrives-above-upper-bound", 3 * AVERAGE_BSQ_PRICE, OfferDirection.BUY));
        assertEquals(List.of("at-upper-bound"), offerIds(model.getTopSellOfferList()));
        assertFalse(model.allOffersOutsidePriceRange.get());
    }

    @Test
    public void allOffersOutsidePriceRangeOnlyWhenTheRangeHidesEveryOffer() {
        ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList(
                bsqItem("below", AVERAGE_BSQ_PRICE / 3, OfferDirection.SELL),
                bsqItem("above", 3 * AVERAGE_BSQ_PRICE, OfferDirection.BUY));
        OfferBookChartViewModel model = activatedBsqModel(offerBookListItems);

        assertTrue(model.getTopBuyOfferList().isEmpty());
        assertTrue(model.getTopSellOfferList().isEmpty());
        assertTrue(model.allOffersOutsidePriceRange.get());

        // An offer within the range arrives
        OfferBookListItem inRange = bsqItem("in-range", AVERAGE_BSQ_PRICE, OfferDirection.BUY);
        offerBookListItems.add(inRange);
        assertFalse(model.allOffersOutsidePriceRange.get());

        offerBookListItems.remove(inRange);
        assertTrue(model.allOffersOutsidePriceRange.get());

        // Another currency has no price range
        model.onSetTradeCurrency(usd);
        assertFalse(model.allOffersOutsidePriceRange.get());
    }

    // A price better for the taker is never outside the range: an offer to buy BSQ above it and an offer to sell BSQ
    // below it stay in the lists
    @Test
    public void bsqOffersBetterForTheTakerThanThePriceRangeAreShown() {
        OfferBookChartViewModel model = activatedBsqModel(FXCollections.observableArrayList(
                bsqItem("buys-bsq-above", 3 * AVERAGE_BSQ_PRICE, OfferDirection.SELL),
                bsqItem("sells-bsq-below", AVERAGE_BSQ_PRICE / 3, OfferDirection.BUY)));

        assertEquals(List.of("buys-bsq-above"), offerIds(model.getTopBuyOfferList()));
        assertEquals(List.of("sells-bsq-below"), offerIds(model.getTopSellOfferList()));
        assertFalse(model.allOffersOutsidePriceRange.get());
    }

    // An empty BSQ market is not caused by the range
    @Test
    public void noOffersIsNotAllOffersOutsidePriceRange() {
        OfferBookChartViewModel model = activatedBsqModel(FXCollections.observableArrayList());

        assertFalse(model.allOffersOutsidePriceRange.get());
    }

    // The chart leaves out offers without a price, such as an offer with a price relative to the market price while
    // there is no market price. They are not hidden by the range, so the hint must not name the range.
    @Test
    public void offersWithoutPriceDoNotCountAsOutsidePriceRange() {
        OfferBookListItem withoutPrice = new OfferBookListItem(make(OfferMaker.btcUsdOffer.but(
                with(OfferMaker.id, "without-price"),
                with(OfferMaker.baseCurrencyCode, "BSQ"),
                with(OfferMaker.counterCurrencyCode, "BTC"),
                with(OfferMaker.paymentMethodId, "BLOCK_CHAINS"),
                with(OfferMaker.useMarketBasedPrice, true))));
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        withoutPrice.getOffer().setPriceFeedService(priceFeedService);

        OfferBookChartViewModel model = activatedBsqModel(FXCollections.observableArrayList(withoutPrice));

        assertTrue(model.getTopBuyOfferList().isEmpty());
        assertTrue(model.getTopSellOfferList().isEmpty());
        assertFalse(model.allOffersOutsidePriceRange.get());
    }

    // The market view keeps the chart active while another of its tabs is selected
    @Test
    public void offerBookTabIsTheFirstTabOfTheMarketView() {
        OfferBookChartViewModel model = activatedBsqModel(FXCollections.observableArrayList());

        model.setSelectedTabIndex(0);
        assertTrue(model.isOfferBookTabSelected());
        model.setSelectedTabIndex(1);
        assertFalse(model.isOfferBookTabSelected());
    }

    private OfferBookChartViewModel activatedBsqModel(ObservableList<OfferBookListItem> offerBookListItems) {
        OfferBook offerBook = mock(OfferBook.class);
        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);
        // A new instance, as the shared one must keep its currency
        Preferences preferences = make(a(PreferenceMakers.Preferences));
        preferences.setOfferBookChartScreenCurrencyCode("BSQ");

        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        when(priceFeedService.updateCounterProperty()).thenReturn(new SimpleIntegerProperty());

        OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, mock(OpenOfferManager.class),
                preferences, priceFeedService, null, null, priceUtil(preferences));
        model.activate();
        return model;
    }

    @Test
    public void otherCurrenciesDoNotUseTheBsqPriceRange() {
        OfferBook offerBook = mock(OfferBook.class);
        ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(OfferBookListItemMaker.btcBuyItem));
        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);
        PriceUtil priceUtil = mock(PriceUtil.class);

        OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, null, empty,
                mock(PriceFeedService.class), null, null, priceUtil);
        model.activate();

        assertEquals(1, model.getTopBuyOfferList().size());
        verifyNoInteractions(priceUtil);
    }

    private static PriceUtil priceUtil(Preferences preferences) {
        TradeStatistics3 statistic = new TradeStatistics3("BSQ", AVERAGE_BSQ_PRICE, Coin.parseCoin("0.1").getValue(),
                "BLOCK_CHAINS", System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1), null, null,
                (TreeMap<String, String>) null);
        TradeStatisticsManager tradeStatisticsManager = mock(TradeStatisticsManager.class);
        when(tradeStatisticsManager.getNavigableTradeStatisticsSet()).thenReturn(new TreeSet<>(List.of(statistic)));
        return new PriceUtil(mock(PriceFeedService.class), tradeStatisticsManager, preferences);
    }

    private static OfferBookListItem bsqItem(String id, long price, OfferDirection direction) {
        return new OfferBookListItem(make(OfferMaker.btcUsdOffer.but(
                with(OfferMaker.id, id),
                with(OfferMaker.baseCurrencyCode, "BSQ"),
                with(OfferMaker.counterCurrencyCode, "BTC"),
                with(OfferMaker.paymentMethodId, "BLOCK_CHAINS"),
                with(OfferMaker.direction, direction),
                with(OfferMaker.price, price))));
    }

    private static List<String> offerIds(List<OfferListItem> items) {
        return items.stream().map(item -> item.offer.getId()).collect(Collectors.toList());
    }
}
