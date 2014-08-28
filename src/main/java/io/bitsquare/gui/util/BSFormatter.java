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
import com.google.bitcoin.utils.BtcFormat;
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
    private static String code = "BTC";
    private static int scale = 3;

    // format is like: 1,00 or 1,0010  never more then 4 decimals 
    private static CoinFormat coinFormat = CoinFormat.BTC.repeatOptionalDecimals(2, 1);

    // format is like: 1,00  never more then 2 decimals 
    private static CoinFormat fiatFormat = CoinFormat.FIAT.repeatOptionalDecimals(0, 0);

    private static String currencyCode = Currency.getInstance(Locale.getDefault()).getCurrencyCode();

    private static BtcFormat btcFormat = getBtcFormat();

    static {
        //useMilliBitFormat(true);
        setLocale(Locale.US);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Config
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static void useMilliBitFormat(boolean useMilliBit) {
        BSFormatter.useMilliBit = useMilliBit;
        code = useMilliBit ? "mBTC" : "BTC";
        btcFormat = getBtcFormat();
        scale = useMilliBit ? 0 : 3;
    }

    /**
     * Note that setting the locale does not set the currency as it might be independent.
     */
    public static void setLocale(Locale locale) {
        BSFormatter.locale = locale;
        btcFormat = getBtcFormat();
    }

    private static BtcFormat getBtcFormat() {
        return BtcFormat.getInstance(useMilliBit ? BtcFormat.MILLICOIN_SCALE : BtcFormat.COIN_SCALE, locale, 2, 2);
    }

    public static void setFiatCurrencyCode(String currencyCode) {
        BSFormatter.currencyCode = currencyCode;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BTC
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatCoin(Coin coin) {
        try {
            return btcFormat.format(coin);
        } catch (Throwable t) {
            log.warn("Exception at formatBtc: " + t.toString());
            return "";
        }
    }

    public static String formatCoinWithCode(Coin coin) {
        try {
            // we don't use the code feature from btcFormat as it does automatic switching between mBTC and BTC and 
            // pre and post fixing
            return btcFormat.format(coin) + " " + code;
        } catch (Throwable t) {
            log.warn("Exception at formatBtcWithCode: " + t.toString());
            return "";
        }
    }

    public static Coin parseToCoin(String input) {
        try {
            return btcFormat.parse(input);
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
            return Coin.valueOf(new BigDecimal(parseToCoin(input).value).setScale(-scale - 1,
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
    public static Coin reduceto4Dezimals(Coin coin) {
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
            input = input.replace(",", ".");
            Double.parseDouble(input); // test if valid double
            return Fiat.parseFiat(currencyCode, input);
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
            input = input.replace(",", ".");
            Double.parseDouble(input); // test if valid double
            return parseToFiat(new BigDecimal(input).setScale(2, BigDecimal.ROUND_HALF_UP).toString());
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
/*
    @Deprecated
    public static String formatCoin(Coin coin) {
        return coin != null ? coin.toPlainString() : "";
    }


    @Deprecated
    public static String formatCoinWithCode(Coin coin) {
        return coin != null ? coin.toFriendlyString() : "";
    }*/
}
