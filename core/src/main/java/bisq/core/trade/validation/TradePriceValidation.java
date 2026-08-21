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

package bisq.core.trade.validation;

import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferValidation;
import bisq.core.offer.bisq_v1.MarketPriceNotAvailableException;
import bisq.core.provider.price.PriceFeedService;

import static bisq.core.util.Validator.checkIsPositive;
import static com.google.common.base.Preconditions.checkNotNull;

public final class TradePriceValidation {
    // Tolerance multiplier applied to Preferences.MAX_PRICE_DISTANCE.
    // Effective absolute deviation = MAX_PRICE_DISTANCE * MAX_TRADE_PRICE_DEVIATION = 0.25 * 1.5 = 0.375 (37.5%).
    public static final double MAX_TRADE_PRICE_DEVIATION = 1.5;

    private TradePriceValidation() {
    }

    /* --------------------------------------------------------------------- */
    // Taker trade price
    /* --------------------------------------------------------------------- */

    public static long checkTakersTradePrice(long takersTradePrice,
                                             PriceFeedService priceFeedService,
                                             Offer offer) {
        long checkedTakersTradePrice = checkIsPositive(takersTradePrice, "takersTradePrice");
        checkNotNull(priceFeedService, "priceFeedService must not be null");
        checkNotNull(offer, "offer must not be null");

        try {
            offer.verifyTakersTradePrice(checkedTakersTradePrice);
            // We allow 50% tolerance to the max allowed price percentage to avoid failing trades in
            // high volatility environments
            OfferValidation.verifyPriceInBounds(priceFeedService, offer, MAX_TRADE_PRICE_DEVIATION);
            return checkedTakersTradePrice;
        } catch (TradePriceOutOfToleranceException | MarketPriceNotAvailableException e) {
            throw new RuntimeException(e);
        }
    }
}
