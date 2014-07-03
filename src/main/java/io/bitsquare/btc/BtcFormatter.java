package io.bitsquare.btc;

import com.google.bitcoin.core.Utils;
import io.bitsquare.gui.util.BitSquareConverter;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO
public class BtcFormatter
{
    private static final BigInteger BTC = new BigInteger("100000000");
    private static final Logger log = LoggerFactory.getLogger(BtcFormatter.class);

    public static BigInteger mBTC = new BigInteger("100000");


    public static String formatSatoshis(BigInteger value)
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
        try
        {
            return Utils.toNanoCoins(String.valueOf(BitSquareConverter.stringToDouble(value)));
        } catch (ArithmeticException e)
        {
            log.warn("ArithmeticException " + e);
        }
        return BigInteger.ZERO;
    }

    public static BigInteger doubleValueToSatoshis(double value)
    {
        try
        {
            // only "." as decimal sep supported by Utils.toNanoCoins
            final DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(Locale.ENGLISH);
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
}
