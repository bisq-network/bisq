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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

import java.util.Date;
import java.util.Objects;

/**
 * A value object representing the spot price in bitcoin for a given currency at a given
 * time as reported by a given provider.
 */
public class ExchangeRate {

    private final String currency;
    private final double price;
    private final long timestamp;
    private final String provider;

    public ExchangeRate(String currency, BigDecimal price, Date timestamp, String provider) {
        this(
            currency,
            price.doubleValue(),
            timestamp.getTime(),
            provider
        );
    }

    public ExchangeRate(String currency, double price, long timestamp, String provider) {
        this.currency = currency;
        this.price = price;
        this.timestamp = timestamp;
        this.provider = provider;
    }

    @JsonProperty(value = "currencyCode", index = 1)
    public String getCurrency() {
        return currency;
    }

    @JsonProperty(value = "price", index = 2)
    public double getPrice() {
        return this.price;
    }

    @JsonProperty(value = "timestampSec", index = 3)
    public long getTimestamp() {
        return this.timestamp;
    }

    @JsonProperty(value = "provider", index = 4)
    public String getProvider() {
        return provider;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExchangeRate exchangeRate = (ExchangeRate) o;
        return Double.compare(exchangeRate.price, price) == 0 &&
            timestamp == exchangeRate.timestamp &&
            Objects.equals(currency, exchangeRate.currency) &&
            Objects.equals(provider, exchangeRate.provider);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, price, timestamp, provider);
    }

    @Override
    public String toString() {
        return "ExchangeRate{" +
            "currency='" + currency + '\'' +
            ", price=" + price +
            ", timestamp=" + timestamp +
            ", provider=" + provider +
            '}';
    }
}
