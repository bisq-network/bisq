package io.bitsquare.gui.util;

import io.bitsquare.locale.Country;
import io.bitsquare.locale.Localisation;
import io.bitsquare.trade.Direction;
import io.bitsquare.user.Arbitrator;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("WeakerAccess")
public class BitSquareFormatter
{
    public static String formatPrice(double price)
    {
        return formatDouble(price);
    }

    @NotNull
    public static String formatPriceWithCurrencyPair(double price, @NotNull Currency currency)
    {
        return formatDouble(price) + " " + currency + "/BTC";
    }

    @SuppressWarnings("SameParameterValue")
    @NotNull
    public static String formatAmount(double amount, boolean useBTC, boolean exact)
    {
        return formatDouble(amount, (exact ? 4 : 2)) + (useBTC ? " BTC" : "");
    }

    @SuppressWarnings("SameParameterValue")
    @NotNull
    public static String formatAmount(double amount, boolean useBTC)
    {
        return formatAmount(amount, useBTC, false);
    }

    @NotNull
    public static String formatAmount(double amount)
    {
        return formatAmount(amount, false);
    }

    @SuppressWarnings("SameParameterValue")
    @NotNull
    public static String formatAmountWithMinAmount(double amount, double minAmount, boolean useBTC)
    {
        if (useBTC)
            return formatDouble(amount) + " BTC (" + formatDouble(minAmount) + " BTC)";
        else
            return formatDouble(amount) + " (" + formatDouble(minAmount) + ")";
    }

    @NotNull
    public static String formatAmountWithMinAmount(double amount, double minAmount)
    {
        return formatAmountWithMinAmount(amount, minAmount, false);
    }

    public static String formatVolume(double volume)
    {
        return formatDouble(volume);
    }

    @NotNull
    public static String formatVolume(double volume, @NotNull Currency currency)
    {
        return formatDouble(volume) + " " + currency;
    }

    @NotNull
    public static String formatVolumeWithMinVolume(double volume, double minVolume, @NotNull Currency currency)
    {
        return formatDouble(volume) + " " + currency + " (" + formatDouble(minVolume) + " " + currency + ")";
    }

    @NotNull
    public static String formatVolumeWithMinVolume(double volume, double minVolume)
    {
        return formatDouble(volume) + " (" + formatDouble(minVolume) + ")";
    }

    @NotNull
    public static String formatCollateral(double collateral, double amount)
    {
        return formatPercent(collateral) + " (" + formatDouble(collateral * amount, 4) + " BTC)";
    }

    @NotNull
    public static String formatDirection(Direction direction, boolean allUpperCase)
    {
        @NotNull String result = (direction == Direction.BUY) ? "Buy" : "Sell";
        if (allUpperCase)
            result = result.toUpperCase();
        return result;
    }

    @NotNull
    public static String formatList(@NotNull List<String> list)
    {
        String s = list.toString();
        return s.substring(1, s.length() - 1);
    }

    public static String formatDouble(double value)
    {
        return formatDouble(value, 2);
    }

    public static String formatDouble(double value, int fractionDigits)
    {
        @NotNull DecimalFormat decimalFormat = getDecimalFormat(fractionDigits);
        return decimalFormat.format(value);
    }

    @NotNull
    public static DecimalFormat getDecimalFormat(int fractionDigits)
    {
        @NotNull DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(Locale.getDefault());
        decimalFormat.setMinimumFractionDigits(fractionDigits);
        decimalFormat.setMaximumFractionDigits(fractionDigits);
        decimalFormat.setGroupingUsed(false);
        return decimalFormat;
    }

    @NotNull
    private static String formatPercent(double value)
    {
        return value * 100 + "%";
    }

    @NotNull
    public static String countryLocalesToString(@NotNull List<Country> countries)
    {
        String result = "";
        int i = 0;
        for (@NotNull Country country : countries)
        {
            result += country.getName();
            i++;
            if (i < countries.size())
                result += ", ";
        }
        return result;
    }

    public static String languageLocalesToString(@NotNull List<Locale> languageLocales)
    {
        String result = "";
        int i = 0;
        for (@NotNull Locale locale : languageLocales)
        {
            result += locale.getDisplayLanguage();
            i++;
            if (i < languageLocales.size())
                result += ", ";
        }
        return result;
    }

    @NotNull
    public static String arbitrationMethodsToString(@NotNull List<Arbitrator.METHOD> items)
    {
        String result = "";
        int i = 0;
        for (@NotNull Arbitrator.METHOD item : items)
        {
            result += Localisation.get(item.toString());
            i++;
            if (i < items.size())
                result += ", ";
        }
        return result;
    }

    @NotNull
    public static String arbitrationIDVerificationsToString(@NotNull List<Arbitrator.ID_VERIFICATION> items)
    {
        String result = "";
        int i = 0;
        for (@NotNull Arbitrator.ID_VERIFICATION item : items)
        {
            result += Localisation.get(item.toString());
            i++;
            if (i < items.size())
                result += ", ";
        }
        return result;
    }

    @NotNull
    public static String formatDateTime(Date date)
    {
        DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, Locale.getDefault());
        DateFormat timeFormatter = DateFormat.getTimeInstance(DateFormat.DEFAULT, Locale.getDefault());
        return dateFormatter.format(date) + " " + timeFormatter.format(date);
    }


}
