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

import bisq.core.api.exception.NotAvailableException;
import bisq.core.monetary.Price;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.util.AveragePriceUtil;

import bisq.common.util.Tuple2;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.function.Consumer;
import java.util.function.Predicate;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.util.MathUtils.roundDouble;
import static bisq.core.locale.CurrencyUtil.isCryptoCurrency;
import static bisq.core.locale.CurrencyUtil.isFiatCurrency;
import static java.lang.String.format;

@Singleton
@Slf4j
class CorePriceService {

    private final Predicate<String> isCurrencyCode = (c) -> isFiatCurrency(c) || isCryptoCurrency(c);

    private final Preferences preferences;
    private final PriceFeedService priceFeedService;
    private final TradeStatisticsManager tradeStatisticsManager;

    @Inject
    public CorePriceService(Preferences preferences,
                            PriceFeedService priceFeedService,
                            TradeStatisticsManager tradeStatisticsManager) {
        this.preferences = preferences;
        this.priceFeedService = priceFeedService;
        this.tradeStatisticsManager = tradeStatisticsManager;
    }

    void getMarketPrice(String currencyCode, Consumer<Double> resultHandler) {
        String upperCaseCurrencyCode = currencyCode.toUpperCase();

        if (!isCurrencyCode.test(upperCaseCurrencyCode))
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
                        if (isFiatCurrency(upperCaseCurrencyCode))
                            resultHandler.accept(roundDouble(price, 4));
                        else if (isCryptoCurrency(upperCaseCurrencyCode))
                            resultHandler.accept(roundDouble(price, 8));
                        else // should not happen, throw error if it does
                            throw new IllegalStateException(
                                    format("%s price feed request should not return data for unsupported currency code",
                                            upperCaseCurrencyCode));
                    } else {
                        throw new NotAvailableException(format("%s price is not available", upperCaseCurrencyCode));
                    }
                },
                log::warn);
    }

    Tuple2<Price, Price> getAverageBsqTradePrice(int days) {
        Tuple2<Price, Price> prices = AveragePriceUtil.getAveragePriceTuple(preferences, tradeStatisticsManager, days);
        if (prices.first.getValue() == 0 || prices.second.getValue() == 0)
            throw new NotAvailableException("average bsq price is not available");

        return prices;
    }
}
