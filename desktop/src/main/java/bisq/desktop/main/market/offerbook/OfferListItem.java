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

package bisq.desktop.main.market.offerbook;

import bisq.core.offer.Offer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OfferListItem {
    private static final Logger log = LoggerFactory.getLogger(OfferListItem.class);
    public final Offer offer;
    public final double accumulated;

    public OfferListItem(Offer offer, double accumulated) {
        this.offer = offer;
        this.accumulated = accumulated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        OfferListItem that = (OfferListItem) o;

        //noinspection SimplifiableIfStatement
        if (Double.compare(that.accumulated, accumulated) != 0) return false;
        return !(offer != null ? !offer.equals(that.offer) : that.offer != null);

    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = offer != null ? offer.hashCode() : 0;
        temp = Double.doubleToLongBits(accumulated);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}
