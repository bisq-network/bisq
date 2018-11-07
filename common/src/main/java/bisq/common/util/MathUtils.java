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

package bisq.common.util;

import com.google.common.math.DoubleMath;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MathUtils {
    private static final Logger log = LoggerFactory.getLogger(MathUtils.class);

    public static double roundDouble(double value, int precision) {
        return roundDouble(value, precision, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("SameParameterValue")
    public static double roundDouble(double value, int precision, RoundingMode roundingMode) {
        if (precision < 0)
            throw new IllegalArgumentException();

        try {
            BigDecimal bd = BigDecimal.valueOf(value);
            bd = bd.setScale(precision, roundingMode);
            return bd.doubleValue();
        } catch (Throwable t) {
            log.error(t.toString());
            return 0;
        }
    }

    public static long roundDoubleToLong(double value) {
        return roundDoubleToLong(value, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("SameParameterValue")
    public static long roundDoubleToLong(double value, RoundingMode roundingMode) {
        return DoubleMath.roundToLong(value, roundingMode);
    }

    public static int roundDoubleToInt(double value) {
        return roundDoubleToInt(value, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("SameParameterValue")
    public static int roundDoubleToInt(double value, RoundingMode roundingMode) {
        return DoubleMath.roundToInt(value, roundingMode);
    }

    public static long doubleToLong(double value) {
        return new Double(value).longValue();
    }

    public static double scaleUpByPowerOf10(double value, int exponent) {
        double factor = Math.pow(10, exponent);
        return value * factor;
    }

    public static double scaleUpByPowerOf10(long value, int exponent) {
        double factor = Math.pow(10, exponent);
        return ((double) value) * factor;
    }

    public static double scaleDownByPowerOf10(double value, int exponent) {
        double factor = Math.pow(10, exponent);
        return value / factor;
    }

    public static double scaleDownByPowerOf10(long value, int exponent) {
        double factor = Math.pow(10, exponent);
        return ((double) value) / factor;
    }

    public static double exactMultiply(double value1, double value2) {
        return BigDecimal.valueOf(value1).multiply(BigDecimal.valueOf(value2)).doubleValue();
    }
}
