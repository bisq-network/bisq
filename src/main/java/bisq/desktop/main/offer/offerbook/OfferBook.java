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

import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.trade.TradeManager;

import bisq.common.app.Log;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.offer.OfferPayload.Direction.BUY;

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
    private final Map<String, Integer> buyOfferCountMap = new HashMap<>();
    private final Map<String, Integer> sellOfferCountMap = new HashMap<>();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OfferBook(OfferBookService offerBookService, TradeManager tradeManager) {
        this.offerBookService = offerBookService;

        offerBookService.addOfferBookChangedListener(new OfferBookService.OfferBookChangedListener() {
            @Override
            public void onAdded(Offer offer) {
                // We get onAdded called every time a new ProtectedStorageEntry is received.
                // Mostly it is the same OfferPayload but the ProtectedStorageEntry is different.
                // We filter here to only add new offers if the same offer (using equals) was not already added.
                boolean hasSameOffer = offerBookListItems.stream()
                        .anyMatch(item -> item.getOffer().equals(offer));
                if (!hasSameOffer) {
                    OfferBookListItem offerBookListItem = new OfferBookListItem(offer);
                    // We don't use the contains method as the equals method in Offer takes state and errorMessage into account.
                    // If we have an offer with same ID we remove it and add the new offer as it might have a changed state.
                    Optional<OfferBookListItem> candidateWithSameId = offerBookListItems.stream()
                            .filter(item -> item.getOffer().getId().equals(offer.getId()))
                            .findAny();
                    if (candidateWithSameId.isPresent()) {
                        log.warn("We had an old offer in the list with the same Offer ID. Might be that the state or errorMessage was different. " +
                                "old offerBookListItem={}, new offerBookListItem={}", candidateWithSameId.get(), offerBookListItem);
                        offerBookListItems.remove(candidateWithSameId.get());
                    }

                    offerBookListItems.add(offerBookListItem);
                    Log.logIfStressTests("OfferPayload added: No. of offers = " + offerBookListItems.size());
                } else {
                    log.debug("We have the exact same offer already in our list and ignore the onAdded call. ID={}", offer.getId());
                }
            }

            @Override
            public void onRemoved(Offer offer) {
                // Update state in case that that offer is used in the take offer screen, so it gets updated correctly
                offer.setState(Offer.State.REMOVED);

                // clean up possible references in openOfferManager
                tradeManager.onOfferRemovedFromRemoteOfferBook(offer);
                // We don't use the contains method as the equals method in Offer takes state and errorMessage into account.
                Optional<OfferBookListItem> candidateToRemove = offerBookListItems.stream()
                        .filter(item -> item.getOffer().getId().equals(offer.getId()))
                        .findAny();
                if (candidateToRemove.isPresent()) {
                    offerBookListItems.remove(candidateToRemove.get());
                    Log.logIfStressTests("OfferPayload removed: No. of offers = " + offerBookListItems.size());
                }
            }
        });
    }

    public ObservableList<OfferBookListItem> getOfferBookListItems() {
        return offerBookListItems;
    }

    public void fillOfferBookListItems() {
        try {
            // setAll causes sometimes an UnsupportedOperationException
            // Investigate why....
            offerBookListItems.clear();
            offerBookListItems.addAll(offerBookService.getOffers().stream()
                    .map(OfferBookListItem::new)
                    .collect(Collectors.toList()));

            Log.logIfStressTests("OfferPayload filled: No. of offers = " + offerBookListItems.size());

            log.debug("offerBookListItems.size " + offerBookListItems.size());
            fillOfferCountMaps();
        } catch (Throwable t) {
            t.printStackTrace();
            log.error("Error at fillOfferBookListItems: " + t.toString());
        }
    }

    public Map<String, Integer> getBuyOfferCountMap() {
        return buyOfferCountMap;
    }

    public Map<String, Integer> getSellOfferCountMap() {
        return sellOfferCountMap;
    }

    private void fillOfferCountMaps() {
        buyOfferCountMap.clear();
        sellOfferCountMap.clear();
        final String[] ccyCode = new String[1];
        final int[] offerCount = new int[1];
        offerBookListItems.forEach(o -> {
            ccyCode[0] = o.getOffer().getCurrencyCode();
            if (o.getOffer().getDirection() == BUY) {
                offerCount[0] = buyOfferCountMap.getOrDefault(ccyCode[0], 0) + 1;
                buyOfferCountMap.put(ccyCode[0], offerCount[0]);
            } else {
                offerCount[0] = sellOfferCountMap.getOrDefault(ccyCode[0], 0) + 1;
                sellOfferCountMap.put(ccyCode[0], offerCount[0]);
            }
        });
        log.debug("buyOfferCountMap.size {}   sellOfferCountMap.size {}",
                buyOfferCountMap.size(), sellOfferCountMap.size());
    }
}
