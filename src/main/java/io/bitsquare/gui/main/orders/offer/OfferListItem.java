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

package io.bitsquare.gui.main.orders.offer;

import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.trade.Offer;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public class OfferListItem {
    private final StringProperty price = new SimpleStringProperty();
    private final StringProperty amount = new SimpleStringProperty();
    private final StringProperty date = new SimpleStringProperty();
    private final StringProperty volume = new SimpleStringProperty();

    private final Offer offer;
    private final String offerId;

    public OfferListItem(Offer offer) {
        this.offer = offer;

        this.date.set(BSFormatter.formatDateTime(offer.getCreationDate()));
        this.price.set(BSFormatter.formatFiat(offer.getPrice()));

        this.amount.set(BSFormatter.formatCoin(
                offer.getAmount()) + " (" + BSFormatter.formatCoin(offer.getMinAmount()) + ")");
        this.volume.set(BSFormatter.formatVolumeWithMinVolume(
                offer.getOfferVolume(), offer.getMinOfferVolume()));
        this.offerId = offer.getId();
    }


    public Offer getOffer() {
        return offer;
    }

    // called form table columns


    public final StringProperty dateProperty() {
        return this.date;
    }


    public final StringProperty priceProperty() {
        return this.price;
    }


    public final StringProperty amountProperty() {
        return this.amount;
    }


    public final StringProperty volumeProperty() {
        return this.volume;
    }

    public String getOfferId() {
        return offerId;
    }
}
