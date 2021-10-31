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

package bisq.core.offer.placeoffer.bsq_swap;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;

import bisq.common.taskrunner.Model;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
public class PlaceBsqSwapOfferModel implements Model {
    private final Offer offer;
    private final OfferBookService offerBookService;

    @Setter
    private boolean offerAddedToOfferBook;

    public PlaceBsqSwapOfferModel(Offer offer, OfferBookService offerBookService) {
        this.offer = offer;
        this.offerBookService = offerBookService;
    }

    @Override
    public void onComplete() {
    }
}
