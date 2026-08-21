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

package bisq.core.offer.placeoffer.bsq_swap.tasks;

import bisq.core.offer.Offer;
import bisq.core.offer.placeoffer.bsq_swap.PlaceBsqSwapOfferModel;

import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class ValidateBsqSwapOffer extends Task<PlaceBsqSwapOfferModel> {
    public ValidateBsqSwapOffer(TaskRunner<PlaceBsqSwapOfferModel> taskHandler, PlaceBsqSwapOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        Offer offer = model.getOffer();
        try {
            runInterceptHook();
            checkArgument(offer.isBsqSwapOffer(),
                    "Offer must be BsqSwapOfferPayload");
            // Coins
            checkCoinNotNullOrZero(offer.getAmount(), "Amount");
            checkCoinNotNullOrZero(offer.getMinAmount(), "MinAmount");

            checkArgument(offer.getAmount().compareTo(offer.getPaymentMethod().getMaxTradeLimitAsCoin(offer.getCurrencyCode())) <= 0,
                    "Amount is larger than " + offer.getPaymentMethod().getMaxTradeLimitAsCoin(offer.getCurrencyCode()).toFriendlyString());
            checkArgument(offer.getAmount().compareTo(offer.getMinAmount()) >= 0, "MinAmount is larger than Amount");

            checkNotNull(offer.getPrice(), "Price is null");
            checkArgument(offer.getPrice().isPositive(),
                    "Price must be positive. price=" + offer.getPrice().toFriendlyString());

            checkArgument(offer.getDate().getTime() > 0,
                    "Date must not be 0. date=" + offer.getDate().toString());

            checkNotNull(offer.getCurrencyCode(), "Currency is null");
            checkNotNull(offer.getDirection(), "Direction is null");
            checkNotNull(offer.getId(), "Id is null");
            checkNotNull(offer.getPubKeyRing(), "pubKeyRing is null");
            checkNotNull(offer.getMinAmount(), "MinAmount is null");
            checkNotNull(offer.getPrice(), "Price is null");
            checkNotNull(offer.getVersionNr(), "VersionNr is null");

            complete();
        } catch (Exception e) {
            offer.setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + e.getMessage());
            failed(e);
        }
    }

    public static void checkCoinNotNullOrZero(Coin value, String name) {
        checkNotNull(value, name + " is null");
        checkArgument(value.isPositive(),
                name + " must be positive. " + name + "=" + value.toFriendlyString());
    }
}
