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

package bisq.core.offer;

import bisq.core.monetary.Price;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.Preferences;
import bisq.core.util.PriceUtil;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class OfferValidation {
    public static boolean isPriceInBounds(PriceFeedService priceFeedService, Offer offer, double tolerance) {
        if (priceFeedService == null || offer == null) {
            return false;
        }
        return findPriceBoundsViolation(priceFeedService, offer, tolerance) == null;
    }

    public static void verifyPriceInBounds(PriceFeedService priceFeedService,
                                           Offer offer,
                                           double tolerance) throws IllegalArgumentException {
        checkNotNull(priceFeedService, "priceFeedService must not be null");
        checkNotNull(offer, "offer must not be null");
        String error = findPriceBoundsViolation(priceFeedService, offer, tolerance);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
    }

    // Returns null if the offer's price is in bounds, or the check is skipped because no recent
    // market price is available. Returns a human-readable reason if the price is out of bounds.
    // Skip semantics intentionally mirror PriceUtil.hasMarketPrice (recent external price required).
    private static String findPriceBoundsViolation(PriceFeedService priceFeedService,
                                                   Offer offer,
                                                   double tolerance) {
        if (offer.isUseMarketBasedPrice()) {
            return findDeviationViolation(tolerance, offer.getMarketPriceMargin());
        }

        Price offerPrice = offer.getPrice();
        if (offerPrice == null) {
            return null;
        }
        String currencyCode = offer.getCurrencyCode();
        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
        if (marketPrice == null || !marketPrice.isRecentExternalPriceAvailable()) {
            log.debug("Recent market price not available for {}", currencyCode);
            return null;
        }

        Optional<Double> percentage = PriceUtil.calculatePercentage(currencyCode,
                offerPrice, marketPrice.getPrice(), offer.getDirection());
        if (!percentage.isPresent()) {
            return "Offer price percentage could not be calculated";
        }
        return findDeviationViolation(tolerance, percentage.get());
    }

    private static String findDeviationViolation(double tolerance, double percentagePrice) {
        double maxAllowedDeviation = Preferences.MAX_PRICE_DISTANCE * tolerance;
        if (Math.abs(percentagePrice) <= maxAllowedDeviation) {
            return null;
        }
        return String.format("Offer price is outside of tolerated max percentage price: " +
                        "observed deviation=%s, max allowed deviation=%s, applied tolerance=%s",
                percentagePrice, maxAllowedDeviation, tolerance);
    }
}
