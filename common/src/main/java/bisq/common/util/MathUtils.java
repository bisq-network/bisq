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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;

public class MathUtils {
    private static final Logger log = LoggerFactory.getLogger(MathUtils.class);

    public static double roundDouble(double value, int precision) {
        return roundDouble(value, precision, RoundingMode.HALF_UP);
    }

    @SuppressWarnings("SameParameterValue")
    public static double roundDouble(double value, int precision, RoundingMode roundingMode) {
        if (precision < 0)
            throw new IllegalArgumentException();
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("Expected a finite double, but found " + value);

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
        return Double.valueOf(value).longValue();
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

    public static long getMedian(Long[] list) {
        if (list.length == 0) {
            return 0L;
        }

        int middle = list.length / 2;
        long median;
        if (list.length % 2 == 1) {
            median = list[middle];
        } else {
            median = MathUtils.roundDoubleToLong((list[middle - 1] + list[middle]) / 2.0);
        }
        return median;
    }

    public static class MovingAverage {
        final Deque<Long> window;
        private final int size;
        private long sum;
        private final double outlier;

        // Outlier as ratio
        public MovingAverage(int size, double outlier) {
            this.size = size;
            window = new ArrayDeque<>(size);
            this.outlier = outlier;
            sum = 0;
        }

        public Optional<Double> next(long val) {
            try {
                var fullAtStart = isFull();
                if (fullAtStart) {
                    if (outlier > 0) {
                        // Return early if it's an outlier
                        checkArgument(size != 0);
                        var avg = (double) sum / size;
                        if (Math.abs(avg - val) / avg > outlier) {
                            return Optional.empty();
                        }
                    }
                    sum -= window.remove();
                }
                window.add(val);
                sum += val;
                if (!fullAtStart && isFull() && outlier != 0) {
                    removeInitialOutlier();
                }
                // When discarding outliers, the first n non discarded elements return Optional.empty()
                return outlier > 0 && !isFull() ? Optional.empty() : current();
            } catch (Throwable t) {
                log.error(t.toString());
                return Optional.empty();
            }
        }

        boolean isFull() {
            return window.size() == size;
        }

        private void removeInitialOutlier() {
            var element = window.iterator();
            while (element.hasNext()) {
                var val = element.next();
                int div = size - 1;
                checkArgument(div != 0);
                var avgExVal = (double) (sum - val) / div;
                if (Math.abs(avgExVal - val) / avgExVal > outlier) {
                    element.remove();
                    break;
                }
            }
        }

        public Optional<Double> current() {
            return window.size() == 0 ? Optional.empty() : Optional.of((double) sum / window.size());
        }
    }
}
