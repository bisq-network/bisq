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

package io.bitsquare.offer;

import io.bitsquare.common.handlers.FaultHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.p2p.DHTService;

import java.util.List;

import javafx.beans.property.LongProperty;

public interface OfferBookService extends DHTService {

    void getOffers(String fiatCode);

    void addOffer(Offer offer, ResultHandler resultHandler, FaultHandler faultHandler);

    void removeOffer(Offer offer, ResultHandler resultHandler, FaultHandler faultHandler);

    void addListener(Listener listener);

    void removeListener(Listener listener);

    LongProperty invalidationTimestampProperty();

    void requestInvalidationTimeStampFromDHT(String fiatCode);

    interface Listener {
        void onOfferAdded(Offer offer);

        void onOffersReceived(List<Offer> offers);

        void onOfferRemoved(Offer offer);
    }
}
