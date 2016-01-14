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

import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.storage.HashMapChangedListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles storage and retrieval of offers.
 * Uses an invalidation flag to only request the full offer map in case there was a change (anyone has added or removed an offer).
 */
public class OfferBookService {
    private static final Logger log = LoggerFactory.getLogger(OfferBookService.class);

    private final P2PService p2PService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public OfferBookService(P2PService p2PService) {
        this.p2PService = p2PService;
    }

    public void addHashSetChangedListener(HashMapChangedListener hashMapChangedListener) {
        p2PService.addHashSetChangedListener(hashMapChangedListener);
    }

    public void addOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        doAddOffer(offer, resultHandler, errorMessageHandler, false);
    }

    public void republishOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        doAddOffer(offer, resultHandler, errorMessageHandler, true);
    }

    private void doAddOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler, boolean rePublish) {
        boolean result;
        if (rePublish)
            result = p2PService.republishData(offer);
        else
            result = p2PService.addData(offer);

        if (result) {
            log.trace("Add offer to network was successful. Offer = " + offer);
            resultHandler.handleResult();
        } else {
            errorMessageHandler.handleErrorMessage("Add offer failed");
        }
    }

    public void removeOffer(Offer offer, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (p2PService.removeData(offer)) {
            log.trace("Remove offer from network was successful. Offer = " + offer);
            if (resultHandler != null) resultHandler.handleResult();
        } else {
            if (errorMessageHandler != null) errorMessageHandler.handleErrorMessage("Remove offer failed");
        }
    }

    public List<Offer> getOffers() {
        final List<Offer> offers = p2PService.getDataMap().values().stream()
                .filter(e -> e.expirablePayload instanceof Offer)
                .map(e -> (Offer) e.expirablePayload)
                .collect(Collectors.toList());
        return offers;
    }

    public void removeOfferAtShutDown(Offer offer) {
        log.debug("removeOfferAtShutDown " + offer);
        removeOffer(offer, null, null);
    }
}
