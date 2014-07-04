package io.bitsquare.util;

import java.math.BigInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class Validator
{
    private static final Logger log = LoggerFactory.getLogger(Validator.class);

    public static String nonEmptyStringOf(String value)
    {
        checkNotNull(value);
        checkArgument(value.length() > 0);
        return value;
    }

    public static long nonNegativeLongOf(long value)
    {
        checkArgument(value >= 0);
        return value;
    }

    public static BigInteger nonZeroBigIntegerOf(BigInteger value)
    {
        checkNotNull(value);
        checkArgument(value.compareTo(BigInteger.ZERO) != 0);
        return value;
    }

    public static BigInteger nonNegativeBigIntegerOf(BigInteger value)
    {
        checkNotNull(value);
        checkArgument(value.compareTo(BigInteger.ZERO) >= 0);
        return value;
    }

}
