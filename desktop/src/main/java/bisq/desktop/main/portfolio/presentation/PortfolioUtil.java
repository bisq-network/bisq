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

package bisq.desktop.main.portfolio.presentation;

import bisq.desktop.Navigation;
import bisq.desktop.main.MainView;
import bisq.desktop.main.offer.BuyOfferView;
import bisq.desktop.main.offer.SellOfferView;
import bisq.desktop.main.offer.bsq_swap.create_offer.BsqSwapCreateOfferView;
import bisq.desktop.main.portfolio.PortfolioView;
import bisq.desktop.main.portfolio.duplicateoffer.DuplicateOfferView;

import bisq.core.offer.OfferDirection;
import bisq.core.offer.OfferPayloadBase;
import bisq.core.offer.bsq_swap.BsqSwapOfferPayload;

public class PortfolioUtil {

    public static void duplicateOffer(Navigation navigation, OfferPayloadBase offerPayload) {
        if (offerPayload instanceof BsqSwapOfferPayload) {
            var offerViewClass = offerPayload.getDirection() == OfferDirection.BUY ? BuyOfferView.class : SellOfferView.class;
            navigation.navigateToWithData(offerPayload, MainView.class, offerViewClass, BsqSwapCreateOfferView.class);
        } else {
            navigation.navigateToWithData(offerPayload, MainView.class, PortfolioView.class, DuplicateOfferView.class);
        }
    }
}
