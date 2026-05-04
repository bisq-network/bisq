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

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class OfferValidation {
    public static boolean isPriceInBounds(PriceFeedService priceFeedService, Offer offer,
                                          double tolerance) {
        try {
            verifyPriceInBounds(priceFeedService, offer, tolerance);
            return true;
        } catch (IllegalArgumentException e) {
            log.debug("Offer isPriceInBounds check failed: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("Unexpected failure during isPriceInBounds check", e);
            return false;
        }
    }

    public static void verifyPriceInBounds(PriceFeedService priceFeedService,
                                           Offer offer,
                                           double tolerance) throws IllegalArgumentException {
        checkNotNull(priceFeedService, "priceFeedService must not be null");
        checkNotNull(offer, "offer must not be null");

        if (offer.isUseMarketBasedPrice()) {
            double percentagePrice = offer.getMarketPriceMargin();
            verifyPriceDeviation(tolerance, percentagePrice);
            return;
        }

        // If we do not have a market price we do not apply the validation
        if (!PriceUtil.hasMarketPrice(priceFeedService, offer)) {
            log.debug("Market price not available for {}", offer.getCurrencyCode());
            return;
        }

        String currencyCode = offer.getCurrencyCode();
        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
        if (marketPrice == null) {
            // If we do not have a market price we do not apply the validation
            log.debug("Market price not available for {}", offer.getCurrencyCode());
            return;
        }

        Price offerPrice = offer.getPrice();
        double marketPriceAsDouble = marketPrice.getPrice();
        double percentagePrice = PriceUtil.calculatePercentage(currencyCode, offerPrice, marketPriceAsDouble, offer.getDirection())
                .orElseThrow(() -> new IllegalArgumentException("Offer price percentage could not be calculated"));

        verifyPriceDeviation(tolerance, percentagePrice);
    }

    private static void verifyPriceDeviation(double tolerance, double percentagePrice) {
        double maxAllowedDeviation = Preferences.MAX_PRICE_DISTANCE * tolerance;
        checkArgument(Math.abs(percentagePrice) <= maxAllowedDeviation,
                String.format("Offer price is outside of tolerated max percentage price: " +
                                "observed deviation=%s, max allowed deviation=%s, applied tolerance=%s",
                        percentagePrice, maxAllowedDeviation, tolerance));
    }
}
