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

package bisq.core.monetary;

import org.junit.Assert;
import org.junit.Test;

public class PriceTest {

    @Test
    public void testParse() {
        Price result = Price.parse("USD", "0.1");
        Assert.assertEquals(
                "Fiat value should be formatted with two decimals.",
                "0.10 BTC/USD",
                result.toFriendlyString()
        );

        result = Price.parse("EUR", "0.1234");
        Assert.assertEquals(
                "Fiat value should be given two decimals",
                "0.1234 BTC/EUR",
                result.toFriendlyString()
        );

        try {
            Price.parse("EUR", "0.12345");
            Assert.fail("Expected IllegalArgumentException to be thrown when too many decimals are used.");
        } catch (IllegalArgumentException iae) {
            Assert.assertEquals(
                    "Unexpected exception message.",
                    "java.lang.ArithmeticException: Rounding necessary",
                    iae.getMessage()
            );
        }

        Assert.assertEquals(
                "Negative value should be parsed correctly.",
                -100000000L,
                Price.parse("LTC", "-1").getValue()
        );

        Assert.assertEquals(
                "Comma (',') as decimal separator should be converted to period ('.')",
                "0.0001 BTC/USD",
                Price.parse("USD", "0,0001").toFriendlyString()
        );

        Assert.assertEquals(
                "Too many decimals should get rounded up properly.",
                "10000.2346 LTC/BTC",
                Price.parse("LTC", "10000,23456789").toFriendlyString()
        );

        Assert.assertEquals(
                "Too many decimals should get rounded down properly.",
                "10000.2345 LTC/BTC",
                Price.parse("LTC", "10000,23454999").toFriendlyString()
        );

        Assert.assertEquals(
                "Underlying long value should be correct.",
                1000023456789L,
                Price.parse("LTC", "10000,23456789").getValue()
        );

        try {
            Price.parse("XMR", "56789.123456789");
            Assert.fail("Expected IllegalArgumentException to be thrown when too many decimals are used.");
        } catch (IllegalArgumentException iae) {
            Assert.assertEquals(
                    "Unexpected exception message.",
                    "java.lang.ArithmeticException: Rounding necessary",
                    iae.getMessage()
            );
        }
    }
    @Test
    public void testValueOf() {
        Price result = Price.valueOf("USD", 1);
        Assert.assertEquals(
                "Fiat value should have four decimals.",
                "0.0001 BTC/USD",
                result.toFriendlyString()
        );

        result = Price.valueOf("EUR", 1234);
        Assert.assertEquals(
                "Fiat value should be given two decimals",
                "0.1234 BTC/EUR",
                result.toFriendlyString()
        );

        Assert.assertEquals(
                "Negative value should be parsed correctly.",
                -1L,
                Price.valueOf("LTC", -1L).getValue()
        );

        Assert.assertEquals(
                "Too many decimals should get rounded up properly.",
                "10000.2346 LTC/BTC",
                Price.valueOf("LTC", 1000023456789L).toFriendlyString()
        );

        Assert.assertEquals(
                "Too many decimals should get rounded down properly.",
                "10000.2345 LTC/BTC",
                Price.valueOf("LTC", 1000023454999L).toFriendlyString()
        );

        Assert.assertEquals(
                "Underlying long value should be correct.",
                1000023456789L,
                Price.valueOf("LTC", 1000023456789L).getValue()
        );
    }
}
