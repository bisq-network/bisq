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

import bisq.price.TestBase;

import java.time.Duration;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class ExchangeRateServiceTest extends TestBase {

    /**
     * Logback version of the Slf4j logger used by {@link ExchangeRateService}. This
     * allows us to test if specific messages were logged.
     * See https://stackoverflow.com/a/52229629
     */
    private static Logger exchangeRateServiceLogger;
    private static final String LIST_APPENDER_NAME = "testListAppender";

    @BeforeAll
    static void setup() {
        // Get the logger object for logs in ExchangeRateService
        exchangeRateServiceLogger = (Logger) LoggerFactory.getLogger(ExchangeRateService.class);

        // Initiate and append a ListAppender, which allows us to programmatically inspect
        // log messages
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.setName(LIST_APPENDER_NAME);
        listAppender.start();
        exchangeRateServiceLogger.addAppender(listAppender);
    }

    @Test
    public void getAllMarketPrices_withNoExchangeRates_logs_Exception() {
        int numberOfCurrencyPairsOnExchange = 0;
        ExchangeRateProvider dummyProvider = buildDummyExchangeRateProvider(numberOfCurrencyPairsOnExchange);
        ExchangeRateService service = new ExchangeRateService(Collections.singletonList(dummyProvider));

        Map<String, Object> retrievedData = service.getAllMarketPrices();

        doSanityChecksForRetrievedDataSingleProvider(
                retrievedData, dummyProvider.getPrefix(), numberOfCurrencyPairsOnExchange);

        // No exchange rates provided by this exchange, two things should happen
        // A) the timestamp should be set to 0
        // B) an error should be logged

        // A) Check that timestamp was set to 0
        // This allows Bisq clients to eventually disregard data from this provider
        assertEquals(0L, retrievedData.get(dummyProvider.getPrefix() + "Ts"));

        // B) Check that an error is logged
        // Log msg has the format: java.lang.IllegalStateException: No exchange rate data
        // found for ExchangeName-JzfP1
        List<ILoggingEvent> logsList = ((ListAppender) exchangeRateServiceLogger.getAppender(LIST_APPENDER_NAME)).list;
        assertEquals(Level.ERROR, logsList.get(0).getLevel());
        assertTrue(logsList.get(0).getMessage().endsWith("No exchange rate data found for " + dummyProvider.getName()));
    }

    @Test
    public void getAllMarketPrices_withSingleExchangeRate() {
        int numberOfCurrencyPairsOnExchange = 1;
        ExchangeRateProvider dummyProvider = buildDummyExchangeRateProvider(numberOfCurrencyPairsOnExchange);
        ExchangeRateService service = new ExchangeRateService(Collections.singletonList(dummyProvider));

        Map<String, Object> retrievedData = service.getAllMarketPrices();

        doSanityChecksForRetrievedDataSingleProvider(
                retrievedData, dummyProvider.getPrefix(), numberOfCurrencyPairsOnExchange);

        // One rate was provided by this provider, so the timestamp should not be 0
        assertNotEquals(0L, retrievedData.get(dummyProvider.getPrefix() + "Ts"));
    }

    @Test
    public void getAllMarketPrices_withMultipleProviders() {
        int numberOfCurrencyPairsOnExchange = 1;
        ExchangeRateProvider dummyProvider1 = buildDummyExchangeRateProvider(numberOfCurrencyPairsOnExchange);
        ExchangeRateProvider dummyProvider2 = buildDummyExchangeRateProvider(numberOfCurrencyPairsOnExchange);
        ExchangeRateService service = new ExchangeRateService(asList(dummyProvider1, dummyProvider2));

        Map<String, Object> retrievedData = service.getAllMarketPrices();

        doSanityChecksForRetrievedDataMultipleProviders(retrievedData,
                asList(dummyProvider1.getPrefix(), dummyProvider2.getPrefix()));

        // One rate was provided by each provider in this service, so the timestamp
        // (for both providers) should not be 0
        assertNotEquals(0L, retrievedData.get(dummyProvider1.getPrefix() + "Ts"));
        assertNotEquals(0L, retrievedData.get(dummyProvider2.getPrefix() + "Ts"));
    }

    /**
     * Performs generic sanity checks on the response format and contents.
     *
     * @param retrievedData Response data retrieved from the {@link ExchangeRateService}
     * @param providerPrefix {@link ExchangeRateProvider#getPrefix()}
     * @param numberOfCurrencyPairsOnExchange Number of currency pairs this exchange was initiated with
     */
    private void doSanityChecksForRetrievedDataSingleProvider(Map<String, Object> retrievedData,
                                                              String providerPrefix,
                                                              int numberOfCurrencyPairsOnExchange) {
        // Check response structure
        doSanityChecksForRetrievedDataMultipleProviders(retrievedData, asList(providerPrefix));

        // Check that the amount of provided exchange rates matches expected value
        // For one provider, the amount of rates of that provider should be the total amount of rates in the response
        List<String> retrievedMarketPricesData = (List<String>) retrievedData.get("data");
        assertEquals(numberOfCurrencyPairsOnExchange, retrievedMarketPricesData.size());
    }

    /**
     * Performs generic sanity checks on the response format and contents.
     *
     * @param retrievedData Response data retrieved from the {@link ExchangeRateService}
     * @param providerPrefixes List of all {@link ExchangeRateProvider#getPrefix()} the
     * {@link ExchangeRateService} uses
     */
    private void doSanityChecksForRetrievedDataMultipleProviders(Map<String, Object> retrievedData,
                                                                 List<String> providerPrefixes) {
        // Check the correct amount of entries were present in the service response:
        // The timestamp and the count fields are per provider, so N providers means N
        // times those fields timestamp (x N) + count (x N) + price data (stored as a list
        // under the key "data"). So expected size is Nx2 + 1.
        int n = providerPrefixes.size();
        assertEquals(n * 2 + 1, retrievedData.size());
        for (String providerPrefix : providerPrefixes) {
            assertNotNull(retrievedData.get(providerPrefix + "Ts"));
            assertNotNull(retrievedData.get(providerPrefix + "Count"));
        }
        assertNotNull(retrievedData.get("data"));

        // TODO Add checks for the case when rates for the same currency pair is retrieved from multiple providers
    }

    private ExchangeRateProvider buildDummyExchangeRateProvider(int numberOfRatesAvailable) {
        ExchangeRateProvider dummyProvider = new ExchangeRateProvider(
                "ExchangeName-" + getRandomAlphaNumericString(5),
                "EXCH-" + getRandomAlphaNumericString(3),
                Duration.ofDays(1)) {

            @Override
            public boolean isRunning() {
                return true;
            }

            @Override
            protected Set<ExchangeRate> doGet() {
                HashSet<ExchangeRate> exchangeRates = new HashSet<>();

                // Simulate the required amount of rates
                for (int i = 0; i < numberOfRatesAvailable; i++) {
                    exchangeRates.add(new ExchangeRate(
                            "DUM-" + getRandomAlphaNumericString(3), // random symbol, avoid duplicates
                            0,
                            System.currentTimeMillis(),
                            getName())); // ExchangeRateProvider name
                }

                return exchangeRates;
            }
        };

        // Initialize provider
        dummyProvider.start();
        dummyProvider.stop();

        return dummyProvider;
    }
}
