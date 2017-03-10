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

import io.bitsquare.messages.trade.offer.payload.Offer;

public class OfferBookListItem {
    private final Offer offer;

    public OfferBookListItem(Offer offer) {
        this.offer = offer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof OfferBookListItem)) return false;

        OfferBookListItem that = (OfferBookListItem) o;

        return !(offer != null ? !offer.equals(that.offer) : that.offer != null);
    }

    @Override
    public int hashCode() {
        return offer != null ? offer.hashCode() : 0;
    }

    public Offer getOffer() {
        return offer;
    }
}

