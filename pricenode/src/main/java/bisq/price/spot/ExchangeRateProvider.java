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

package bisq.price.spot;

import bisq.price.PriceProvider;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.TradeCurrency;

import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.currency.Currency;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.exceptions.CurrencyPairNotValidException;
import org.knowm.xchange.service.marketdata.MarketDataService;

import java.time.Duration;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Abstract base class for providers of bitcoin {@link ExchangeRate} data. Implementations
 * are marked with the {@link org.springframework.stereotype.Component} annotation in
 * order to be discovered via classpath scanning. Implementations are also marked with the
 * {@link org.springframework.core.annotation.Order} annotation to determine their
 * precedence over each other in the case of two or more services returning exchange rate
 * data for the same currency pair. In such cases, results from the provider with the
 * higher order value will take precedence over the provider with a lower value,
 * presuming that such providers are being iterated over in an ordered list.
 *
 * @see ExchangeRateService#ExchangeRateService(java.util.List)
 */
public abstract class ExchangeRateProvider extends PriceProvider<Set<ExchangeRate>> {

    private final String name;
    private final String prefix;

    public ExchangeRateProvider(String name, String prefix, Duration refreshInterval) {
        super(refreshInterval);
        this.name = name;
        this.prefix = prefix;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    @Override
    protected void onRefresh() {
        get().stream()
            .filter(e -> "USD".equals(e.getCurrency()) || "LTC".equals(e.getCurrency()))
            .forEach(e -> log.info("BTC/{}: {}", e.getCurrency(), e.getPrice()));
    }

    /**
     * @param exchangeClass Class of the {@link Exchange} for which the rates should be
     *                      polled
     * @return Exchange rates for Bisq-supported fiat currencies and altcoins in the
     * specified {@link Exchange}
     *
     * @see CurrencyUtil#getAllSortedFiatCurrencies()
     * @see CurrencyUtil#getAllSortedCryptoCurrencies()
     */
    protected Set<ExchangeRate> doGet(Class<? extends Exchange> exchangeClass) {
        Set<ExchangeRate> result = new HashSet<ExchangeRate>();

        // Initialize XChange objects
        Exchange exchange = ExchangeFactory.INSTANCE.createExchange(exchangeClass.getName());
        MarketDataService marketDataService = exchange.getMarketDataService();

        // Retrieve all currency pairs supported by the exchange
        List<CurrencyPair> currencyPairs = exchange.getExchangeSymbols();

        Set<String> supportedCryptoCurrencies = CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                .map(TradeCurrency::getCode)
                .collect(Collectors.toSet());

        Set<String> supportedFiatCurrencies = CurrencyUtil.getAllSortedFiatCurrencies().stream()
                .map(TradeCurrency::getCode)
                .collect(Collectors.toSet());

        // Filter the supported fiat currencies (currency pair format is BTC-FIAT)
        currencyPairs.stream()
                .filter(cp -> cp.base.equals(Currency.BTC))
                .filter(cp -> supportedFiatCurrencies.contains(cp.counter.getCurrencyCode()))
                .forEach(cp -> {
                    try {
                        Ticker t = marketDataService.getTicker(new CurrencyPair(cp.base, cp.counter));

                        result.add(new ExchangeRate(
                                cp.counter.getCurrencyCode(),
                                t.getLast(),
                                // Some exchanges do not provide timestamps
                                t.getTimestamp() == null ? new Date() : t.getTimestamp(),
                                this.getName()
                        ));
                    } catch (CurrencyPairNotValidException cpnve) {
                        // Some exchanges support certain currency pairs for other
                        // services but not for spot markets. In that case, trying to
                        // retrieve the market ticker for that pair may fail with this
                        // specific type of exception
                        log.info("Currency pair " + cp + " not supported in Spot Markets: " + cpnve.getMessage());
                    } catch (Exception e) {
                        // Catch any other type of generic exception (IO, network level,
                        // rate limit reached, etc)
                        log.info("Exception encountered while retrieving rate for currency pair " + cp + ": " +
                                e.getMessage());
                    }
                });

        // Filter the supported altcoins (currency pair format is ALT-BTC)
        currencyPairs.stream()
                .filter(cp -> cp.counter.equals(Currency.BTC))
                .filter(cp -> supportedCryptoCurrencies.contains(cp.base.getCurrencyCode()))
                .forEach(cp -> {
                    try {
                        Ticker t = marketDataService.getTicker(new CurrencyPair(cp.base, cp.counter));

                        result.add(new ExchangeRate(
                                cp.base.getCurrencyCode(),
                                t.getLast(),
                                // Some exchanges do not provide timestamps
                                t.getTimestamp() == null ? new Date() : t.getTimestamp(),
                                this.getName()
                        ));
                    } catch (CurrencyPairNotValidException cpnve) {
                        // Some exchanges support certain currency pairs for other
                        // services but not for spot markets. In that case, trying to
                        // retrieve the market ticker for that pair may fail with this
                        // specific type of exception
                        log.info("Currency pair " + cp + " not supported in Spot Markets: " + cpnve.getMessage());
                    } catch (Exception e) {
                        // Catch any other type of generic exception (IO, network level,
                        // rate limit reached, etc)
                        log.info("Exception encountered while retrieving rate for currency pair " + cp + ": " +
                                e.getMessage());
                    }
                });

        return result;
    }
}
