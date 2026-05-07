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

package bisq.core.trade.validation;

import bisq.common.util.Hex;
import bisq.common.util.Utilities;

import java.util.Arrays;

import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;

public class TradeValidationUtils {
    static long checkValueInTolerance(long actualValue, long expectedValue, double factor) {
        checkArgument(expectedValue > 0, "expectedValue must be > 0");
        checkArgument(factor >= 1.0, "factor must be >= 1");

        double min = expectedValue / factor;
        double max = expectedValue * factor;

        checkArgument(actualValue >= min && actualValue <= max,
                "actualValue is outside of allowed tolerance. " +
                        "actualValue=%s, expectedValue=%s, min=%s, max=%s, factor=%s",
                actualValue,
                expectedValue,
                min,
                max,
                factor);

        return actualValue;
    }

    public static byte[] checkByteArrayWithExpected(byte[] current, byte[] expected) {
        checkNonEmptyBytes(current, "current");
        checkNonEmptyBytes(expected, "expected");
        checkArgument(Arrays.equals(current, expected),
                "current is not matching expected. " +
                        "current=%s, expected=%s",
                Utilities.toTruncatedString(Hex.encode(current), 8),
                Utilities.toTruncatedString(Hex.encode(expected), 8));
        return current;
    }
}
