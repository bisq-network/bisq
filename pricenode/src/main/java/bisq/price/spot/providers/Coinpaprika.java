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
import bisq.price.util.coinpaprika.CoinpaprikaMarketData;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Component
class Coinpaprika extends ExchangeRateProvider {

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Used to determine the currencies in which the BTC price can be quoted. There seems
     * to be no programatic way to retrieve it, so we get the value from the API
     * documentation (see "quotes" param decsribed at
     * https://api.coinpaprika.com/#operation/getTickersById ). The hardcoded value below
     * is the list of allowed values as per the API documentation, but without BTC and ETH
     * as it makes no sense to quote the BTC price in them.
     */
    private final static String SUPPORTED_CURRENCIES =
            ("USD, EUR, PLN, KRW, GBP, CAD, JPY, RUB, TRY, NZD, AUD, CHF, UAH, HKD, " +
            "SGD, NGN, PHP, MXN, BRL, THB, CLP, CNY, CZK, DKK, HUF, IDR, ILS," +
            "INR, MYR, NOK, PKR, SEK, TWD, ZAR, VND, BOB, COP, PEN, ARS, ISK")
            .replace(" ", ""); // Strip any spaces

    public Coinpaprika(Environment env) {
        super(env, "COINPAPRIKA", "coinpaprika", Duration.ofMinutes(1));
    }

    @Override
    public Set<ExchangeRate> doGet() {

        // Single IP address can send less than 10 requests per second
        // We make only 1 API call per provider poll, so we're not at risk of reaching it

        Set<ExchangeRate> result = new HashSet<ExchangeRate>();

        Predicate<Map.Entry> isDesiredFiatPair = t -> getSupportedFiatCurrencies().contains(t.getKey());

        getMarketData().getQuotes().entrySet().stream()
                .filter(isDesiredFiatPair)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))
                .forEach((key, ticker) -> {

                    result.add(new ExchangeRate(
                            key,
                            ticker.getPrice(),
                            new Date(),
                            this.getName()
                    ));
                });

        return result;
    }

    private CoinpaprikaMarketData getMarketData() {
        return restTemplate.exchange(
                RequestEntity
                        .get(UriComponentsBuilder
                                .fromUriString(
                                        "https://api.coinpaprika.com/v1/tickers/btc-bitcoin?quotes=" +
                                        SUPPORTED_CURRENCIES).build()
                                .toUri())
                        .build(),
                new ParameterizedTypeReference<CoinpaprikaMarketData>() {
                }
        ).getBody();
    }
}
