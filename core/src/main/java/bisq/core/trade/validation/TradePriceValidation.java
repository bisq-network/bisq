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

public final class TradePriceValidation {
    public static final double MAX_TRADE_PRICE_DEVIATION = 1.5;

    private TradePriceValidation() {
    }

    /* --------------------------------------------------------------------- */
    // Taker trade price
    /* --------------------------------------------------------------------- */

    public static long checkTakersTradePrice(long takersTradePrice,
                                             PriceFeedService priceFeedService,
                                             Offer offer) {
        try {
            offer.verifyTakersTradePrice(takersTradePrice);
            // We allow 50% tolerance to the max allowed price percentage to avoid failing trades in
            // high volatility environments
            OfferValidation.verifyPriceInBounds(priceFeedService, offer, MAX_TRADE_PRICE_DEVIATION);
            return takersTradePrice;
        } catch (TradePriceOutOfToleranceException | MarketPriceNotAvailableException e) {
            throw new RuntimeException(e);
        }
    }
}
