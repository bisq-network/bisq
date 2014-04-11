package io.bitsquare.gui.util;

import io.bitsquare.settings.Settings;

import java.text.DecimalFormat;
import java.text.ParseException;

public class Converter
{
    public static double convertToDouble(String input)
    {
        try
        {
            DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(Settings.getLocale());
            return decimalFormat.parse(input).doubleValue();
        } catch (ParseException e)
        {
            //e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return 0.0;
    }


}
