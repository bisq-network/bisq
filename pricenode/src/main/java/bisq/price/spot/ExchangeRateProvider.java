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
import org.knowm.xchange.exceptions.ExchangeException;
import org.knowm.xchange.exceptions.NotYetImplementedForExchangeException;
import org.knowm.xchange.service.marketdata.MarketDataService;
import org.knowm.xchange.service.marketdata.params.CurrencyPairsParam;
import org.knowm.xchange.service.marketdata.params.Params;

import org.springframework.core.env.Environment;

import java.time.Duration;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract base class for providers of bitcoin {@link ExchangeRate} data. Implementations
 * are marked with the {@link org.springframework.stereotype.Component} annotation in
 * order to be discovered via classpath scanning. If multiple
 * {@link ExchangeRateProvider}s retrieve rates for the same currency, then the
 * {@link ExchangeRateService} will average them out and expose an aggregate rate.
 *
 * @see ExchangeRateService#getAllMarketPrices()
 */
public abstract class ExchangeRateProvider extends PriceProvider<Set<ExchangeRate>> {

    private static Set<String> SUPPORTED_CRYPTO_CURRENCIES = new HashSet<>();
    private static Set<String> SUPPORTED_FIAT_CURRENCIES = new HashSet<>();
    private final String name;
    private final String prefix;
    private final Environment env;

    public ExchangeRateProvider(Environment env, String name, String prefix, Duration refreshInterval) {
        super(refreshInterval);
        this.name = name;
        this.prefix = prefix;
        this.env = env;
    }

    public Set<String> getSupportedFiatCurrencies() {
        if (SUPPORTED_FIAT_CURRENCIES.isEmpty()) {         // one-time initialization
            List<String> excludedFiatCurrencies =
                    Arrays.asList(env.getProperty("bisq.price.fiatcurrency.excluded", "")
                            .toUpperCase().trim().split("\\s*,\\s*"));
            String validatedExclusionList = excludedFiatCurrencies.stream()
                    .filter(ccy -> !ccy.isEmpty())
                    .filter(CurrencyUtil::isFiatCurrency)
                    .collect(Collectors.toList()).toString();
            SUPPORTED_FIAT_CURRENCIES = CurrencyUtil.getAllSortedFiatCurrencies().stream()
                    .map(TradeCurrency::getCode)
                    .filter(ccy -> !validatedExclusionList.contains(ccy.toUpperCase()))
                    .collect(Collectors.toSet());
            log.info("fiat currencies excluded: {}", validatedExclusionList);
            log.info("fiat currencies supported: {}", SUPPORTED_FIAT_CURRENCIES.size());
        }
        return SUPPORTED_FIAT_CURRENCIES;
    }

    public Set<String> getSupportedCryptoCurrencies() {
        if (SUPPORTED_CRYPTO_CURRENCIES.isEmpty()) {        // one-time initialization
            List<String> excludedCryptoCurrencies =
                    Arrays.asList(env.getProperty("bisq.price.cryptocurrency.excluded", "")
                            .toUpperCase().trim().split("\\s*,\\s*"));
            String validatedExclusionList = excludedCryptoCurrencies.stream()
                    .filter(ccy -> !ccy.isEmpty())
                    .filter(CurrencyUtil::isCryptoCurrency)
                    .collect(Collectors.toList()).toString();
            SUPPORTED_CRYPTO_CURRENCIES = CurrencyUtil.getAllSortedCryptoCurrencies().stream()
                    .map(TradeCurrency::getCode)
                    .filter(ccy -> !validatedExclusionList.contains(ccy.toUpperCase()))
                    .collect(Collectors.toSet());
            log.info("crypto currencies excluded: {}", validatedExclusionList);
            log.info("crypto currencies supported: {}", SUPPORTED_CRYPTO_CURRENCIES.size());
        }
        return SUPPORTED_CRYPTO_CURRENCIES;
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
        List<CurrencyPair> allCurrencyPairsOnExchange = exchange.getExchangeSymbols();

        // Find out which currency pairs we are interested in polling ("desired pairs")
        // This will be the intersection of:
        // 1) the pairs available on the exchange, and
        // 2) the pairs Bisq considers relevant / valid
        // This will result in two lists of desired pairs (fiat and alts)

        // Find the desired fiat pairs (pair format is BTC-FIAT)
        List<CurrencyPair> desiredFiatPairs = allCurrencyPairsOnExchange.stream()
                .filter(cp -> cp.base.equals(Currency.BTC))
                .filter(cp -> getSupportedFiatCurrencies().contains(cp.counter.getCurrencyCode()))
                .collect(Collectors.toList());

        // Find the desired altcoin pairs (pair format is ALT-BTC)
        List<CurrencyPair> desiredCryptoPairs = allCurrencyPairsOnExchange.stream()
                .filter(cp -> cp.counter.equals(Currency.BTC))
                .filter(cp -> getSupportedCryptoCurrencies().contains(cp.base.getCurrencyCode()))
                .collect(Collectors.toList());

        // Retrieve in bulk all tickers offered by the exchange
        // The benefits of this approach (vs polling each ticker) are twofold:
        // 1) the polling of the exchange is faster (one HTTP call vs several)
        // 2) it's easier to stay below any API rate limits the exchange might have
        List<Ticker> tickersRetrievedFromExchange = new ArrayList<>();
        try {
            tickersRetrievedFromExchange = marketDataService.getTickers(new CurrencyPairsParam() {

                /**
                 * The {@link MarketDataService#getTickers(Params)} interface requires a
                 * {@link CurrencyPairsParam} argument when polling for tickers in bulk.
                 * This parameter is meant to indicate a list of currency pairs for which
                 * the tickers should be polled. However, the actual implementations for
                 * the different exchanges differ, for example:
                 * - some will ignore it (and retrieve all available tickers)
                 * - some will require it (and will fail if a null or empty list is given)
                 * - some will properly handle it
                 *
                 * We take a simplistic approach, namely:
                 * - for providers that require such a filter, specify one
                 * - for all others, do not specify one
                 *
                 * We make this distinction using
                 * {@link ExchangeRateProvider#requiresFilterDuringBulkTickerRetrieval}
                 *
                 * @return Filter (list of desired currency pairs) to be used during bulk
                 * ticker retrieval
                 */
                @Override
                public Collection<CurrencyPair> getCurrencyPairs() {
                    // If required by the exchange implementation, specify a filter
                    // (list of pairs which should be retrieved)
                    if (requiresFilterDuringBulkTickerRetrieval()) {
                        return Stream.of(desiredFiatPairs, desiredCryptoPairs)
                                .flatMap(Collection::stream)
                                .collect(Collectors.toList());
                    }

                    // Otherwise, specify an empty list, indicating that the API should
                    // simply return all available tickers
                    return Collections.emptyList();
                }
            });

            if (tickersRetrievedFromExchange.isEmpty()) {
                // If the bulk ticker retrieval went through, but no tickers were
                // retrieved, this is a strong indication that this specific exchange
                // needs a specific list of pairs given as argument, for bulk retrieval to
                // work. See requiresFilterDuringBulkTickerRetrieval()
                throw new IllegalArgumentException("No tickers retrieved, " +
                        "exchange requires explicit filter argument during bulk retrieval?");
            }
        } catch (NotYetImplementedForExchangeException e) {
            // Thrown when a provider has no marketDataService.getTickers() implementation
            // either because the exchange API does not provide it, or because it has not
            // been implemented yet in the knowm xchange library

            // In this case (retrieval of bulk tickers is not possible) retrieve the
            // tickers one by one
            List<Ticker> finalTickersRetrievedFromExchange = tickersRetrievedFromExchange;
            Stream.of(desiredFiatPairs, desiredCryptoPairs)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList())
                    .forEach(cp -> {
                        try {

                            // This is done in a loop, and can therefore result in a burst
                            // of API calls. Some exchanges do not allow bursts
                            // A simplistic solution is to delay every call by 1 second
                            // TODO Switch to using a more elegant solution (per exchange)
                            // like ResilienceSpecification (needs knowm xchange libs v5)
                            if (getMarketDataCallDelay() > 0) {
                                Thread.sleep(getMarketDataCallDelay());
                            }

                            Ticker ticker = marketDataService.getTicker(cp);
                            finalTickersRetrievedFromExchange.add(ticker);

                        } catch (IOException | InterruptedException ioException) {
                            ioException.printStackTrace();
                            log.error("Could not query tickers for " + getName(), e);
                        }
                    });
        } catch (ExchangeException | // Errors reported by the exchange (rate limit, etc)
                IOException | // Errors while trying to connect to the API (timeouts, etc)
                // Potential error when integrating new exchange (hints that exchange
                // provider implementation needs to overwrite
                // requiresFilterDuringBulkTickerRetrieval() and have it return true )
                IllegalArgumentException e) {
            // Catch and handle all other possible exceptions
            // If there was a problem with polling this exchange, return right away,
            // since there are no results to parse and process
            log.error("Could not query tickers for provider " + getName(), e);
            return result;
        }

        // Create an ExchangeRate for each desired currency pair ticker that was retrieved
        Predicate<Ticker> isDesiredFiatPair = t -> desiredFiatPairs.contains(t.getCurrencyPair());
        Predicate<Ticker> isDesiredCryptoPair = t -> desiredCryptoPairs.contains(t.getCurrencyPair());
        tickersRetrievedFromExchange.stream()
                .filter(isDesiredFiatPair.or(isDesiredCryptoPair)) // Only consider desired pairs
                .forEach(t -> {
                    // All tickers here match all requirements

                    // We have two kinds of currency pairs, BTC-FIAT and ALT-BTC
                    // In the first one, BTC is the first currency of the pair
                    // In the second type, BTC is listed as the second currency
                    // Distinguish between the two and create ExchangeRates accordingly

                    // In every Bisq ExchangeRate, BTC is one currency in the pair
                    // Extract the other currency from the ticker, to create ExchangeRates
                    String otherExchangeRateCurrency;
                    if (t.getCurrencyPair().base.equals(Currency.BTC)) {
                        otherExchangeRateCurrency = t.getCurrencyPair().counter.getCurrencyCode();
                    } else {
                        otherExchangeRateCurrency = t.getCurrencyPair().base.getCurrencyCode();
                    }

                    result.add(new ExchangeRate(
                            otherExchangeRateCurrency,
                            t.getLast(),
                            // Some exchanges do not provide timestamps
                            t.getTimestamp() == null ? new Date() : t.getTimestamp(),
                            this.getName()
                    ));
                });

        return result;
    }

    /**
     * Specifies optional delay between certain kind of API calls that can result in
     * bursts. We want to avoid bursts, because this can cause certain exchanges to
     * temporarily restrict access to the pricenode IP.
     *
     * @return Amount of milliseconds of delay between marketDataService.getTicker calls.
     * By default 0, but can be overwritten by each provider.
     */
    protected long getMarketDataCallDelay() {
        return 0;
    }

    /**
     * @return Whether or not the bulk retrieval of tickers from the exchange requires an
     * explicit filter (list of desired pairs) or not. If true, the
     * {@link MarketDataService#getTickers(Params)} call will be constructed and given as
     * argument, which acts as a filter indicating for which pairs the ticker should be
     * retrieved. If false, {@link MarketDataService#getTickers(Params)} will be called
     * with an empty argument, indicating that the API should simply return all available
     * tickers on the exchange
     */
    protected boolean requiresFilterDuringBulkTickerRetrieval() {
        return false;
    }
}
