package bisq.desktop.util;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.FormattingUtils;
import bisq.core.util.ParsingUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.text.DateFormat;

import java.math.BigDecimal;

import java.util.Date;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DisplayUtils {
    private final static int scale = 3;
    private static final MonetaryFormat fiatVolumeFormat = new MonetaryFormat().shift(0).minDecimals(2).repeatOptionalDecimals(0, 0);

    public static String formatDateTime(Date date) {
        return FormattingUtils.formatDateTime(date, true);
    }

    public static String formatDateTimeSpan(Date dateFrom, Date dateTo) {
        if (dateFrom != null && dateTo != null) {
            DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, GlobalSettings.getLocale());
            DateFormat timeFormatter = DateFormat.getTimeInstance(DateFormat.DEFAULT, GlobalSettings.getLocale());
            return dateFormatter.format(dateFrom) + " " + timeFormatter.format(dateFrom) + FormattingUtils.RANGE_SEPARATOR + timeFormatter.format(dateTo);
        } else {
            return "";
        }
    }

    public static String formatTime(Date date) {
        if (date != null) {
            DateFormat timeFormatter = DateFormat.getTimeInstance(DateFormat.DEFAULT, GlobalSettings.getLocale());
            return timeFormatter.format(date);
        } else {
            return "";
        }
    }

    public static String formatDate(Date date) {
        if (date != null) {
            DateFormat dateFormatter = DateFormat.getDateInstance(DateFormat.DEFAULT, GlobalSettings.getLocale());
            return dateFormatter.format(date);
        } else {
            return "";
        }
    }

    public static String formatAccountAge(long durationMillis) {
        durationMillis = Math.max(0, durationMillis);
        String day = Res.get("time.day").toLowerCase();
        String days = Res.get("time.days");
        String format = " d\' " + days + "\'";
        return StringUtils.strip(StringUtils.replaceOnce(DurationFormatUtils.formatDuration(durationMillis, format), " 1 " + days, " 1 " + day));
    }

    public static String booleanToYesNo(boolean value) {
        return value ? Res.get("shared.yes") : Res.get("shared.no");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Volume
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatVolume(Offer offer, Boolean decimalAligned, int maxNumberOfDigits) {
        return formatVolume(offer, decimalAligned, maxNumberOfDigits, true);
    }

    public static String formatVolume(Offer offer, Boolean decimalAligned, int maxNumberOfDigits, boolean showRange) {
        String formattedVolume = offer.isRange() && showRange ? formatVolume(offer.getMinVolume()) + FormattingUtils.RANGE_SEPARATOR + formatVolume(offer.getVolume()) : formatVolume(offer.getVolume());

        if (decimalAligned) {
            formattedVolume = FormattingUtils.fillUpPlacesWithEmptyStrings(formattedVolume, maxNumberOfDigits);
        }
        return formattedVolume;
    }

    public static String formatVolume(Volume volume) {
        return formatVolume(volume, fiatVolumeFormat, false);
    }

    private static String formatVolume(Volume volume, MonetaryFormat fiatVolumeFormat, boolean appendCurrencyCode) {
        if (volume != null) {
            Monetary monetary = volume.getMonetary();
            if (monetary instanceof Fiat)
                return FormattingUtils.formatFiat((Fiat) monetary, fiatVolumeFormat, appendCurrencyCode);
            else
                return FormattingUtils.formatAltcoinVolume((Altcoin) monetary, appendCurrencyCode);
        } else {
            return "";
        }
    }

    public static String formatVolumeWithCode(Volume volume) {
        return formatVolume(volume, fiatVolumeFormat, true);
    }

    public static String formatVolumeLabel(String currencyCode) {
        return formatVolumeLabel(currencyCode, "");
    }

    public static String formatVolumeLabel(String currencyCode, String postFix) {
        return Res.get("formatter.formatVolumeLabel",
                currencyCode, postFix);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Offer direction
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String getDirectionWithCode(OfferPayload.Direction direction, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return (direction == OfferPayload.Direction.BUY) ? Res.get("shared.buyCurrency", Res.getBaseCurrencyCode()) : Res.get("shared.sellCurrency", Res.getBaseCurrencyCode());
        else
            return (direction == OfferPayload.Direction.SELL) ? Res.get("shared.buyCurrency", currencyCode) : Res.get("shared.sellCurrency", currencyCode);
    }

    public static String getDirectionBothSides(OfferPayload.Direction direction, String currencyCode) {
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

    public static String getDirectionForBuyer(boolean isMyOffer, String currencyCode) {
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

    public static String getDirectionForSeller(boolean isMyOffer, String currencyCode) {
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

    public static String getDirectionForTakeOffer(OfferPayload.Direction direction, String currencyCode) {
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

    public static String getOfferDirectionForCreateOffer(OfferPayload.Direction direction, String currencyCode) {
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Amount
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatAmount(Offer offer, CoinFormatter formatter) {
        return formatAmount(offer, false, formatter);
    }

    private static String formatAmount(Offer offer, boolean decimalAligned, CoinFormatter coinFormatter) {
        String formattedAmount = offer.isRange() ? coinFormatter.formatCoin(offer.getMinAmount()) + FormattingUtils.RANGE_SEPARATOR + coinFormatter.formatCoin(offer.getAmount()) : coinFormatter.formatCoin(offer.getAmount());
        if (decimalAligned) {
            formattedAmount = FormattingUtils.fillUpPlacesWithEmptyStrings(formattedAmount, 15);
        }
        return formattedAmount;
    }

    public static String formatAmount(Offer offer,
                                      int decimalPlaces,
                                      boolean decimalAligned,
                                      int maxPlaces,
                                      CoinFormatter coinFormatter) {
        String formattedAmount = offer.isRange() ? coinFormatter.formatCoin(offer.getMinAmount(), decimalPlaces) + FormattingUtils.RANGE_SEPARATOR + coinFormatter.formatCoin(offer.getAmount(), decimalPlaces) : coinFormatter.formatCoin(offer.getAmount(), decimalPlaces);

        if (decimalAligned) {
            formattedAmount = FormattingUtils.fillUpPlacesWithEmptyStrings(formattedAmount, maxPlaces);
        }
        return formattedAmount;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Other
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatPrice(Price price, Boolean decimalAligned, int maxPlaces) {
        String formattedPrice = FormattingUtils.formatPrice(price);

        if (decimalAligned) {
            formattedPrice = FormattingUtils.fillUpPlacesWithEmptyStrings(formattedPrice, maxPlaces);
        }
        return formattedPrice;
    }

    public static String getFeeWithFiatAmount(Coin makerFeeAsCoin,
                                              Optional<Volume> optionalFeeInFiat,
                                              CoinFormatter formatter) {
        String fee = makerFeeAsCoin != null ? formatter.formatCoinWithCode(makerFeeAsCoin) : Res.get("shared.na");
        String feeInFiatAsString;
        if (optionalFeeInFiat != null && optionalFeeInFiat.isPresent()) {
            feeInFiatAsString = formatVolumeWithCode(optionalFeeInFiat.get());
        } else {
            feeInFiatAsString = Res.get("shared.na");
        }
        return Res.get("feeOptionWindow.fee", fee, feeInFiatAsString);
    }

    /**
     * Converts to a coin with max. 4 decimal places. Last place gets rounded.
     * 0.01234 -> 0.0123
     * 0.01235 -> 0.0124
     *
     * @param input
     * @param coinFormatter
     * @return
     */
    public static Coin parseToCoinWith4Decimals(String input, CoinFormatter coinFormatter) {
        try {
            return Coin.valueOf(new BigDecimal(ParsingUtils.parseToCoin(ParsingUtils.cleanDoubleInput(input), coinFormatter).value).setScale(-scale - 1,
                    BigDecimal.ROUND_HALF_UP).setScale(scale + 1, BigDecimal.ROUND_HALF_UP).toBigInteger().longValue());
        } catch (Throwable t) {
            if (input != null && input.length() > 0)
                log.warn("Exception at parseToCoinWith4Decimals: " + t.toString());
            return Coin.ZERO;
        }
    }

    public static boolean hasBtcValidDecimals(String input, CoinFormatter coinFormatter) {
        return ParsingUtils.parseToCoin(input, coinFormatter).equals(parseToCoinWith4Decimals(input, coinFormatter));
    }

    /**
     * Transform a coin with the properties defined in the format (used to reduce decimal places)
     *
     * @param coin The coin which should be transformed
     * @param coinFormatter
     * @return The transformed coin
     */
    public static Coin reduceTo4Decimals(Coin coin, CoinFormatter coinFormatter) {
        return ParsingUtils.parseToCoin(coinFormatter.formatCoin(coin), coinFormatter);
    }
}
