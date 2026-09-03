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

import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;
import bisq.core.filter.FilterPolicyService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.provider.price.PriceFeedService;

import bisq.common.UserThread;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OfferBookTest {
    private OfferBookService offerBookService;
    private OfferBook offerBook;
    private OfferBookService.OfferBookChangedListener listener;

    @BeforeEach
    void setUp() {
        UserThread.resetForTests();

        offerBookService = mock(OfferBookService.class);
        FilterManager filterManager = mock(FilterManager.class);
        FilterPolicyService filterPolicyService = mock(FilterPolicyService.class);
        PriceFeedService priceFeedService = mock(PriceFeedService.class);
        when(filterManager.filterProperty()).thenReturn(new SimpleObjectProperty<Filter>());
        when(priceFeedService.updateCounterProperty()).thenReturn(new SimpleIntegerProperty());

        offerBook = new OfferBook(offerBookService, filterManager, filterPolicyService, priceFeedService);
        var listenerCaptor = ArgumentCaptor.forClass(OfferBookService.OfferBookChangedListener.class);
        verify(offerBookService).addOfferBookChangedListener(listenerCaptor.capture());
        listener = listenerCaptor.getValue();
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
    }

    @Test
    void ignoresPresentationUpdatesDuringJvmShutdown() {
        OfferBookListItem existingItem = mock(OfferBookListItem.class);
        Offer addedOffer = mock(Offer.class);
        Offer removedOffer = mock(Offer.class);
        offerBook.getOfferBookListItems().add(existingItem);
        UserThread.executeAtShutdown(() -> {
        });

        listener.onAdded(addedOffer);
        listener.onRemoved(removedOffer);

        assertEquals(1, offerBook.getOfferBookListItems().size());
        assertSame(existingItem, offerBook.getOfferBookListItems().get(0));
        verifyNoInteractions(addedOffer, removedOffer);
    }
}
