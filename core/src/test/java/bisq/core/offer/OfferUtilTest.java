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

package bisq.core.offer;

import bisq.core.monetary.Price;

import org.bitcoinj.core.Coin;

import org.junit.Assert;
import org.junit.Test;

public class OfferUtilTest {

    @Test
    public void testGetAdjustedAmount() {
        Coin result = OfferUtil.getAdjustedAmount(
                Coin.valueOf(100_000),
                Price.valueOf("USD", 1000_0000),
                20_000_000,
                1);
        Assert.assertEquals(
                "Minimum trade amount allowed should be adjusted to the smallest trade allowed.",
                "0.001 BTC",
                result.toFriendlyString()
        );

        try {
            OfferUtil.getAdjustedAmount(
                    Coin.ZERO,
                    Price.valueOf("USD", 1000_0000),
                    20_000_000,
                    1);
            Assert.fail("Expected IllegalArgumentException to be thrown when amount is too low.");
        } catch (IllegalArgumentException iae) {
            Assert.assertEquals(
                    "Unexpected exception message.",
                    "amount needs to be above minimum of 10k satoshis",
                    iae.getMessage()
            );
        }

        result = OfferUtil.getAdjustedAmount(
                Coin.valueOf(1_000_000),
                Price.valueOf("USD", 1000_0000),
                20_000_000,
                1);
        Assert.assertEquals(
                "Minimum allowed trade amount should not be adjusted.",
                "0.01 BTC",
                result.toFriendlyString()
        );

        result = OfferUtil.getAdjustedAmount(
                Coin.valueOf(100_000),
                Price.valueOf("USD", 1000_0000),
                1_000_000,
                1);
        Assert.assertEquals(
                "Minimum trade amount allowed should respect maxTradeLimit and factor, if possible.",
                "0.001 BTC",
                result.toFriendlyString()
        );

        // TODO(chirhonul): The following seems like it should raise an exception or otherwise fail.
        // We are asking for the smallest allowed BTC trade when price is 1000 USD each, and the
        // max trade limit is 5k sat = 0.00005 BTC. But the returned amount 0.00005 BTC, or
        // 0.05 USD worth, which is below the factor of 1 USD, but does respect the maxTradeLimit.
        // Basically the given constraints (maxTradeLimit vs factor) are impossible to both fulfill..
        result = OfferUtil.getAdjustedAmount(
                Coin.valueOf(100_000),
                Price.valueOf("USD", 1000_0000),
                5_000,
                1);
        Assert.assertEquals(
                "Minimum trade amount allowed with low maxTradeLimit should still respect that limit, even if result does not respect the factor specified.",
                "0.00005 BTC",
                result.toFriendlyString()
        );
    }
}
