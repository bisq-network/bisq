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

import bisq.core.provider.price.PriceFeedService;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.util.MathUtils.roundDouble;
import static bisq.core.locale.CurrencyUtil.isFiatCurrency;
import static java.lang.String.format;

@Singleton
@Slf4j
class CorePriceService {

    private final PriceFeedService priceFeedService;

    @Inject
    public CorePriceService(PriceFeedService priceFeedService) {
        this.priceFeedService = priceFeedService;
    }

    public void getMarketPrice(String currencyCode, Consumer<Double> resultHandler) {
        String upperCaseCurrencyCode = currencyCode.toUpperCase();

        if (!isFiatCurrency(upperCaseCurrencyCode))
            throw new IllegalStateException(format("%s is not a valid currency code", upperCaseCurrencyCode));

        if (!priceFeedService.hasPrices())
            throw new IllegalStateException("price feed service has no prices");

        try {
            priceFeedService.setCurrencyCode(upperCaseCurrencyCode);
        } catch (Throwable throwable) {
            log.warn("Could not set currency code in PriceFeedService", throwable);
        }

        priceFeedService.requestPriceFeed(price -> {
                    if (price > 0) {
                        log.info("{} price feed request returned {}", upperCaseCurrencyCode, price);
                        resultHandler.accept(roundDouble(price, 4));
                    } else {
                        throw new IllegalStateException(format("%s price is not available", upperCaseCurrencyCode));
                    }
                },
                (errorMessage, throwable) -> log.warn(errorMessage, throwable));
    }
}
