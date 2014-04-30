package io.bitsquare.gui.util;

import io.bitsquare.btc.BtcFormatter;
import io.bitsquare.trade.Direction;

import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.Currency;
import java.util.List;
import java.util.Locale;

public class Formatter
{


    public static String formatPrice(double price)
    {
        return formatDouble(price);
    }

    public static String formatPriceWithCurrencyPair(double price, Currency currency)
    {
        return formatDouble(price) + " " + currency.toString() + "/BTC";
    }

    public static String formatAmount(double amount, boolean useBTC, boolean exact)
    {
        return formatDouble(amount, (exact ? 4 : 2)) + (useBTC ? " BTC" : "");
    }

    public static String formatAmount(double amount, boolean useBTC)
    {
        return formatAmount(amount, useBTC, false);
    }

    public static String formatAmount(double amount)
    {
        return formatAmount(amount, false);
    }

    public static String formatAmountWithMinAmount(double amount, double minAmount, boolean useBTC)
    {
        if (useBTC)
            return formatDouble(amount) + " BTC (" + formatDouble(minAmount) + " BTC)";
        else
            return formatDouble(amount) + " (" + formatDouble(minAmount) + ")";
    }

    public static String formatAmountWithMinAmount(double amount, double minAmount)
    {
        return formatAmountWithMinAmount(amount, minAmount, false);
    }

    public static String formatVolume(double volume)
    {
        return formatDouble(volume);
    }

    public static String formatVolume(double volume, Currency currency)
    {
        return formatDouble(volume) + " " + currency.toString();
    }

    public static String formatVolumeWithMinVolume(double volume, double minVolume, Currency currency)
    {
        return formatDouble(volume) + " " + currency.toString() + " (" + formatDouble(minVolume) + " " + currency.toString() + ")";
    }

    public static String formatVolumeWithMinVolume(double volume, double minVolume)
    {
        return formatDouble(volume) + " (" + formatDouble(minVolume) + ")";
    }

    public static String formatCollateral(double collateral, double amount)
    {
        return formatPercent(collateral) + " (" + formatDouble(collateral * amount, 4) + " BTC)";
    }

    public static String formatDirection(Direction direction, boolean allUpperCase)
    {
        String result = (direction == Direction.BUY) ? "Buy" : "Sell";
        if (allUpperCase)
            result = result.toUpperCase();
        return result;
    }

    public static String formatList(List<String> list)
    {
        String s = list.toString();
        return s.substring(1, s.length() - 1);
    }

    public static String formatSatoshis(BigInteger satoshis, boolean useBTC)
    {
        if (useBTC)
            return formatDouble(BtcFormatter.satoshiToBTC(satoshis), 4) + " BTC";
        else
            return formatDouble(BtcFormatter.satoshiToBTC(satoshis), 4);
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

    private static String formatPercent(double value)
    {
        return value * 100 + "%";
    }

    public static String countryLocalesToString(List<Locale> countryLocales)
    {
        String result = "";
        int i = 0;
        for (Locale locale : countryLocales)
        {
            result += locale.getCountry();
            i++;
            if (i < countryLocales.size())
                result += ", ";
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
                result += ", ";
        }
        return result;
    }

}
