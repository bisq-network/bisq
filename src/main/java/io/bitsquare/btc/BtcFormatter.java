package io.bitsquare.btc;

import com.google.bitcoin.core.Utils;
import io.bitsquare.gui.util.Converter;
import io.bitsquare.gui.util.Formatter;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.Locale;

public class BtcFormatter
{
    public static BigInteger BTC = new BigInteger("100000000");
    public static BigInteger mBTC = new BigInteger("100000");

    //TODO
    public static double satoshiToBTC(BigInteger satoshis)
    {
        return satoshis.doubleValue() / BTC.doubleValue();
    }

    public static BigInteger stringValueToSatoshis(String value)
    {
        return Utils.toNanoCoins(String.valueOf(Converter.stringToDouble(value)));
    }

    public static BigInteger doubleValueToSatoshis(double value)
    {
        try
        {
            // only "." as decimal sep supported by Utils.toNanoCoins
            DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(Locale.ENGLISH);
            String stringValue = decimalFormat.format(value);
            return Utils.toNanoCoins(stringValue);
        } catch (Exception e)
        {
            return BigInteger.ZERO;
        }
    }

    public static String formatSatoshis(BigInteger satoshis, boolean useBTC)
    {
        if (useBTC)
            return Formatter.formatDouble(satoshiToBTC(satoshis), 8) + " BTC";
        else
            return Formatter.formatDouble(satoshiToBTC(satoshis), 8);
    }
}
