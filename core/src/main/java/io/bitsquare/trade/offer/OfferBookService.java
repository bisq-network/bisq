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

package io.bitsquare.trade.offer;

import io.bitsquare.btc.pricefeed.PriceFeed;
import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.storage.HashMapChangedListener;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles storage and retrieval of offers.
 * Uses an invalidation flag to only request the full offer map in case there was a change (anyone has added or removed an offer).
 */
public class OfferBookService {
    private static final Logger log = LoggerFactory.getLogger(OfferBookService.class);

    public interface OfferBookChangedListener {
        void onAdded(Offer offer);

        void onRemoved(Offer offer);
    }

    private final P2PService p2PService;
    private PriceFeed priceFeed;
    private final List<OfferBookChangedListener> offerBookChangedListeners = new LinkedList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookService(P2PService p2PService, PriceFeed priceFeed) {
        this.p2PService = p2PService;
        this.priceFeed = priceFeed;

        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                offerBookChangedListeners.stream().forEach(listener -> {
                    if (data.getStoragePayload() instanceof Offer) {
                        Offer offer = (Offer) data.getStoragePayload();
                        offer.setPriceFeed(priceFeed);
                        listener.onAdded(offer);
                    }
                });
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                offerBookChangedListeners.stream().forEach(listener -> {
                    if (data.getStoragePayload() instanceof Offer)
                        listener.onRemoved((Offer) data.getStoragePayload());
                });
            }
        });
    }

    public void addOfferBookChangedListener(OfferBookChangedListener offerBookChangedListener) {
        offerBookChangedListeners.add(offerBookChangedListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        boolean result = p2PService.addData(offer, true);
        if (result) {
            log.trace("Add offer to network was successful. Offer ID = " + offer.getId());
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Add offer failed");
        }
    }

    public void refreshTTL(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        boolean result = p2PService.refreshTTL(offer, true);
        if (result) {
            log.trace("Refresh TTL was successful. Offer ID = " + offer.getId());
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Refresh TTL failed.");
        }
    }

    public void removeOffer(Offer offer, @Nullable ResultHandler resultHandler, @Nullable ErrorMessageHandler errorMessageHandler) {
        if (p2PService.removeData(offer, true)) {
            log.trace("Remove offer from network was successful. Offer ID = " + offer.getId());
            if (resultHandler != null)
                resultHandler.handleResult();
        } else {
            if (errorMessageHandler != null)
                errorMessageHandler.handleErrorMessage("Remove offer failed");
        }
    }

    public List<Offer> getOffers() {
        return p2PService.getDataMap().values().stream()
                .filter(data -> data.getStoragePayload() instanceof Offer)
                .map(data -> {
                    Offer offer = (Offer) data.getStoragePayload();
                    offer.setPriceFeed(priceFeed);
                    return offer;
                })
                .collect(Collectors.toList());
    }

    public void removeOfferAtShutDown(Offer offer) {
        log.debug("removeOfferAtShutDown " + offer);
        if (p2PService.isBootstrapped())
            removeOffer(offer, null, null);
    }

}
