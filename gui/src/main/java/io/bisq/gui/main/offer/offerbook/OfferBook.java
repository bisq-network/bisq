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

package io.bisq.gui.main.offer.offerbook;

import io.bisq.common.app.Log;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.OfferBookService;
import io.bisq.core.trade.TradeManager;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Holds and manages the unsorted and unfiltered offerbook list of both buy and sell offers.
 * It is handled as singleton by Guice and is used by 2 instances of OfferBookDataModel (one for Buy one for Sell).
 * As it is used only by the Buy and Sell UIs we treat it as local UI model.
 * It also use OfferRepository.Listener as the lists items class and we don't want to get any dependency out of the
 * package for that.
 */
@Slf4j
public class OfferBook {
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
                if (!isOfferWithIdInList(offer)) {
                    offerBookListItems.add(offerBookListItem);
                    Log.logIfStressTests("OfferPayload added: No. of offers = " + offerBookListItems.size());
                }
            }

            @Override
            public void onRemoved(Offer offer) {
                // Update state in case that that offer is used in the take offer screen, so it gets updated correctly
                offer.setState(Offer.State.REMOVED);

                // clean up possible references in openOfferManager
                tradeManager.onOfferRemovedFromRemoteOfferBook(offer);
                Optional<OfferBookListItem> candidate = offerBookListItems.stream()
                        .filter(item -> item.getOffer().getId().equals(offer.getId()))
                        .findAny();
                if (candidate.isPresent()) {
                    OfferBookListItem item = candidate.get();
                    if (offerBookListItems.contains(item)) {
                        offerBookListItems.remove(item);
                        Log.logIfStressTests("OfferPayload removed: No. of offers = " + offerBookListItems.size());
                    }
                }
            }
        });
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isOfferWithIdInList(Offer offer) {
        return offerBookListItems.stream().filter(o -> o.getOffer().getId().equals(offer.getId())).findAny().isPresent();
    }

    public ObservableList<OfferBookListItem> getOfferBookListItems() {
        return offerBookListItems;
    }

    public void fillOfferBookListItems() {
        log.debug("fillOfferBookListItems");

        try {
            // setAll causes sometimes an UnsupportedOperationException
            // Investigate why....
            offerBookListItems.clear();
            offerBookListItems.addAll(offerBookService.getOffers().stream()
                    .map(OfferBookListItem::new)
                    .collect(Collectors.toList()));

            Log.logIfStressTests("OfferPayload filled: No. of offers = " + offerBookListItems.size());

            log.debug("offerBookListItems.size " + offerBookListItems.size());
        } catch (Throwable t) {
            t.printStackTrace();
            log.error("Error at fillOfferBookListItems: " + t.toString());
        }
    }
}
