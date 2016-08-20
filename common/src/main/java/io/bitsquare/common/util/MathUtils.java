package io.bitsquare.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MathUtils {
    private static final Logger log = LoggerFactory.getLogger(MathUtils.class);

    public static double roundDouble(double value, int precision) {
        return roundDouble(value, precision, RoundingMode.HALF_UP);
    }

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

    public static double scaleUp(double value, int precision) {
        if (precision < 0)
            throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, precision);
        return value * factor;
    }

    public static double exactMultiply(double value1, double value2) {
        return BigDecimal.valueOf(value1).multiply(BigDecimal.valueOf(value2)).doubleValue();
    }
}
