package io.bitsquare.util;

import com.google.bitcoin.core.Coin;
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

    public static Coin nonZeroCoinOf(Coin value)
    {
        checkNotNull(value);
        checkArgument(!value.isZero());
        return value;
    }

    public static Coin positiveCoinOf(Coin value)
    {
        checkNotNull(value);
        checkArgument(value.isPositive());
        return value;
    }

}
