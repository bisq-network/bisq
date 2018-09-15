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

import bisq.price.spot.providers.BitcoinAverage;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * High-level {@link ExchangeRate} data operations.
 */
@Service
class ExchangeRateService {
    protected final Logger log = LoggerFactory.getLogger(this.getClass());

    private final List<ExchangeRateProvider> providers;

    /**
     * Construct an {@link ExchangeRateService} with a list of all
     * {@link ExchangeRateProvider} implementations discovered via classpath scanning.
     *
     * @param providers all {@link ExchangeRateProvider} implementations in ascending
     *                  order of precedence
     */
    public ExchangeRateService(List<ExchangeRateProvider> providers) {
        this.providers = providers;
    }

    public Map<String, Object> getAllMarketPrices() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, ExchangeRate> allExchangeRates = new LinkedHashMap<>();

        providers.forEach(p -> {
            Set<ExchangeRate> exchangeRates = p.get();
            metadata.putAll(getMetadata(p, exchangeRates));
            exchangeRates.forEach(e ->
                allExchangeRates.put(e.getCurrency(), e)
            );
        });

        return new LinkedHashMap<String, Object>() {{
            putAll(metadata);
            // Use a sorted list by currency code to make comparision of json data between different
            // price nodes easier
            List<ExchangeRate> values = new ArrayList<>(allExchangeRates.values());
            values.sort(Comparator.comparing(ExchangeRate::getCurrency));
            put("data", values);
        }};
    }

    private Map<String, Object> getMetadata(ExchangeRateProvider provider, Set<ExchangeRate> exchangeRates) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        // In case a provider is not available we still want to deliver the data of the other providers, so we catch
        // a possible exception and leave timestamp at 0. The Bisq app will check if the timestamp is in a tolerance
        // window and if it is too old it will show that the price is not available.
        long timestamp = 0;
        try {
            timestamp = getTimestamp(provider, exchangeRates);
        } catch (Throwable t) {
            log.error(t.toString());
            t.printStackTrace();
        }

        if (provider instanceof BitcoinAverage.Local) {
            metadata.put("btcAverageTs", timestamp);
        }

        String prefix = provider.getPrefix();
        metadata.put(prefix + "Ts", timestamp);
        metadata.put(prefix + "Count", exchangeRates.size());

        return metadata;
    }

    private long getTimestamp(ExchangeRateProvider provider, Set<ExchangeRate> exchangeRates) {
        return exchangeRates.stream()
            .filter(e -> provider.getName().equals(e.getProvider()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No exchange rate data found for " + provider.getName()))
            .getTimestamp();
    }
}
