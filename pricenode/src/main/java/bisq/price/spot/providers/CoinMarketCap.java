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
 * Stub implementation of CoinMarketCap price provider to prevent NullPointerExceptions within legacy clients
 */
@Component
class CoinMarketCap extends ExchangeRateProvider {

    public CoinMarketCap(Environment env) {
        super(env, "CMC", "coinmarketcap", Duration.ofMinutes(5)); // large data structure, so don't request it too often
    }

    /**
     * Returns a Set with a non existing symbol for the CoinMarketCap price provider.
     * Price data of CMC provider is not used in the client anymore, except for the last update timestamp.
     * To prevent a unnecessary warning log in that case we have to pass at least one element.
     *
     * @return Empty Set
     */
    @Override
    public Set<ExchangeRate> doGet() {
        HashSet<ExchangeRate> exchangeRates = new HashSet<>();
        exchangeRates.add(new ExchangeRate("NON_EXISTING_SYMBOL", 0, 0L, "CMC"));
        return exchangeRates;
    }
}
