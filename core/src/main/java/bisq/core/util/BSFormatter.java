/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.util;

import bisq.core.app.BisqEnvironment;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.offer.OfferPayload;

import bisq.network.p2p.NodeAddress;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.text.DateFormat;
import java.text.DecimalFormat;

import java.math.BigDecimal;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j
@Singleton
public class BSFormatter {
    public final static String RANGE_SEPARATOR = " - ";

    // We don't support localized formatting. Format is always using "." as decimal mark and no grouping separator.
    // Input of "," as decimal mark (like in german locale) will be replaced with ".".
    // Input of a group separator (1,123,45) lead to an validation error.
    // Note: BtcFormat was intended to be used, but it lead to many problems (automatic format to mBit,
    // no way to remove grouping separator). It seems to be not optimal for user input formatting.
    @Getter
    protected MonetaryFormat monetaryFormat;

    //  protected String currencyCode = CurrencyUtil.getDefaultFiatCurrencyAsCode();

    public static final MonetaryFormat fiatPriceFormat = new MonetaryFormat().shift(0).minDecimals(4).repeatOptionalDecimals(0, 0);
    protected static final MonetaryFormat altcoinFormat = new MonetaryFormat().shift(0).minDecimals(8).repeatOptionalDecimals(0, 0);
    protected static final DecimalFormat decimalFormat = new DecimalFormat("#.#");


    @Inject
    public BSFormatter() {
        monetaryFormat = BisqEnvironment.getParameters().getMonetaryFormat();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BTC
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String formatCoin(Coin coin) {
        return formatCoin(coin, -1);
    }

    @NotNull
    public String formatCoin(Coin coin, int decimalPlaces) {
        return formatCoin(coin, decimalPlaces, false, 0);
    }

    public String formatCoin(long value, MonetaryFormat coinFormat) {
        return formatCoin(Coin.valueOf(value), -1, false, 0, coinFormat);
    }

    public String formatCoin(Coin coin, int decimalPlaces, boolean decimalAligned, int maxNumberOfDigits) {
        return formatCoin(coin, decimalPlaces, decimalAligned, maxNumberOfDigits, monetaryFormat);
    }

    public static String formatCoin(Coin coin,
                                    int decimalPlaces,
                                    boolean decimalAligned,
                                    int maxNumberOfDigits,
                                    MonetaryFormat coinFormat) {
        String formattedCoin = "";

        if (coin != null) {
            try {
                if (decimalPlaces < 0 || decimalPlaces > 4) {
                    formattedCoin = coinFormat.noCode().format(coin).toString();
                } else {
                    formattedCoin = coinFormat.noCode().minDecimals(decimalPlaces).repeatOptionalDecimals(1, decimalPlaces).format(coin).toString();
                }
            } catch (Throwable t) {
                log.warn("Exception at formatBtc: " + t.toString());
            }
        }

        if (decimalAligned) {
            formattedCoin = fillUpPlacesWithEmptyStrings(formattedCoin, maxNumberOfDigits);
        }

        return formattedCoin;
    }

    public String formatCoinWithCode(Coin coin) {
        return formatCoinWithCode(coin, monetaryFormat);
    }

    public String formatCoinWithCode(long value) {
        return formatCoinWithCode(Coin.valueOf(value), monetaryFormat);
    }

    public static String formatCoinWithCode(long value, MonetaryFormat coinFormat) {
        return formatCoinWithCode(Coin.valueOf(value), coinFormat);
    }

    public static String formatCoinWithCode(Coin coin, MonetaryFormat coinFormat) {
        if (coin != null) {
            try {
                // we don't use the code feature from coinFormat as it does automatic switching between mBTC and BTC and
                // pre and post fixing
                return coinFormat.postfixCode().format(coin).toString();
            } catch (Throwable t) {
                log.warn("Exception at formatBtcWithCode: " + t.toString());
                return "";
            }
        } else {
            return "";
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // FIAT
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatFiat(Fiat fiat, MonetaryFormat format, boolean appendCurrencyCode) {
        if (fiat != null) {
            try {
                final String res = format.noCode().format(fiat).toString();
                if (appendCurrencyCode)
                    return res + " " + fiat.getCurrencyCode();
                else
                    return res;
            } catch (Throwable t) {
                log.warn("Exception at formatFiatWithCode: " + t.toString());
                return Res.get("shared.na") + " " + fiat.getCurrencyCode();
            }
        } else {
            return Res.get("shared.na");
        }
    }

    private static Fiat parseToFiat(String input, String currencyCode) {
        if (input != null && input.length() > 0) {
            try {
                return Fiat.parseFiat(currencyCode, ParsingUtils.cleanDoubleInput(input));
            } catch (Exception e) {
                log.warn("Exception at parseToFiat: " + e.toString());
                return Fiat.valueOf(currencyCode, 0);
            }

        } else {
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

    public static Fiat parseToFiatWithPrecision(String input, String currencyCode) {
        if (input != null && input.length() > 0) {
            try {
                return parseToFiat(new BigDecimal(ParsingUtils.cleanDoubleInput(input)).setScale(2, BigDecimal.ROUND_HALF_UP).toString(),
                        currencyCode);
            } catch (Throwable t) {
                log.warn("Exception at parseToFiatWithPrecision: " + t.toString());
                return Fiat.valueOf(currencyCode, 0);
            }

        }
        return Fiat.valueOf(currencyCode, 0);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Altcoin
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static String formatAltcoin(Altcoin altcoin, boolean appendCurrencyCode) {
        if (altcoin != null) {
            try {
                String res = altcoinFormat.noCode().format(altcoin).toString();
                if (appendCurrencyCode)
                    return res + " " + altcoin.getCurrencyCode();
                else
                    return res;
            } catch (Throwable t) {
                log.warn("Exception at formatAltcoin: " + t.toString());
                return Res.get("shared.na") + " " + altcoin.getCurrencyCode();
            }
        } else {
            return Res.get("shared.na");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Volume
    ///////////////////////////////////////////////////////////////////////////////////////////

    @NotNull
    public static String fillUpPlacesWithEmptyStrings(String formattedNumber, int maxNumberOfDigits) {
        //FIXME: temporary deactivate adding spaces in front of numbers as we don't use a monospace font right now.
        /*int numberOfPlacesToFill = maxNumberOfDigits - formattedNumber.length();
        for (int i = 0; i < numberOfPlacesToFill; i++) {
            formattedNumber = " " + formattedNumber;
        }*/
        return formattedNumber;
    }

    public static String formatAltcoinVolume(Altcoin altcoin, boolean appendCurrencyCode) {
        if (altcoin != null) {
            try {
                // TODO quick hack...
                String res;
                if (altcoin.getCurrencyCode().equals("BSQ"))
                    res = altcoinFormat.noCode().minDecimals(2).repeatOptionalDecimals(0, 0).format(altcoin).toString();
                else
                    res = altcoinFormat.noCode().format(altcoin).toString();
                if (appendCurrencyCode)
                    return res + " " + altcoin.getCurrencyCode();
                else
                    return res;
            } catch (Throwable t) {
                log.warn("Exception at formatAltcoinVolume: " + t.toString());
                return Res.get("shared.na") + " " + altcoin.getCurrencyCode();
            }
        } else {
            return Res.get("shared.na");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Price
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static String formatPrice(Price price, MonetaryFormat fiatPriceFormat, boolean appendCurrencyCode) {
        if (price != null) {
            Monetary monetary = price.getMonetary();
            if (monetary instanceof Fiat)
                return formatFiat((Fiat) monetary, fiatPriceFormat, appendCurrencyCode);
            else
                return formatAltcoin((Altcoin) monetary, appendCurrencyCode);
        } else {
            return Res.get("shared.na");
        }
    }

    public static String formatPrice(Price price, boolean appendCurrencyCode) {
        return formatPrice(price, fiatPriceFormat, true);
    }

    public static String formatPrice(Price price) {
        return formatPrice(price, fiatPriceFormat, false);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Market price
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatMarketPrice(double price, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return formatMarketPrice(price, 2);
        else
            return formatMarketPrice(price, 8);
    }

    public static String formatMarketPrice(double price, int precision) {
        return formatRoundedDoubleWithPrecision(price, precision);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Other
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatRoundedDoubleWithPrecision(double value, int precision) {
        decimalFormat.setMinimumFractionDigits(precision);
        decimalFormat.setMaximumFractionDigits(precision);
        return decimalFormat.format(MathUtils.roundDouble(value, precision)).replace(",", ".");
    }

    public static String getDirectionWithCodeDetailed(OfferPayload.Direction direction, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return (direction == OfferPayload.Direction.BUY) ? Res.get("shared.buyingBTCWith", currencyCode) : Res.get("shared.sellingBTCFor", currencyCode);
        else
            return (direction == OfferPayload.Direction.SELL) ? Res.get("shared.buyingCurrency", currencyCode) : Res.get("shared.sellingCurrency", currencyCode);
    }

    public static String arbitratorAddressesToString(List<NodeAddress> nodeAddresses) {
        return nodeAddresses.stream().map(NodeAddress::getFullAddress).collect(Collectors.joining(", "));
    }

    public static String formatDateTime(Date date, boolean useLocaleAndLocalTimezone) {
        Locale locale = useLocaleAndLocalTimezone ? GlobalSettings.getLocale() : Locale.US;
        DateFormat dateInstance = DateFormat.getDateInstance(DateFormat.DEFAULT, locale);
        DateFormat timeInstance = DateFormat.getTimeInstance(DateFormat.DEFAULT, locale);
        if (!useLocaleAndLocalTimezone) {
            dateInstance.setTimeZone(TimeZone.getTimeZone("UTC"));
            timeInstance.setTimeZone(TimeZone.getTimeZone("UTC"));
        }
        return formatDateTime(date, dateInstance, timeInstance);
    }

    public static String formatDateTime(Date date, DateFormat dateFormatter, DateFormat timeFormatter) {
        if (date != null) {
            return dateFormatter.format(date) + " " + timeFormatter.format(date);
        } else {
            return "";
        }
    }

    public static String formatToPercentWithSymbol(double value) {
        return formatToPercent(value) + "%";
    }

    public static String formatPercentagePrice(double value) {
        return formatToPercentWithSymbol(value);
    }

    public static String formatToPercent(double value) {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        decimalFormat.setMinimumFractionDigits(2);
        decimalFormat.setMaximumFractionDigits(2);
        return decimalFormat.format(MathUtils.roundDouble(value * 100.0, 2)).replace(",", ".");
    }

    public static String formatDurationAsWords(long durationMillis) {
        return formatDurationAsWords(durationMillis, false, true);
    }

    public static String formatDurationAsWords(long durationMillis, boolean showSeconds, boolean showZeroValues) {
        String format = "";
        String second = Res.get("time.second");
        String minute = Res.get("time.minute");
        String hour = Res.get("time.hour").toLowerCase();
        String day = Res.get("time.day").toLowerCase();
        String days = Res.get("time.days");
        String hours = Res.get("time.hours");
        String minutes = Res.get("time.minutes");
        String seconds = Res.get("time.seconds");

        if (durationMillis >= DateUtils.MILLIS_PER_DAY) {
            format = "d\' " + days + ", \'";
        }

        if (showSeconds) {
            format += "H\' " + hours + ", \'m\' " + minutes + ", \'s\' " + seconds + "\'";
        } else {
            format += "H\' " + hours + ", \'m\' " + minutes + "\'";
        }

        String duration = durationMillis > 0 ? DurationFormatUtils.formatDuration(durationMillis, format) : "";

        duration = StringUtils.replacePattern(duration, "^1 " + seconds + "|\\b1 " + seconds, "1 " + second);
        duration = StringUtils.replacePattern(duration, "^1 " + minutes + "|\\b1 " + minutes, "1 " + minute);
        duration = StringUtils.replacePattern(duration, "^1 " + hours + "|\\b1 " + hours, "1 " + hour);
        duration = StringUtils.replacePattern(duration, "^1 " + days + "|\\b1 " + days, "1 " + day);

        if (!showZeroValues) {
            duration = duration.replace(", 0 seconds", "");
            duration = duration.replace(", 0 minutes", "");
            duration = duration.replace(", 0 hours", "");
            duration = StringUtils.replacePattern(duration, "^0 days, ", "");
            duration = StringUtils.replacePattern(duration, "^0 hours, ", "");
            duration = StringUtils.replacePattern(duration, "^0 minutes, ", "");
            duration = StringUtils.replacePattern(duration, "^0 seconds, ", "");
        }
        return duration.trim();
    }

    public static String getRole(boolean isBuyerMakerAndSellerTaker, boolean isMaker, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            String baseCurrencyCode = Res.getBaseCurrencyCode();
            if (isBuyerMakerAndSellerTaker)
                return isMaker ?
                        Res.get("formatter.asMaker", baseCurrencyCode, Res.get("shared.buyer")) :
                        Res.get("formatter.asTaker", baseCurrencyCode, Res.get("shared.seller"));
            else
                return isMaker ?
                        Res.get("formatter.asMaker", baseCurrencyCode, Res.get("shared.seller")) :
                        Res.get("formatter.asTaker", baseCurrencyCode, Res.get("shared.buyer"));
        } else {
            if (isBuyerMakerAndSellerTaker)
                return isMaker ?
                        Res.get("formatter.asMaker", currencyCode, Res.get("shared.seller")) :
                        Res.get("formatter.asTaker", currencyCode, Res.get("shared.buyer"));
            else
                return isMaker ?
                        Res.get("formatter.asMaker", currencyCode, Res.get("shared.buyer")) :
                        Res.get("formatter.asTaker", currencyCode, Res.get("shared.seller"));
        }

    }

    public static String formatBytes(long bytes) {
        double kb = 1024;
        double mb = kb * kb;
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        if (bytes < kb)
            return bytes + " bytes";
        else if (bytes < mb)
            return decimalFormat.format(bytes / kb) + " KB";
        else
            return decimalFormat.format(bytes / mb) + " MB";
    }

    public static String getCurrencyPair(String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return Res.getBaseCurrencyCode() + "/" + currencyCode;
        else
            return currencyCode + "/" + Res.getBaseCurrencyCode();
    }

    public static String getCounterCurrency(String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return currencyCode;
        else
            return Res.getBaseCurrencyCode();
    }

    public static String getPriceWithCurrencyCode(String currencyCode) {
        return getPriceWithCurrencyCode(currencyCode, "shared.priceInCurForCur");
    }

    public static String getPriceWithCurrencyCode(String currencyCode, String translationKey) {
        if (CurrencyUtil.isCryptoCurrency(currencyCode))
            return Res.get(translationKey, Res.getBaseCurrencyCode(), currencyCode);
        else
            return Res.get(translationKey, currencyCode, Res.getBaseCurrencyCode());
    }
}
