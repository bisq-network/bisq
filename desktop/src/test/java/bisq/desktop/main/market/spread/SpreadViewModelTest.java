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

package bisq.desktop.main.market.spread;


import bisq.desktop.main.offer.offerbook.OfferBook;
import bisq.desktop.main.offer.offerbook.OfferBookListItem;
import bisq.desktop.main.offer.offerbook.OfferBookListItemMaker;

import bisq.core.provider.price.PriceFeedService;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.coin.ImmutableCoinFormatter;

import bisq.common.config.Config;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import org.junit.Test;

import static bisq.desktop.main.offer.offerbook.OfferBookListItemMaker.btcBuyItem;
import static bisq.desktop.main.offer.offerbook.OfferBookListItemMaker.btcSellItem;
import static bisq.desktop.main.offer.offerbook.OfferBookListItemMaker.id;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpreadViewModelTest {

    private final CoinFormatter coinFormatter = new ImmutableCoinFormatter(Config.baseCurrencyNetworkParameters().getMonetaryFormat());

    @Test
    public void testMaxCharactersForAmountWithNoOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        SpreadViewModel model = new SpreadViewModel(offerBook, null, coinFormatter);
        assertEquals(0, model.maxPlacesForAmount.intValue());
    }

    @Test
    public void testMaxCharactersForAmount() {
        OfferBook offerBook = mock(OfferBook.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(btcBuyItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        SpreadViewModel model = new SpreadViewModel(offerBook, null, coinFormatter);
        model.activate();
        assertEquals(6, model.maxPlacesForAmount.intValue()); // 0.001
        offerBookListItems.addAll(make(btcBuyItem.but(with(OfferBookListItemMaker.amount, 1403000000L))));
        assertEquals(7, model.maxPlacesForAmount.intValue()); //14.0300
    }

    @Test
    public void testFilterSpreadItemsForUniqueOffers() {
        OfferBook offerBook = mock(OfferBook.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
        offerBookListItems.addAll(make(btcBuyItem));

        when(offerBook.getOfferBookListItems()).thenReturn(offerBookListItems);

        SpreadViewModel model = new SpreadViewModel(offerBook, priceFeedService, coinFormatter);
        model.activate();

        assertEquals(1, model.spreadItems.get(0).numberOfOffers);

        offerBookListItems.addAll(make(btcBuyItem.but(with(id, "2345"))),
                make(btcBuyItem.but(with(id, "2345"))),
                make(btcSellItem.but(with(id, "3456"))),
                make(btcSellItem.but(with(id, "3456"))));

        assertEquals(2, model.spreadItems.get(0).numberOfBuyOffers);
        assertEquals(1, model.spreadItems.get(0).numberOfSellOffers);
        assertEquals(3, model.spreadItems.get(0).numberOfOffers);
    }
}
