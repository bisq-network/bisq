/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main.offer.offerbook;

import io.bitsquare.p2p.storage.HashMapChangedListener;
import io.bitsquare.p2p.storage.data.ProtectedData;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OfferBookService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.Serializable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds and manages the unsorted and unfiltered offerbook list of both buy and sell offers.
 * It is handled as singleton by Guice and is used by 2 instances of OfferBookDataModel (one for Buy one for Sell).
 * As it is used only by the Buy and Sell UIs we treat it as local UI model.
 * It also use OfferRepository.Listener as the lists items class and we don't want to get any dependency out of the
 * package for that.
 */
public class OfferBook {
    private static final Logger log = LoggerFactory.getLogger(OfferBook.class);

    private final OfferBookService offerBookService;
    private final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OfferBook(OfferBookService offerBookService, TradeManager tradeManager) {
        this.offerBookService = offerBookService;
        offerBookService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedData entry) {
                log.debug("onAdded " + entry);
                Serializable data = entry.expirablePayload;
                if (data instanceof Offer) {
                    Offer offer = (Offer) data;
                    OfferBookListItem offerBookListItem = new OfferBookListItem(offer);
                    if (!offerBookListItems.contains(offerBookListItem))
                        offerBookListItems.add(offerBookListItem);
                }
            }

            @Override
            public void onRemoved(ProtectedData entry) {
                log.debug("onRemoved " + entry);
                if (entry.expirablePayload instanceof Offer) {
                    Offer offer = (Offer) entry.expirablePayload;

                    // Update state in case that that offer is used in the take offer screen, so it gets updated correctly
                    offer.setState(Offer.State.REMOVED);

                    // clean up possible references in openOfferManager 
                    tradeManager.onOfferRemovedFromRemoteOfferBook(offer);

                    offerBookListItems.removeIf(item -> item.getOffer().getId().equals(offer.getId()));
                }
            }
        });
    }

    public ObservableList<OfferBookListItem> getOfferBookListItems() {
        return offerBookListItems;
    }

    public void fillOfferBookListItems() {
        log.debug("fillOfferBookListItems");
        List<Offer> offers = offerBookService.getOffers();
        CopyOnWriteArrayList<OfferBookListItem> list = new CopyOnWriteArrayList<>();
        offers.stream().forEach(e -> list.add(new OfferBookListItem(e)));
        offerBookListItems.clear();
        offerBookListItems.addAll(list);

        log.debug("offerBookListItems " + offerBookListItems.size());
    }
}
