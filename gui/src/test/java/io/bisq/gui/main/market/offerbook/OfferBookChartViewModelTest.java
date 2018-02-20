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
    public void testMaxCharactersForPriceWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, null,null, new BSFormatter());
        assertEquals(0, model.maxPlacesForPrice.intValue());
    }

    @Test
    public void testMaxCharactersForPrice() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(btcItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, service, null, new BSFormatter());
        model.activate();
        assertEquals(7, model.maxPlacesForPrice.intValue());
        offerBookListItems.addAll(make(btcItem.but(with(OfferBookListItemMaker.price,94016475L))));
        assertEquals(9, model.maxPlacesForPrice.intValue());
        offerBookListItems.addAll(make(btcItem.but(with(OfferBookListItemMaker.price,101016475L))));
        assertEquals(10, model.maxPlacesForPrice.intValue());
    }

    @Test
    public void testMaxCharactersForVolumeWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, null,null, new BSFormatter());
        assertEquals(0, model.maxPlacesForVolume.intValue());
    }

    @Test
    public void testMaxCharactersForVolume() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService service = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(btcItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final OfferBookChartViewModel model = new OfferBookChartViewModel(offerBook, empty, service, null, new BSFormatter());
        model.activate();
        assertEquals(4, model.maxPlacesForVolume.intValue()); //0.01
        offerBookListItems.addAll(make(btcItem.but(with(OfferBookListItemMaker.amount,100000000L))));
        assertEquals(5, model.maxPlacesForVolume.intValue()); //10.00
        offerBookListItems.addAll(make(btcItem.but(with(OfferBookListItemMaker.amount,22128600000L))));
        assertEquals(7, model.maxPlacesForVolume.intValue()); //2212.86
    }
}
