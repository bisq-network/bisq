package io.bitsquare.btc;

import com.google.bitcoin.core.Utils;
import io.bitsquare.gui.util.BitSquareConverter;
import io.bitsquare.gui.util.BitSquareFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.Locale;

// TODO
public class BtcFormatter
{
    private static final Logger log = LoggerFactory.getLogger(BtcFormatter.class);

    public static BigInteger BTC = new BigInteger("100000000");
    public static BigInteger mBTC = new BigInteger("100000");


    public static String btcToString(BigInteger value)
    {
        return Utils.bitcoinValueToFriendlyString(value);
    }

    //TODO
    public static double satoshiToBTC(BigInteger satoshis)
    {
        return satoshis.doubleValue() / BTC.doubleValue();
    }

    public static BigInteger stringValueToSatoshis(String value)
    {
        return Utils.toNanoCoins(String.valueOf(BitSquareConverter.stringToDouble2(value)));
    }

    public static BigInteger doubleValueToSatoshis(double value)
    {
        try
        {
            // only "." as decimal sep supported by Utils.toNanoCoins
            DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(Locale.ENGLISH);
            decimalFormat.setMaximumFractionDigits(10);
            decimalFormat.setMinimumFractionDigits(10);
            String stringValue = decimalFormat.format(value);
            return Utils.toNanoCoins(stringValue);
        } catch (Exception e)
        {
            log.warn("Exception at doubleValueToSatoshis " + e.getMessage());
            return BigInteger.ZERO;
        }
    }

    public static String formatSatoshis(BigInteger satoshis, boolean useBTC)
    {
        if (useBTC)
            return BitSquareFormatter.formatDouble(satoshiToBTC(satoshis), 8) + " BTC";
        else
            return BitSquareFormatter.formatDouble(satoshiToBTC(satoshis), 8);
    }
}
