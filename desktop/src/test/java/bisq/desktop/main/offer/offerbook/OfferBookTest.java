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

import bisq.core.filter.Filter;
import bisq.core.filter.FilterManager;
import bisq.core.filter.FilterPolicyService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.provider.price.PriceFeedService;

import bisq.network.p2p.NodeAddress;

import bisq.common.UserThread;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import static com.natpryce.makeiteasy.MakeItEasy.make;
import static com.natpryce.makeiteasy.MakeItEasy.with;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OfferBookTest {
    private static final String OFFER_ID = "offer-id";
    // Outside of regtest the offer book shows only offers of makers with a Tor v3 address
    private static final NodeAddress MAKER_NODE_ADDRESS = new NodeAddress("a".repeat(56) + ".onion", 9999);

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
    void offerOfTheSameOwnerReplacesListedOfferWithTheSameId() {
        PubKeyRing owner = pubKeyRing();
        Offer listedOffer = offer(OFFER_ID, owner, 100_000);
        Offer editedOffer = offer(OFFER_ID, owner, 200_000);

        listener.onAdded(listedOffer);
        listener.onAdded(editedOffer);

        assertEquals(List.of(editedOffer), offersInBook());
    }

    @Test
    void offerOfAnotherOwnerDoesNotReplaceListedOfferWithTheSameId() {
        Offer listedOffer = offer(OFFER_ID, pubKeyRing(), 100_000);
        Offer offerWithCopiedId = offer(OFFER_ID, pubKeyRing(), 200_000);

        listener.onAdded(listedOffer);
        listener.onAdded(offerWithCopiedId);

        assertEquals(List.of(listedOffer, offerWithCopiedId), offersInBook());
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

    private List<Offer> offersInBook() {
        return offerBook.getOfferBookListItems().stream()
                .map(OfferBookListItem::getOffer)
                .collect(Collectors.toList());
    }

    private static Offer offer(String offerId, PubKeyRing owner, long amount) {
        return make(OfferMaker.btcUsdOffer.but(
                with(OfferMaker.id, offerId),
                with(OfferMaker.pubKeyRing, owner),
                with(OfferMaker.amount, amount),
                with(OfferMaker.minAmount, amount),
                with(OfferMaker.nodeAddress, MAKER_NODE_ADDRESS)));
    }

    private static PubKeyRing pubKeyRing() {
        return new PubKeyRing(Sig.generateKeyPair().getPublic(), Encryption.generateKeyPair().getPublic());
    }
}
