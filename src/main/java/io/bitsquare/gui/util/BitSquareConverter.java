package io.bitsquare.gui.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BitSquareConverter
{
    private static final Logger log = LoggerFactory.getLogger(BitSquareConverter.class);


    /**
     * @param input String to be converted to a double. Both decimal points "." and "," are supported. Thousands separator is not supported.
     * @return Returns a double value. Any invalid value returns Double.NEGATIVE_INFINITY.
     */
    public static double stringToDouble(String input)
    {
        try
        {
            return stringToDouble2(input);
        } catch (NumberFormatException | NullPointerException e)
        {
            return Double.NEGATIVE_INFINITY;
        }
    }


    /**
     * @param input String to be converted to a double. Both decimal points "." and "," are supported. Thousands separator is not supported.
     * @return Returns a double value. Any invalid value throws an exception.
     */
    public static double stringToDouble2(String input)
    {
        input = input.replace(",", ".");
        return Double.parseDouble(input);
    }

}
