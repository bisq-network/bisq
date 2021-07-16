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
import bisq.price.util.coingecko.CoinGeckoMarketData;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
class CoinGecko extends ExchangeRateProvider {

    private final RestTemplate restTemplate = new RestTemplate();

    public CoinGecko(Environment env) {
        super(env, "COINGECKO", "coingecko", Duration.ofMinutes(1));
    }

    @Override
    public Set<ExchangeRate> doGet() {

        // Rate limit for the CoinGecko API is 10 calls each second per IP address
        // We retrieve all rates in bulk, so we only make 1 call per provider poll

        Set<ExchangeRate> result = new HashSet<ExchangeRate>();

        Predicate<Map.Entry> isDesiredFiatPair = t -> getSupportedFiatCurrencies().contains(t.getKey());
        Predicate<Map.Entry> isDesiredCryptoPair = t -> getSupportedCryptoCurrencies().contains(t.getKey());

        getMarketData().getRates().entrySet().stream()
                .filter(isDesiredFiatPair.or(isDesiredCryptoPair))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                .forEach((key, ticker) -> {

                    boolean useInverseRate = false;
                    if (getSupportedCryptoCurrencies().contains(key)) {
                        // Use inverse rate for alts, because the API returns the
                        // conversion rate in the opposite direction than what we need
                        // API returns the BTC/Alt rate, we need the Alt/BTC rate
                        useInverseRate = true;
                    }

                    BigDecimal rate = ticker.getValue();
                    // Find the inverse rate, while using enough decimals to reflect very
                    // small exchange rates
                    BigDecimal inverseRate = (rate.compareTo(BigDecimal.ZERO) > 0) ?
                            BigDecimal.ONE.divide(rate, 8, RoundingMode.HALF_UP) :
                            BigDecimal.ZERO;

                    result.add(new ExchangeRate(
                            key,
                            (useInverseRate ? inverseRate : rate),
                            new Date(),
                            this.getName()
                    ));
                });

        return result;
    }

    private CoinGeckoMarketData getMarketData() {
        return restTemplate.exchange(
                RequestEntity
                        .get(UriComponentsBuilder
                                .fromUriString("https://api.coingecko.com/api/v3/exchange_rates").build()
                                .toUri())
                        .build(),
                new ParameterizedTypeReference<CoinGeckoMarketData>() {
                }
        ).getBody();
    }
}
