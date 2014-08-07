package io.bitsquare.gui.util;

import com.google.bitcoin.core.Coin;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Localisation;
import io.bitsquare.trade.Direction;
import io.bitsquare.user.Arbitrator;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//TODO cleanup...
public class BitSquareFormatter
{
    private static final Logger log = LoggerFactory.getLogger(BitSquareFormatter.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // BTC
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatCoin(Coin coin)
    {
        return coin != null ? coin.toPlainString() : "";
    }


   /* public static String formatCoinToWithSymbol(Coin coin)
    {
        return "฿ " + coin.toPlainString();
    } */

    public static String formatCoinWithCode(Coin coin)
    {
        return coin != null ? coin.toFriendlyString() : "";
    }

    /**
     * @param input String input in decimal or integer format. Both decimal marks (",", ".") are supported.
     *              If input has an incorrect format it returns a zero value coin.
     * @return
     */
    public static Coin parseToCoin(String input)
    {
        try
        {
            input = input.replace(",", ".");
            Double.parseDouble(input);

        } catch (NumberFormatException | NullPointerException e)
        {
            log.warn("Exception at parseBtcToCoin: " + e.toString());
            input = "0";
        }
        return Coin.parseCoin(input);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // FIAT
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static String formatPrice(double price)
    {
        return formatDouble(price);
    }

    public static String formatVolume(double volume)
    {
        return formatDouble(volume);
    }

    public static String formatVolumeWithMinVolume(double volume, double minVolume)
    {
        return formatDouble(volume) + " (" + formatDouble(minVolume) + ")";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Other
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatDirection(Direction direction, boolean allUpperCase)
    {
        String result = (direction == Direction.BUY) ? "Buy" : "Sell";
        if (allUpperCase)
        {
            result = result.toUpperCase();
        }
        return result;
    }

    public static String formatDouble(double value)
    {
        return formatDouble(value, 2);
    }

    public static String formatDouble(double value, int fractionDigits)
    {
        DecimalFormat decimalFormat = getDecimalFormat(fractionDigits);
        return decimalFormat.format(value);
    }

    public static DecimalFormat getDecimalFormat(int fractionDigits)
    {
        DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(Locale.getDefault());
        decimalFormat.setMinimumFractionDigits(fractionDigits);
        decimalFormat.setMaximumFractionDigits(fractionDigits);
        decimalFormat.setGroupingUsed(false);
        return decimalFormat;
    }

    public static String countryLocalesToString(List<Country> countries)
    {
        String result = "";
        int i = 0;
        for (Country country : countries)
        {
            result += country.getName();
            i++;
            if (i < countries.size())
            {
                result += ", ";
            }
        }
        return result;
    }

    public static String languageLocalesToString(List<Locale> languageLocales)
    {
        String result = "";
        int i = 0;
        for (Locale locale : languageLocales)
        {
            result += locale.getDisplayLanguage();
            i++;
            if (i < languageLocales.size())
            {
                result += ", ";
            }
        }
        return result;
    }


    public static String arbitrationMethodsToString(List<Arbitrator.METHOD> items)
    {
        String result = "";
        int i = 0;
        for (Arbitrator.METHOD item : items)
        {
            result += Localisation.get(item.toString());
            i++;
            if (i < items.size())
            {
                result += ", ";
            }
        }
        return result;
    }


    public static String arbitrationIDVerificationsToString(List<Arbitrator.ID_VERIFICATION> items)
    {
        String result = "";
        int i = 0;
        for (Arbitrator.ID_VERIFICATION item : items)
        {
            result += Localisation.get(item.toString());
            i++;
            if (i < items.size())
            {
                result += ", ";
            }
        }
        return result;
    }

    public static String formatDateTime(Date date)
    {
        DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault());
        DateFormat timeFormatter = DateFormat.getTimeInstance(DateFormat.DEFAULT, Locale.getDefault());
        return dateFormatter.format(date) + " " + timeFormatter.format(date);
    }

    public static String formatCollateralPercent(double collateral)
    {
        return getDecimalFormat(2).format(collateral * 100) + " %";
    }
}
