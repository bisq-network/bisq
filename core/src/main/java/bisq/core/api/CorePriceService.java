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

package bisq.core.api;

import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.util.MathUtils.roundDouble;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;


@Slf4j
class CorePriceService {

    private final PriceFeedService priceFeedService;

    @Inject
    public CorePriceService(PriceFeedService priceFeedService) {
        this.priceFeedService = priceFeedService;
    }

    public double getMarketPrice(String currencyCode) {
        if (!priceFeedService.hasPrices())
            throw new IllegalStateException("price feed service has no prices");

        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode.toUpperCase());
        if (requireNonNull(marketPrice).isPriceAvailable()) {
            return roundDouble(marketPrice.getPrice(), 4);
        } else {
            throw new IllegalStateException(format("'%s' price is not available", currencyCode));
        }
    }
}
