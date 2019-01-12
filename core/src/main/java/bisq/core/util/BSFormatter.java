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
import bisq.core.locale.LanguageUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;

import bisq.network.p2p.NodeAddress;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.text.DateFormat;
import java.text.DecimalFormat;

import java.math.BigDecimal;

import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

@Slf4j
public class BSFormatter {
    public final static String RANGE_SEPARATOR = " - ";

    protected boolean useMilliBit;
    protected int scale = 3;

    // We don't support localized formatting. Format is always using "." as decimal mark and no grouping separator.
    // Input of "," as decimal mark (like in german locale) will be replaced with ".".
    // Input of a group separator (1,123,45) lead to an validation error.
    // Note: BtcFormat was intended to be used, but it lead to many problems (automatic format to mBit,
    // no way to remove grouping separator). It seems to be not optimal for user input formatting.
    protected MonetaryFormat coinFormat;

    //  protected String currencyCode = CurrencyUtil.getDefaultFiatCurrencyAsCode();

    protected final MonetaryFormat fiatPriceFormat = new MonetaryFormat().shift(0).minDecimals(4).repeatOptionalDecimals(0, 0);
    protected final MonetaryFormat fiatVolumeFormat = new MonetaryFormat().shift(0).minDecimals(2).repeatOptionalDecimals(0, 0);
    protected final MonetaryFormat altcoinFormat = new MonetaryFormat().shift(0).minDecimals(8).repeatOptionalDecimals(0, 0);
    protected final DecimalFormat decimalFormat = new DecimalFormat("#.#");


    @Inject
    public BSFormatter() {
        coinFormat = BisqEnvironment.getParameters().getMonetaryFormat();
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
        return formatCoin(coin, decimalPlaces, decimalAligned, maxNumberOfDigits, coinFormat);
    }

    public String formatCoin(Coin coin, int decimalPlaces, boolean decimalAligned, int maxNumberOfDigits, MonetaryFormat coinFormat) {
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
        return formatCoinWithCode(coin, coinFormat);
    }

    public String formatCoinWithCode(long value) {
        return formatCoinWithCode(Coin.valueOf(value), coinFormat);
    }

    public String formatCoinWithCode(long value, MonetaryFormat coinFormat) {
        return formatCoinWithCode(Coin.valueOf(value), coinFormat);
    }

    public String formatCoinWithCode(Coin coin, MonetaryFormat coinFormat) {
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

    public Coin parseToCoin(String input) {
        return parseToCoin(input, coinFormat);
    }

    public Coin parseToCoin(String input, MonetaryFormat coinFormat) {
        if (input != null && input.length() > 0) {
            try {
                return coinFormat.parse(cleanDoubleInput(input));
            } catch (Throwable t) {
                log.warn("Exception at parseToBtc: " + t.toString());
                return Coin.ZERO;
            }
        } else {
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
    public Coin parseToCoinWith4Decimals(String input) {
        try {
            return Coin.valueOf(new BigDecimal(parseToCoin(cleanDoubleInput(input)).value).setScale(-scale - 1,
                    BigDecimal.ROUND_HALF_UP).setScale(scale + 1, BigDecimal.ROUND_HALF_UP).toBigInteger().longValue());
        } catch (Throwable t) {
            if (input != null && input.length() > 0)
                log.warn("Exception at parseToCoinWith4Decimals: " + t.toString());
            return Coin.ZERO;
        }
    }

    public boolean hasBtcValidDecimals(String input) {
        return parseToCoin(input).equals(parseToCoinWith4Decimals(input));
    }

    /**
     * Transform a coin with the properties defined in the format (used to reduce decimal places)
     *
     * @param coin The coin which should be transformed
     * @return The transformed coin
     */
    public Coin reduceTo4Decimals(Coin coin) {
        return parseToCoin(formatCoin(coin));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // FIAT
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String formatFiat(Fiat fiat, MonetaryFormat format, boolean appendCurrencyCode) {
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

    protected Fiat parseToFiat(String input, String currencyCode) {
        if (input != null && input.length() > 0) {
            try {
                return Fiat.parseFiat(currencyCode, cleanDoubleInput(input));
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

    public Fiat parseToFiatWithPrecision(String input, String currencyCode) {
        if (input != null && input.length() > 0) {
            try {
                return parseToFiat(new BigDecimal(cleanDoubleInput(input)).setScale(2, BigDecimal.ROUND_HALF_UP).toString(),
                        currencyCode);
            } catch (Throwable t) {
                log.warn("Exception at parseToFiatWithPrecision: " + t.toString());
                return Fiat.valueOf(currencyCode, 0);
            }

        }
        return Fiat.valueOf(currencyCode, 0);
    }

    public boolean isFiatAlteredWhenPrecisionApplied(String input, String currencyCode) {
        return parseToFiat(input, currencyCode).equals(parseToFiatWithPrecision(input, currencyCode));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Altcoin
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String formatAltcoin(Altcoin altcoin) {
        return formatAltcoin(altcoin, false);
    }

    public String formatAltcoinWithCode(Altcoin altcoin) {
        return formatAltcoin(altcoin, true);
    }

    public String formatAltcoin(Altcoin altcoin, boolean appendCurrencyCode) {
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

    public String formatVolume(Offer offer, Boolean decimalAligned, int maxNumberOfDigits) {
        return formatVolume(offer, decimalAligned, maxNumberOfDigits, true);
    }

    public String formatVolume(Offer offer, Boolean decimalAligned, int maxNumberOfDigits, boolean showRange) {
        String formattedVolume = offer.isRange() && showRange ? formatVolume(offer.getMinVolume()) + RANGE_SEPARATOR + formatVolume(offer.getVolume()) : formatVolume(offer.getVolume());

        if (decimalAligned) {
            formattedVolume = fillUpPlacesWithEmptyStrings(formattedVolume, maxNumberOfDigits);
        }
        return formattedVolume;
    }

    @NotNull
    public String fillUpPlacesWithEmptyStrings(String formattedNumber, int maxNumberOfDigits) {
        //FIXME: temporary deactivate adding spaces in front of numbers as we don't use a monospace font right now.
        /*int numberOfPlacesToFill = maxNumberOfDigits - formattedNumber.length();
        for (int i = 0; i < numberOfPlacesToFill; i++) {
            formattedNumber = " " + formattedNumber;
        }*/
        return formattedNumber;
    }

    public String formatVolume(Volume volume) {
        return formatVolume(volume, fiatVolumeFormat, false);
    }

    public String formatVolumeWithCode(Volume volume) {
        return formatVolume(volume, fiatVolumeFormat, true);
    }

    public String formatVolume(Volume volume, MonetaryFormat fiatVolumeFormat, boolean appendCurrencyCode) {
        if (volume != null) {
            Monetary monetary = volume.getMonetary();
            if (monetary instanceof Fiat)
                return formatFiat((Fiat) monetary, fiatVolumeFormat, appendCurrencyCode);
            else
                return formatAltcoinVolume((Altcoin) monetary, appendCurrencyCode);
        } else {
            return "";
        }
    }

    public String formatAltcoinVolume(Altcoin altcoin, boolean appendCurrencyCode) {
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

    public String formatVolumeLabel(String currencyCode) {
        return formatVolumeLabel(currencyCode, "");
    }

    public String formatVolumeLabel(String currencyCode, String postFix) {
        return Res.get("formatter.formatVolumeLabel",
                currencyCode, postFix);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Amount
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String formatAmount(Offer offer) {
        return formatAmount(offer, false);
    }

    public String formatAmount(Offer offer, boolean decimalAligned) {
        String formattedAmount = offer.isRange() ? formatCoin(offer.getMinAmount()) + RANGE_SEPARATOR + formatCoin(offer.getAmount()) : formatCoin(offer.getAmount());
        if (decimalAligned) {
            formattedAmount = fillUpPlacesWithEmptyStrings(formattedAmount, 15);
        }
        return formattedAmount;
    }

    public String formatAmount(Offer offer, int decimalPlaces, boolean decimalAligned, int maxPlaces) {
        String formattedAmount = offer.isRange() ? formatCoin(offer.getMinAmount(), decimalPlaces) + RANGE_SEPARATOR + formatCoin(offer.getAmount(), decimalPlaces) : formatCoin(offer.getAmount(), decimalPlaces);

        if (decimalAligned) {
            formattedAmount = fillUpPlacesWithEmptyStrings(formattedAmount, maxPlaces);
        }
        return formattedAmount;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Price
    ///////////////////////////////////////////////////////////////////////////////////////////


    public String formatPrice(Price price, MonetaryFormat fiatPriceFormat, boolean appendCurrencyCode) {
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

    public String formatPrice(Price price) {
        return formatPrice(price, fiatPriceFormat, false);
    }

    public String formatPrice(Price price, Boolean decimalAligned, int maxPlaces) {
        String formattedPrice = formatPrice(price);

        if (decimalAligned) {
            formattedPrice = fillUpPlacesWithEmptyStrings(formattedPrice, maxPlaces);
        }
        return formattedPrice;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Market price
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String formatMarketPrice(double price, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return formatMarketPrice(price, 2);
        else
            return formatMarketPrice(price, 8);
    }

    public String formatMarketPrice(double price, int precision) {
        return formatRoundedDoubleWithPrecision(price, precision);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Other
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String formatRoundedDoubleWithPrecision(double value, int precision) {
        decimalFormat.setMinimumFractionDigits(precision);
        decimalFormat.setMaximumFractionDigits(precision);
        return decimalFormat.format(MathUtils.roundDouble(value, precision)).replace(",", ".");
    }

    public String getDirectionWithCode(OfferPayload.Direction direction, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return (direction == OfferPayload.Direction.BUY) ? Res.get("shared.buyCurrency", Res.getBaseCurrencyCode()) : Res.get("shared.sellCurrency", Res.getBaseCurrencyCode());
        else
            return (direction == OfferPayload.Direction.SELL) ? Res.get("shared.buyCurrency", currencyCode) : Res.get("shared.sellCurrency", currencyCode);
    }

    public String getDirectionWithCodeDetailed(OfferPayload.Direction direction, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return (direction == OfferPayload.Direction.BUY) ? Res.get("shared.buyingBTCWith", currencyCode) : Res.get("shared.sellingBTCFor", currencyCode);
        else
            return (direction == OfferPayload.Direction.SELL) ? Res.get("shared.buyingCurrency", currencyCode) : Res.get("shared.sellingCurrency", currencyCode);
    }

    public String arbitratorAddressesToString(List<NodeAddress> nodeAddresses) {
        return nodeAddresses.stream().map(NodeAddress::getFullAddress).collect(Collectors.joining(", "));
    }

    public String languageCodesToString(List<String> languageLocales) {
        return languageLocales.stream().map(LanguageUtil::getDisplayName).collect(Collectors.joining(", "));
    }

    public String formatDateTime(Date date) {
        return formatDateTime(date,
                DateFormat.getDateInstance(DateFormat.DEFAULT, getLocale()),
                DateFormat.getTimeInstance(DateFormat.DEFAULT, getLocale()));
    }

    public String formatDateTime(Date date, DateFormat dateFormatter, DateFormat timeFormatter) {
        if (date != null) {
            return dateFormatter.format(date) + " " + timeFormatter.format(date);
        } else {
            return "";
        }
    }

    public String formatDateTimeSpan(Date dateFrom, Date dateTo) {
        if (dateFrom != null && dateTo != null) {
            DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, getLocale());
            DateFormat timeFormatter = DateFormat.getTimeInstance(DateFormat.DEFAULT, getLocale());
            return dateFormatter.format(dateFrom) + " " + timeFormatter.format(dateFrom) + RANGE_SEPARATOR + timeFormatter.format(dateTo);
        } else {
            return "";
        }
    }

    public String formatTime(Date date) {
        if (date != null) {
            DateFormat timeFormatter = DateFormat.getTimeInstance(DateFormat.DEFAULT, getLocale());
            return timeFormatter.format(date);
        } else {
            return "";
        }
    }

    public String formatDate(Date date) {
        if (date != null) {
            DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, getLocale());
            return dateFormatter.format(date);
        } else {
            return "";
        }
    }

    public String formatToPercentWithSymbol(double value) {
        return formatToPercent(value) + "%";
    }

    public String formatPercentagePrice(double value) {
        return formatToPercentWithSymbol(value);
    }

    public String formatToPercent(double value) {
        DecimalFormat decimalFormat = new DecimalFormat("#.##");
        decimalFormat.setMinimumFractionDigits(2);
        decimalFormat.setMaximumFractionDigits(2);
        return decimalFormat.format(MathUtils.roundDouble(value * 100.0, 2)).replace(",", ".");
    }

    public double parseNumberStringToDouble(String input) throws NumberFormatException {
        return Double.parseDouble(cleanDoubleInput(input));
    }

    public double parsePercentStringToDouble(String percentString) throws NumberFormatException {
        String input = percentString.replace("%", "");
        input = cleanDoubleInput(input);
        double value = Double.parseDouble(input);
        return value / 100d;
    }

    public long parsePriceStringToLong(String currencyCode, String amount, int precision) {
        if (amount == null || amount.isEmpty())
            return 0;

        long value = 0;
        try {
            double amountValue = Double.parseDouble(amount);
            amount = formatRoundedDoubleWithPrecision(amountValue, precision);
            value = Price.parse(currencyCode, amount).getValue();
        } catch (NumberFormatException ignore) {
            // expected NumberFormatException if input is not a number
        } catch (Throwable t) {
            log.error("parsePriceStringToLong: " + t.toString());
        }

        return value;
    }

    public static String convertCharsForNumber(String input) {
        // Some languages like finnish use the long dash for the minus
        input = input.replace("−", "-");
        input = StringUtils.deleteWhitespace(input);
        return input.replace(",", ".");
    }

    protected String cleanDoubleInput(String input) {
        input = convertCharsForNumber(input);
        if (input.equals("."))
            input = input.replace(".", "0.");
        if (input.equals("-."))
            input = input.replace("-.", "-0.");
        // don't use String.valueOf(Double.parseDouble(input)) as return value as it gives scientific
        // notation (1.0E-6) which screw up coinFormat.parse
        //noinspection ResultOfMethodCallIgnored
        // Just called to check if we have a valid double, throws exception otherwise
        //noinspection ResultOfMethodCallIgnored
        Double.parseDouble(input);
        return input;
    }

    public String formatAccountAge(long durationMillis) {
        durationMillis = Math.max(0, durationMillis);
        String day = Res.get("time.day").toLowerCase();
        String days = Res.get("time.days");
        String format = "d\' " + days + "\'";
        return StringUtils.replaceOnce(DurationFormatUtils.formatDuration(durationMillis, format), "1 " + days, "1 " + day);
    }

    public String formatDurationAsWords(long durationMillis) {
        return formatDurationAsWords(durationMillis, false, true);
    }

    public String formatDurationAsWords(long durationMillis, boolean showSeconds, boolean showZeroValues) {
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
        } else
            format += "H\' " + hours + ", \'m\' " + minutes + "\'";

        String duration = durationMillis > 0 ? DurationFormatUtils.formatDuration(durationMillis, format) : "";

        duration = StringUtils.replacePattern(duration, "^1 " + seconds + "|\\b1 " + seconds, "1 " + second);
        duration = StringUtils.replacePattern(duration, "^1 " + minutes + "|\\b1 " + minutes, "1 " + minute);
        duration = StringUtils.replacePattern(duration, "^1 " + hours + "|\\b1 " + hours, "1 " + hour);
        duration = StringUtils.replacePattern(duration, "^1 " + days + "|\\b1 " + days, "1 " + day);

        if (!showZeroValues) {
            duration = duration.replace(", 0 seconds", "");
            duration = duration.replace(", 0 minutes", "");
            duration = duration.replace(", 0 hours", "");
            duration = duration.replace("0 days", "");
            duration = duration.replace("0 hours, ", "");
            duration = duration.replace("0 minutes, ", "");
            duration = duration.replace("0 seconds", "");
        }
        return duration.trim();
    }

    public String booleanToYesNo(boolean value) {
        return value ? Res.get("shared.yes") : Res.get("shared.no");
    }

    public String getDirectionBothSides(OfferPayload.Direction direction, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            currencyCode = Res.getBaseCurrencyCode();
            return direction == OfferPayload.Direction.BUY ?
                    Res.get("formatter.makerTaker", currencyCode, Res.get("shared.buyer"), currencyCode, Res.get("shared.seller")) :
                    Res.get("formatter.makerTaker", currencyCode, Res.get("shared.seller"), currencyCode, Res.get("shared.buyer"));
        } else {
            return direction == OfferPayload.Direction.SELL ?
                    Res.get("formatter.makerTaker", currencyCode, Res.get("shared.buyer"), currencyCode, Res.get("shared.seller")) :
                    Res.get("formatter.makerTaker", currencyCode, Res.get("shared.seller"), currencyCode, Res.get("shared.buyer"));
        }
    }

    public String getDirectionForBuyer(boolean isMyOffer, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            String code = Res.getBaseCurrencyCode();
            return isMyOffer ?
                    Res.get("formatter.youAreAsMaker", Res.get("shared.buying"), code, Res.get("shared.selling"), code) :
                    Res.get("formatter.youAreAsTaker", Res.get("shared.buying"), code, Res.get("shared.selling"), code);
        } else {
            return isMyOffer ?
                    Res.get("formatter.youAreAsMaker", Res.get("shared.selling"), currencyCode, Res.get("shared.buying"), currencyCode) :
                    Res.get("formatter.youAreAsTaker", Res.get("shared.selling"), currencyCode, Res.get("shared.buying"), currencyCode);
        }
    }

    public String getDirectionForSeller(boolean isMyOffer, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            String code = Res.getBaseCurrencyCode();
            return isMyOffer ?
                    Res.get("formatter.youAreAsMaker", Res.get("shared.selling"), code, Res.get("shared.buying"), code) :
                    Res.get("formatter.youAreAsTaker", Res.get("shared.selling"), code, Res.get("shared.buying"), code);
        } else {
            return isMyOffer ?
                    Res.get("formatter.youAreAsMaker", Res.get("shared.buying"), currencyCode, Res.get("shared.selling"), currencyCode) :
                    Res.get("formatter.youAreAsTaker", Res.get("shared.buying"), currencyCode, Res.get("shared.selling"), currencyCode);
        }
    }

    public String getDirectionForTakeOffer(OfferPayload.Direction direction, String currencyCode) {
        String baseCurrencyCode = Res.getBaseCurrencyCode();
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            return direction == OfferPayload.Direction.BUY ?
                    Res.get("formatter.youAre", Res.get("shared.selling"), baseCurrencyCode, Res.get("shared.buying"), currencyCode) :
                    Res.get("formatter.youAre", Res.get("shared.buying"), baseCurrencyCode, Res.get("shared.selling"), currencyCode);
        } else {

            return direction == OfferPayload.Direction.SELL ?
                    Res.get("formatter.youAre", Res.get("shared.selling"), currencyCode, Res.get("shared.buying"), baseCurrencyCode) :
                    Res.get("formatter.youAre", Res.get("shared.buying"), currencyCode, Res.get("shared.selling"), baseCurrencyCode);
        }
    }

    public String getOfferDirectionForCreateOffer(OfferPayload.Direction direction, String currencyCode) {
        String baseCurrencyCode = Res.getBaseCurrencyCode();
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            return direction == OfferPayload.Direction.BUY ?
                    Res.get("formatter.youAreCreatingAnOffer.fiat", Res.get("shared.buy"), baseCurrencyCode) :
                    Res.get("formatter.youAreCreatingAnOffer.fiat", Res.get("shared.sell"), baseCurrencyCode);
        } else {
            return direction == OfferPayload.Direction.SELL ?
                    Res.get("formatter.youAreCreatingAnOffer.altcoin", Res.get("shared.buy"), currencyCode, Res.get("shared.selling"), baseCurrencyCode) :
                    Res.get("formatter.youAreCreatingAnOffer.altcoin", Res.get("shared.sell"), currencyCode, Res.get("shared.buying"), baseCurrencyCode);
        }
    }

    public String getRole(boolean isBuyerMakerAndSellerTaker, boolean isMaker, String currencyCode) {
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

    public String formatBytes(long bytes) {
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

    public String getCurrencyPair(String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return Res.getBaseCurrencyCode() + "/" + currencyCode;
        else
            return currencyCode + "/" + Res.getBaseCurrencyCode();
    }

    public String getCounterCurrency(String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return currencyCode;
        else
            return Res.getBaseCurrencyCode();
    }

    public String getBaseCurrency(String currencyCode) {
        if (CurrencyUtil.isCryptoCurrency(currencyCode))
            return currencyCode;
        else
            return Res.getBaseCurrencyCode();
    }

    public String getCounterCurrencyAndCurrencyPair(String currencyCode) {
        return getCounterCurrency(currencyCode) + " (" + getCurrencyPair(currencyCode) + ")";
    }

    public String getCurrencyNameAndCurrencyPair(String currencyCode) {
        return CurrencyUtil.getNameByCode(currencyCode) + " (" + getCurrencyPair(currencyCode) + ")";
    }

    public String getPriceWithCurrencyCode(String currencyCode) {
        return getPriceWithCurrencyCode(currencyCode, "shared.priceInCurForCur");
    }

    public String getPriceWithCurrencyCode(String currencyCode, String translationKey) {
        if (CurrencyUtil.isCryptoCurrency(currencyCode))
            return Res.get(translationKey, Res.getBaseCurrencyCode(), currencyCode);
        else
            return Res.get(translationKey, currencyCode, Res.getBaseCurrencyCode());
    }

    public Locale getLocale() {
        return GlobalSettings.getLocale();
    }
}
