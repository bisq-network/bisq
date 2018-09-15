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

import java.time.Duration;

import java.util.Set;

/**
 * Abstract base class for providers of bitcoin {@link ExchangeRate} data. Implementations
 * are marked with the {@link org.springframework.stereotype.Component} annotation in
 * order to be discovered via classpath scanning. Implementations are also marked with the
 * {@link org.springframework.core.annotation.Order} annotation to determine their
 * precedence over each other in the case of two or more services returning exchange rate
 * data for the same currency pair. In such cases, results from the provider with the
 * higher order value will taking precedence over the provider with a lower value,
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
}
