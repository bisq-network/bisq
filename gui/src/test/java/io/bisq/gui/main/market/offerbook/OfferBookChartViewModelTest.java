package io.bisq.gui.main.market.offerbook;

import io.bisq.common.GlobalSettings;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.gui.main.offer.offerbook.OfferBook;
import io.bisq.gui.main.offer.offerbook.OfferBookListItem;
import io.bisq.gui.main.offer.offerbook.OfferBookListItemMaker;
import io.bisq.gui.util.BSFormatter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static io.bisq.common.locale.TradeCurrencyMakers.usd;
import static io.bisq.core.user.PreferenceMakers.empty;
import static io.bisq.gui.main.offer.offerbook.OfferBookListItemMaker.btcItem;
import static io.bisq.gui.main.offer.offerbook.OfferBookListItemMaker.btcSellItem;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OfferBook.class, PriceFeedService.class})
public class OfferBookChartViewModelTest {

    @Before
    public void setUp() {
        GlobalSettings.setDefaultTradeCurrency(usd);
    }

    @Test
    public void testMaxCharactersForBuyPriceWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, null,null, new BSFormatter());
        assertEquals(0, model.maxPlacesForBuyPrice.intValue());
    }

    @Test
    public void testMaxCharactersForBuyPrice() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(btcItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, service, null, new BSFormatter());
        model.activate();
        assertEquals(7, model.maxPlacesForBuyPrice.intValue());
        offerBookListItems.addAll(make(btcItem.but(with(OfferBookListItemMaker.price,94016475L))));
        assertEquals(9, model.maxPlacesForBuyPrice.intValue());
        offerBookListItems.addAll(make(btcItem.but(with(OfferBookListItemMaker.price,101016475L))));
        assertEquals(10, model.maxPlacesForBuyPrice.intValue());
    }

    @Test
    public void testMaxCharactersForBuyVolumeWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, null,null, new BSFormatter());
        assertEquals(0, model.maxPlacesForBuyVolume.intValue());
    }

    @Test
    public void testMaxCharactersForBuyVolume() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(btcItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, service, null, new BSFormatter());
        model.activate();
        assertEquals(4, model.maxPlacesForBuyVolume.intValue()); //0.01
        offerBookListItems.addAll(make(btcItem.but(with(OfferBookListItemMaker.amount,100000000L))));
        assertEquals(5, model.maxPlacesForBuyVolume.intValue()); //10.00
        offerBookListItems.addAll(make(btcItem.but(with(OfferBookListItemMaker.amount,22128600000L))));
        assertEquals(7, model.maxPlacesForBuyVolume.intValue()); //2212.86
    }

    @Test
    public void testMaxCharactersForSellPriceWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, null,null, new BSFormatter());
        assertEquals(0, model.maxPlacesForSellPrice.intValue());
    }

    @Test
    public void testMaxCharactersForSellPrice() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(btcSellItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, service, null, new BSFormatter());
        model.activate();
        assertEquals(7, model.maxPlacesForSellPrice.intValue());
        offerBookListItems.addAll(make(btcSellItem.but(with(OfferBookListItemMaker.price,94016475L))));
        assertEquals(9, model.maxPlacesForSellPrice.intValue());
        offerBookListItems.addAll(make(btcSellItem.but(with(OfferBookListItemMaker.price,101016475L))));
        assertEquals(10, model.maxPlacesForSellPrice.intValue());
    }

    @Test
    public void testMaxCharactersForSellVolumeWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, null,null, new BSFormatter());
        assertEquals(0, model.maxPlacesForSellVolume.intValue());
    }

    @Test
    public void testMaxCharactersForSellVolume() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(btcSellItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, service, null, new BSFormatter());
        model.activate();
        assertEquals(4, model.maxPlacesForSellVolume.intValue()); //0.01
        offerBookListItems.addAll(make(btcSellItem.but(with(OfferBookListItemMaker.amount,100000000L))));
        assertEquals(5, model.maxPlacesForSellVolume.intValue()); //10.00
        offerBookListItems.addAll(make(btcSellItem.but(with(OfferBookListItemMaker.amount,22128600000L))));
        assertEquals(7, model.maxPlacesForSellVolume.intValue()); //2212.86
    }
}
