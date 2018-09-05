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

package bisq.core.provider.price;

import java.time.Instant;

import lombok.Getter;
import lombok.Value;

@Value
public class MarketPrice {
    public static final long MARKET_PRICE_MAX_AGE_SEC = 1800;  // 30 min

    private final String currencyCode;
    private final double price;
    private final long timestampSec;
    @Getter
    private final boolean isExternallyProvidedPrice;

    public MarketPrice(String currencyCode, double price, long timestampSec, boolean isExternallyProvidedPrice) {
        this.currencyCode = currencyCode;
        this.price = price;
        this.timestampSec = timestampSec;
        this.isExternallyProvidedPrice = isExternallyProvidedPrice;
    }

    public boolean isPriceAvailable() {
        return price > 0;
    }

    public boolean isRecentPriceAvailable() {
        return isPriceAvailable() && timestampSec > (Instant.now().getEpochSecond() - MARKET_PRICE_MAX_AGE_SEC);
    }

    public boolean isRecentExternalPriceAvailable() {
        return isExternallyProvidedPrice && isRecentPriceAvailable();
    }
}
