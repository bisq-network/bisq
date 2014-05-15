package io.bitsquare.gui.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.Locale;

public class Converter
{
    private static final Logger log = LoggerFactory.getLogger(Converter.class);

    public static double stringToDouble(String input)
    {
        if (input == null || input.equals(""))
            return 0;
        try
        {
            DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(Locale.getDefault());
            Object d = decimalFormat.getDecimalFormatSymbols();
            return decimalFormat.parse(input).doubleValue();
        } catch (ParseException e)
        {
            log.warn(e.toString());
            return 0;
        }
    }

}
