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
import bisq.price.util.Altcoins;

import org.knowm.xchange.coinmarketcap.dto.marketdata.CoinMarketCapTicker;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Order(3)
class CoinMarketCap extends ExchangeRateProvider {

    private final RestTemplate restTemplate = new RestTemplate();

    public CoinMarketCap() {
        super("CMC", "coinmarketcap", Duration.ofMinutes(5)); // large data structure, so don't request it too often
    }

    @Override
    public Set<ExchangeRate> doGet() {

        return getTickers()
            .filter(t -> Altcoins.ALL_SUPPORTED.contains(t.getIsoCode()))
            .map(t ->
                new ExchangeRate(
                    t.getIsoCode(),
                    t.getPriceBTC(),
                    t.getLastUpdated(),
                    this.getName()
                )
            )
            .collect(Collectors.toSet());
    }

    private Stream<CoinMarketCapTicker> getTickers() {
        return restTemplate.exchange(
            RequestEntity
                .get(UriComponentsBuilder
                    .fromUriString("https://api.coinmarketcap.com/v1/ticker/?limit=200").build()
                    .toUri())
                .build(),
            new ParameterizedTypeReference<List<CoinMarketCapTicker>>() {
            }
        ).getBody().stream();
    }
}
