package bisq.desktop.main.market.spread;


import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.offer.offerbook.OfferBookListItem;
import bisq.desktop.main.offer.offerbook.OfferBookListItemMaker;
import bisq.desktop.util.BSFormatter;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static bisq.desktop.main.offer.offerbook.OfferBookListItemMaker.btcItem;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(OfferBook.class)
public class SpreadViewModelTest {

    @Test
    public void testMaxCharactersForAmountWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final SpreadViewModel model = new SpreadViewModel(offerBook, null, new BSFormatter());
        assertEquals(0, model.maxPlacesForAmount.intValue());
    }

    @Test
    public void testMaxCharactersForAmount() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(btcItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        final SpreadViewModel model = new SpreadViewModel(offerBook, null, new BSFormatter());
        model.activate();
        assertEquals(6, model.maxPlacesForAmount.intValue()); // 0.001
        offerBookListItems.addAll(make(btcItem.but(with(OfferBookListItemMaker.amount,1403000000L))));
        assertEquals(7, model.maxPlacesForAmount.intValue()); //14.0300
    }
}
