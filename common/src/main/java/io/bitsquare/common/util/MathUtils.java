package io.bitsquare.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class MathUtils {
    private static final Logger log = LoggerFactory.getLogger(MathUtils.class);

    public static double roundDouble(double value, int digits) {
        if (digits < 0)
            throw new IllegalArgumentException();

        BigDecimal bd = BigDecimal.valueOf(value);
        bd = bd.setScale(digits, RoundingMode.HALF_UP);
        return bd.doubleValue();
    }

    public static double exactMultiply(double value1, double value2) {
        return BigDecimal.valueOf(value1).multiply(BigDecimal.valueOf(value2)).doubleValue();
    }
}
