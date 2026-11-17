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
package bisq.core.payment.stablecoin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Ported from Bisq 2 (bisq.common.asset.StableCoinTest), without the tests of custom coins, of the lookup by code
// and of the display names
public class StableCoinTest {
    @Test
    public void pegCurrencyCodeIsUsdForAllListedCoins() {
        for (StableCoinPaymentRail rail : StableCoinPaymentRail.values()) {
            assertEquals("USD", rail.getStableCoin().getPegCurrencyCode(), rail.name());
        }
    }
}
