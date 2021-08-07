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

import bisq.core.filter.FilterManager;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OfferRestrictions;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.utils.Utils;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.offer.OfferPayload.Direction.BUY;

/**
 * Holds and manages the unsorted and unfiltered offerbook list (except for banned offers) of both buy and sell offers.
 * It is handled as singleton by Guice and is used by 2 instances of OfferBookDataModel (one for Buy one for Sell).
 * As it is used only by the Buy and Sell UIs we treat it as local UI model.
 * It also use OfferRepository.Listener as the lists items class and we don't want to get any dependency out of the
 * package for that.
 */
@Singleton
@Slf4j
public class OfferBook {
    private final OfferBookService offerBookService;
    private final ObservableList<OfferBookListItem> offerBookListItems = FXCollections.observableArrayList();
    private final Map<String, Integer> buyOfferCountMap = new HashMap<>();  // TODO what is this for?
    private final Map<String, Integer> sellOfferCountMap = new HashMap<>(); // TODO what is this for?
    private final FilterManager filterManager;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    OfferBook(OfferBookService offerBookService, FilterManager filterManager) {
        this.offerBookService = offerBookService;
        this.filterManager = filterManager;

        offerBookService.addOfferBookChangedListener(new OfferBookService.OfferBookChangedListener() {
            @Override
            public void onAdded(Offer offer, P2PDataStorage.ByteArray hashOfPayload, int sequenceNumber) {
                printOfferBookListItems("Before onAdded");
                // We get onAdded called every time a new ProtectedStorageEntry is received.
                // Mostly it is the same OfferPayload but the ProtectedStorageEntry is different.
                // We filter here to only add new offers if the same offer (using equals) was not already added and it
                // is not banned.

                if (filterManager.isOfferIdBanned(offer.getId())) {
                    log.debug("Ignored banned offer. ID={}", offer.getId());
                    return;
                }

                if (OfferRestrictions.requiresNodeAddressUpdate() && !Utils.isV3Address(offer.getMakerNodeAddress().getHostName())) {
                    log.debug("Ignored offer with Tor v2 node address. ID={}", offer.getId());
                    return;
                }

                // Use offer.equals(offer) to see if the OfferBook list contains an exact
                // match -- offer.equals(offer) includes comparisons of payload, state
                // and errorMessage.
                boolean hasSameOffer = offerBookListItems.stream().anyMatch(item -> item.getOffer().equals(offer));
                if (!hasSameOffer) {
                    OfferBookListItem newOfferBookListItem = new OfferBookListItem(offer, hashOfPayload, sequenceNumber);
                    removeAnyOldOfferBookListItemsBeforeAddingReplacement(newOfferBookListItem);
                    offerBookListItems.add(newOfferBookListItem);  // Add replacement.
                    if (log.isDebugEnabled()) {
                        log.debug("onAdded: Added new offer {}\n"
                                        + "\t\tonAdded.seqNo {}  newItem.seqNo: {}  newItem.payloadHash: {}",
                                offer.getId(),
                                sequenceNumber,
                                newOfferBookListItem.getSequenceNumber(),
                                newOfferBookListItem.hashOfPayload == null ? "null" : newOfferBookListItem.hashOfPayload.getHex());
                    }
                } else {
                    log.debug("We have the exact same offer already in our list and ignore the onAdded call. ID={}", offer.getId());
                }
                printOfferBookListItems("After onAdded");
            }

            @Override
            public void onRemoved(Offer offer, P2PDataStorage.ByteArray hashOfPayload, int sequenceNumber) {
                printOfferBookListItems("Before onRemoved");
                removeOffer(offer, hashOfPayload, sequenceNumber);
                printOfferBookListItems("After onRemoved");
            }
        });
    }

    private void removeAnyOldOfferBookListItemsBeforeAddingReplacement(OfferBookListItem newOfferBookListItem) {
        String offerId = newOfferBookListItem.getOffer().getId();
        List<OfferBookListItem> offerItemsWithSameIdAndDifferentHash = offerBookListItems.stream()
                .filter(item -> item.getOffer().getId().equals(offerId) && (
                        item.hashOfPayload == null
                                || !item.hashOfPayload.equals(newOfferBookListItem.hashOfPayload))
                )
                .collect(Collectors.toList());
        if (offerItemsWithSameIdAndDifferentHash.size() > 0) {
            offerItemsWithSameIdAndDifferentHash.forEach(oldOfferItem -> {
                offerBookListItems.remove(oldOfferItem);
                if (log.isDebugEnabled()) {
                    log.debug("onAdded: Removed old offer {} from list\n"
                                    + "\told.seqNo = {} old.payloadHash  = {}\n"
                                    + "\tThis may make a subsequent onRemoved( {} ) call redundant.",
                            offerId,
                            oldOfferItem.getSequenceNumber(),
                            oldOfferItem.getHashOfPayload() == null ? "null" : oldOfferItem.getHashOfPayload().getHex(),
                            oldOfferItem.getOffer().getId());
                }
            });
        }
    }

    public void removeOffer(Offer offer, P2PDataStorage.ByteArray hashOfPayload, int sequenceNumber) {
        // Update state in case that that offer is used in the take offer screen, so it gets updated correctly
        offer.setState(Offer.State.REMOVED);
        offer.cancelAvailabilityRequest();

        if (log.isDebugEnabled()) {
            log.debug("onRemoved: id={} \n\tpayload-hash={} \n\tseq-no={}",
                    offer.getId(),
                    hashOfPayload.getHex(),
                    sequenceNumber);
        }

        // Find the removal candidate in the OfferBook list with matching offer-id and payload-hash.
        Optional<OfferBookListItem> candidateToRemove = offerBookListItems.stream()
                .filter(item -> item.getOffer().getId().equals(offer.getId()) && (
                        item.hashOfPayload == null
                                || item.hashOfPayload.equals(hashOfPayload))
                )
                .findAny();

        if (!candidateToRemove.isPresent()) {
            // Candidate is not in list, print reason and return.
            if (log.isDebugEnabled()) {
                log.debug("List does not contain offer with id {} and payload-hash {}",
                        offer.getId(),
                        hashOfPayload.getHex());
            }
            return;
        }

        OfferBookListItem candidate = candidateToRemove.get();

        // Remove the candidate only if the storage sequenceNumber has increased, and the candidate's
        // storage payload hash matches the onRemoved hashOfPayload parameter.  We may receive add/remove
        // messages out of order (from api 'editoffer'), so we use the sequenceNumber and payload hash to
        // ensure we do not remove an edited offer immediately after it was added.

        if (candidate.getSequenceNumber() >= sequenceNumber) {
            // Candidate's seq-no is not < onRemoved.seq-no, print reason for not removing candidate and return.
            if (log.isDebugEnabled()) {
                log.debug("Candidate.seqNo: {} < onRemoved.seqNo: {} ?"
                                + " No, old offer not removed",
                        candidate.getSequenceNumber(),
                        sequenceNumber);
            }
            return;
        }

        // The seq-no test for removal has passed;  ensure the candidate is removed
        // only if its payload hash matches the method arg (payload hash.).
        if (log.isDebugEnabled()) {
            // Print the seq-no test has passed.
            log.debug("Candidate.seqNo: {} < onRemoved.seqNo: {} ? Yes",
                    candidate.getSequenceNumber(),
                    sequenceNumber);
        }

        if ((candidate.getHashOfPayload() == null || candidate.getHashOfPayload().equals(hashOfPayload))) {
            // The payload-hash test passed, remove the candidate and print reason.
            offerBookListItems.remove(candidate);

            if (log.isDebugEnabled()) {
                log.debug("Candidate.payload-hash: {} is null or == onRemoved.payload-hash: {} ?"
                                + " Yes, removed old offer",
                        candidate.hashOfPayload == null ? "null" : candidate.hashOfPayload.getHex(),
                        hashOfPayload == null ? "null" : hashOfPayload.getHex());
            }
        } else {
            if (log.isDebugEnabled()) {
                // Candidate's payload-hash test failed:  payload-hash != onRemoved.payload-hash.
                // Print reason for not removing candidate.
                log.debug("Candidate.payload-hash: {} == onRemoved.payload-hash: {} ?"
                                + " No, old offer not removed",
                        candidate.hashOfPayload == null ? "null" : candidate.hashOfPayload.getHex(),
                        hashOfPayload == null ? "null" : hashOfPayload.getHex());
            }
        }
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
                    .filter(o -> !filterManager.isOfferIdBanned(o.getId()))
                    .filter(o -> !OfferRestrictions.requiresNodeAddressUpdate() || Utils.isV3Address(o.getMakerNodeAddress().getHostName()))
                    .map(OfferBookListItem::new)
                    .collect(Collectors.toList()));

            log.debug("offerBookListItems.size {}", offerBookListItems.size());
            fillOfferCountMaps();
        } catch (Throwable t) {
            log.error("Error at fillOfferBookListItems: " + t);
        }
    }

    public void printOfferBookListItems(String msg) {
        if (log.isDebugEnabled()) {
            if (offerBookListItems.size() == 0) {
                log.debug("{} -> OfferBookListItems:  none", msg);
                return;
            }

            StringBuilder stringBuilder = new StringBuilder(msg + " -> ").append("OfferBookListItems:").append("\n");
            offerBookListItems.forEach(i -> stringBuilder.append("\t").append(i.toString()).append("\n"));
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
            log.debug(stringBuilder.toString());
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
