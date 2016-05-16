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

import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.offer.OfferBookService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArraySet;

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

        offerBookService.addOfferBookChangedListener(new OfferBookService.OfferBookChangedListener() {
            @Override
            public void onAdded(Offer offer) {
                OfferBookListItem offerBookListItem = new OfferBookListItem(offer);
                if (!offerBookListItems.contains(offerBookListItem))
                    offerBookListItems.add(offerBookListItem);
            }

            @Override
            public void onRemoved(Offer offer) {
                // Update state in case that that offer is used in the take offer screen, so it gets updated correctly
                offer.setState(Offer.State.REMOVED);

                // clean up possible references in openOfferManager 
                tradeManager.onOfferRemovedFromRemoteOfferBook(offer);

                Optional<OfferBookListItem> candidate = offerBookListItems.stream().filter(item -> item.getOffer().getId().equals(offer.getId())).findAny();
                if (candidate.isPresent()) {
                    try {
                        OfferBookListItem item = candidate.get();
                        if (offerBookListItems.contains(item))
                            offerBookListItems.remove(item);
                    } catch (Throwable t) {
                        log.error(" offerBookListItems.remove failed " + t.getMessage());
                    }
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
        CopyOnWriteArraySet<OfferBookListItem> list = new CopyOnWriteArraySet<>();
        offers.stream().forEach(e -> list.add(new OfferBookListItem(e)));
        try {
            offerBookListItems.clear();
        } catch (Throwable t) {
            log.error(" offerBookListItems.clear failed " + t.getMessage());
        }
       
        offerBookListItems.addAll(list);

        log.debug("offerBookListItems " + offerBookListItems.size());
    }
}
