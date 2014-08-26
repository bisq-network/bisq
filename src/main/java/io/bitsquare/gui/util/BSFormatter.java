/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.util;

import io.bitsquare.locale.Country;
import io.bitsquare.locale.Localisation;
import io.bitsquare.trade.Direction;
import io.bitsquare.user.Arbitrator;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.CoinFormat;
import com.google.bitcoin.utils.Fiat;

import java.text.DateFormat;
import java.text.DecimalFormat;

import java.util.Currency;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.*;

//TODO a lot of old trash... need to cleanup...
public class BSFormatter {
    private static final Logger log = LoggerFactory.getLogger(BSFormatter.class);

    // format is like: 1,00 or 1,0010  never more then 4 decimals 
    private static CoinFormat coinFormat = CoinFormat.BTC.repeatOptionalDecimals(2, 1);

    // format is like: 1,00  never more then 2 decimals 
    private static CoinFormat fiatFormat = CoinFormat.FIAT.repeatOptionalDecimals(0, 0);

    private static String currencyCode = Currency.getInstance(Locale.getDefault()).getCurrencyCode();
    private static Locale locale = Locale.getDefault();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Config
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void useMilliBitFormat() {
        coinFormat = CoinFormat.MBTC.repeatOptionalDecimals(2, 1);
    }

    public static void setFiatCurrencyCode(String currencyCode) {
        BSFormatter.currencyCode = currencyCode;
    }

    public static void setLocale(Locale locale) {
        BSFormatter.locale = locale;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BTC
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatBtc(Coin coin) {
        try {
            return coinFormat.noCode().format(coin).toString();
        } catch (Throwable t) {
            log.warn("Exception at formatBtc: " + t.toString());
            return "";
        }
    }

    public static String formatBtcWithCode(Coin coin) {
        try {
            return coinFormat.postfixCode().format(coin).toString();
        } catch (Throwable t) {
            log.warn("Exception at formatBtcWithCode: " + t.toString());
            return "";
        }
    }

    public static Coin parseToCoin(String input) {
        try {
            input = input.replace(",", ".");
            Double.parseDouble(input); // test if valid double
            return Coin.parseCoin(input);
        } catch (Throwable t) {
            log.warn("Exception at parseToCoin: " + t.toString());
            return Coin.ZERO;
        }
    }

    /**
     * Transform a coin with the properties defined in the format (used to reduce decimal places)
     *
     * @param coin The coin which should be transformed
     * @return The transformed coin
     */
    public static Coin applyFormatRules(Coin coin) {
        return parseToCoin(formatBtc(coin));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // FIAT
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatFiat(Fiat fiat) {
        try {
            return fiatFormat.noCode().format(fiat).toString();
        } catch (Throwable t) {
            log.warn("Exception at formatFiat: " + t.toString());
            return "";
        }
    }

    public static String formatFiatWithCode(Fiat fiat) {
        try {
            return fiatFormat.postfixCode().format(fiat).toString();
        } catch (Throwable t) {
            log.warn("Exception at formatFiatWithCode: " + t.toString());
            return "";
        }
    }

    public static Fiat parseToFiat(String input) {
        try {
            input = input.replace(",", ".");
            Double.parseDouble(input); // test if valid double
            return Fiat.parseFiat(currencyCode, input);
        } catch (Exception e) {
            return Fiat.valueOf(currencyCode, 0);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Other
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param input String to be converted to a double. Both decimal points "." and ",
     *              " are supported. Thousands separator is not supported.
     * @return Returns a double value. Any invalid value returns Double.NEGATIVE_INFINITY.
     */
    public static double parseToDouble(String input) {
        try {
            checkNotNull(input);
            checkArgument(input.length() > 0);
            input = input.replace(",", ".").trim();
            return Double.parseDouble(input);
        } catch (Exception e) {
            return 0;
        }
    }

    public static String formatDirection(Direction direction, boolean allUpperCase) {
        String result = (direction == Direction.BUY) ? "Buy" : "Sell";
        if (allUpperCase) {
            result = result.toUpperCase();
        }
        return result;
    }

    public static String formatDouble(double value) {
        return formatDouble(value, 4);
    }

    public static String formatDouble(double value, int fractionDigits) {
        DecimalFormat decimalFormat = getDecimalFormat(fractionDigits);
        return decimalFormat.format(value);
    }

    public static DecimalFormat getDecimalFormat(int fractionDigits) {
        DecimalFormat decimalFormat = (DecimalFormat) DecimalFormat.getInstance(locale);
        decimalFormat.setMinimumFractionDigits(fractionDigits);
        decimalFormat.setMaximumFractionDigits(fractionDigits);
        decimalFormat.setGroupingUsed(false);
        return decimalFormat;
    }

    public static String countryLocalesToString(List<Country> countries) {
        String result = "";
        int i = 0;
        for (Country country : countries) {
            result += country.getName();
            i++;
            if (i < countries.size()) {
                result += ", ";
            }
        }
        return result;
    }

    public static String languageLocalesToString(List<Locale> languageLocales) {
        String result = "";
        int i = 0;
        for (Locale locale : languageLocales) {
            result += locale.getDisplayLanguage();
            i++;
            if (i < languageLocales.size()) {
                result += ", ";
            }
        }
        return result;
    }


    public static String arbitrationMethodsToString(List<Arbitrator.METHOD> items) {
        String result = "";
        int i = 0;
        for (Arbitrator.METHOD item : items) {
            result += Localisation.get(item.toString());
            i++;
            if (i < items.size()) {
                result += ", ";
            }
        }
        return result;
    }


    public static String arbitrationIDVerificationsToString(List<Arbitrator.ID_VERIFICATION> items) {
        String result = "";
        int i = 0;
        for (Arbitrator.ID_VERIFICATION item : items) {
            result += Localisation.get(item.toString());
            i++;
            if (i < items.size()) {
                result += ", ";
            }
        }
        return result;
    }

    public static String formatDateTime(Date date) {
        DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, locale);
        DateFormat timeFormatter = DateFormat.getTimeInstance(DateFormat.DEFAULT, locale);
        return dateFormatter.format(date) + " " + timeFormatter.format(date);
    }

    public static String formatCollateralPercent(long collateral) {
        return getDecimalFormat(1).format(collateral / 10) + " %";
    }

    @Deprecated
    public static String formatPrice(double volume) {
        return formatDouble(volume);
    }

    @Deprecated
    public static String formatVolume(double volume) {
        return formatDouble(volume);
    }

    @Deprecated
    public static String formatVolumeWithMinVolume(double volume, double minVolume) {
        return formatDouble(volume) + " (" + formatDouble(minVolume) + ")";
    }

    @Deprecated
    public static String formatCoin(Coin coin) {
        return coin != null ? coin.toPlainString() : "";
    }


    @Deprecated
    public static String formatCoinWithCode(Coin coin) {
        return coin != null ? coin.toFriendlyString() : "";
    }
}
