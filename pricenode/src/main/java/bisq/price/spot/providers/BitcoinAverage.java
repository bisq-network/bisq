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

package bisq.price.spot.providers;

import bisq.price.spot.ExchangeRate;
import bisq.price.spot.ExchangeRateProvider;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;

import java.util.HashSet;
import java.util.Set;

/**
 * Stub implementation (similar to #CoinMarketCap) for backward compatibility with legacy
 * Bisq clients
 */
@Component
class BitcoinAverage extends ExchangeRateProvider {

    public BitcoinAverage(Environment env) {
        // Simulate a deactivated BitcoinAverage provider
        // We still need the class to exist and be registered as a provider though,
        // because the returned data structure must contain the "btcAverageTs" key
        // for backward compatibility with Bisq clients which hardcode that key
        super(env, "BA", "btcAverage", Duration.ofMinutes(100));
    }

    /**
     * @see CoinMarketCap#doGet()
     * @return
     */
    @Override
    public Set<ExchangeRate> doGet() {

        HashSet<ExchangeRate> exchangeRates = new HashSet<>();
        exchangeRates.add(new ExchangeRate("NON_EXISTING_SYMBOL_BA", 0, 0L, getName()));
        return exchangeRates;
    }
}
