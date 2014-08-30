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

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Localisation;
import io.bitsquare.trade.Direction;

import com.google.bitcoin.core.Coin;
import com.google.bitcoin.utils.CoinFormat;
import com.google.bitcoin.utils.Fiat;

import java.math.BigDecimal;

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

/**
 * Central point for formatting and input parsing.
 * <p>
 * Note that we never use for text input values any coin or currency symbol or code.
 * BtcFormat does not support
 */
public class BSFormatter {
    private static final Logger log = LoggerFactory.getLogger(BSFormatter.class);

    private static Locale locale = Locale.getDefault();
    private static boolean useMilliBit;
    private static int scale = 3;

    // Format use 2 min decimal places and 2 more optional: 1.00 or 1.0010  
    // There are not more then 4 decimals allowed.
    // We don't support localized formatting. Format is always using "." as decimal mark and no grouping separator.
    // Input of "," as decimal mark (like in german locale) will be replaced with ".".
    // Input of a group separator (1,123,45) lead to an validation error.
    // Note: BtcFormat was intended to be used, but it lead to many problems (automatic format to mBit, 
    // no way to remove grouping separator). It seems to be not optimal for user input formatting.
    private static CoinFormat coinFormat = CoinFormat.BTC.repeatOptionalDecimals(2, 1);

    // format is like: 1,00  never more then 2 decimals 
    private static CoinFormat fiatFormat = CoinFormat.FIAT.repeatOptionalDecimals(0, 0);

    private static String currencyCode = Currency.getInstance(Locale.getDefault()).getCurrencyCode();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Config
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static void useMilliBitFormat(boolean useMilliBit) {
        BSFormatter.useMilliBit = useMilliBit;
        coinFormat = getCoinFormat();
        scale = useMilliBit ? 0 : 3;
    }

    /**
     * Note that setting the locale does not set the currency as it might be independent.
     */
    public static void setLocale(Locale locale) {
        BSFormatter.locale = locale;
    }

    private static CoinFormat getCoinFormat() {
        if (useMilliBit)
            return CoinFormat.MBTC.repeatOptionalDecimals(2, 1);
        else
            return CoinFormat.BTC.repeatOptionalDecimals(2, 1);
    }

    public static void setFiatCurrencyCode(String currencyCode) {
        BSFormatter.currencyCode = currencyCode;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BTC
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatCoin(Coin coin) {
        try {
            return coinFormat.noCode().format(coin).toString();
        } catch (Throwable t) {
            log.warn("Exception at formatBtc: " + t.toString());
            return "";
        }
    }

    public static String formatCoinWithCode(Coin coin) {
        try {
            // we don't use the code feature from coinFormat as it does automatic switching between mBTC and BTC and 
            // pre and post fixing
            return coinFormat.postfixCode().format(coin).toString();
        } catch (Throwable t) {
            log.warn("Exception at formatBtcWithCode: " + t.toString());
            return "";
        }
    }


    public static Coin parseToCoin(String input) {
        try {
            return coinFormat.parse(cleanedInput(input));
        } catch (Throwable t) {
            log.warn("Exception at parseToBtc: " + t.toString());
            return Coin.ZERO;
        }
    }

    /**
     * Converts to a coin with max. 4 decimal places. Last place gets rounded.
     * 0.01234 -> 0.0123
     * 0.01235 -> 0.0124
     *
     * @param input
     * @return
     */
    public static Coin parseToCoinWith4Decimals(String input) {
        try {
            return Coin.valueOf(new BigDecimal(parseToCoin(cleanedInput(input)).value).setScale(-scale - 1,
                    BigDecimal.ROUND_HALF_UP).setScale(scale + 1).toBigInteger().longValue());
        } catch (Throwable t) {
            log.warn("Exception at parseToCoinWith4Decimals: " + t.toString());
            return Coin.ZERO;
        }
    }

    public static boolean hasBtcValidDecimals(String input) {
        return parseToCoin(input).equals(parseToCoinWith4Decimals(input));
    }

    /**
     * Transform a coin with the properties defined in the format (used to reduce decimal places)
     *
     * @param coin The coin which should be transformed
     * @return The transformed coin
     */
    public static Coin reduceTo4Decimals(Coin coin) {
        return parseToCoin(formatCoin(coin));
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
            return Fiat.parseFiat(currencyCode, cleanedInput(input));
        } catch (Exception e) {
            return Fiat.valueOf(currencyCode, 0);
        }
    }

    /**
     * Converts to a fiat with max. 2 decimal places. Last place gets rounded.
     * 0.234 -> 0.23
     * 0.235 -> 0.24
     *
     * @param input
     * @return
     */
    public static Fiat parseToFiatWith2Decimals(String input) {
        try {
            return parseToFiat(new BigDecimal(cleanedInput(input)).setScale(2, BigDecimal.ROUND_HALF_UP).toString());
        } catch (Throwable t) {
            log.warn("Exception at parseCoinTo4Decimals: " + t.toString());
            return Fiat.valueOf(currencyCode, 0);
        }

    }

    public static boolean hasFiatValidDecimals(String input) {
        return parseToFiat(input).equals(parseToFiatWith2Decimals(input));
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

    public static String formatDouble(Fiat value) {
        return formatDouble(value, 4);
    }

    public static String formatDouble(Fiat value, int fractionDigits) {
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

    public static String formatVolumeWithMinVolume(Fiat volume, Fiat minVolume) {
        return formatFiat(volume) + " (" + formatFiat(minVolume) + ")";
    }
/*
    @Deprecated
    public static String formatCoin(Coin coin) {
        return coin != null ? coin.toPlainString() : "";
    }


    @Deprecated
    public static String formatCoinWithCode(Coin coin) {
        return coin != null ? coin.toFriendlyString() : "";
    }*/

    private static String cleanedInput(String input) {
        input = input.replace(",", ".");
        // don't use String.valueOf(Double.parseDouble(input)) as return value as it gives scientific 
        // notation (1.0E-6) which screw up coinFormat.parse
        Double.parseDouble(input);
        return input;
    }
}
