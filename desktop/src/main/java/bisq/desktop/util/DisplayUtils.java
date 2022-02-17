package bisq.desktop.util;

import bisq.core.account.witness.AccountAgeWitness;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.util.FormattingUtils;
import bisq.core.offer.OfferDirection;
import bisq.core.payment.PaymentAccount;
import bisq.core.util.ParsingUtils;
import bisq.core.util.VolumeUtil;
import bisq.core.util.coin.CoinFormatter;

import bisq.common.crypto.PubKeyRing;

import org.bitcoinj.core.Coin;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DurationFormatUtils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.Date;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DisplayUtils {
    private static final int SCALE = 3;

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

    public static String formatDateAxis(Date date, String format) {
        if (date != null) {
            SimpleDateFormat dateFormatter = new SimpleDateFormat(format, GlobalSettings.getLocale());
            return dateFormatter.format(date);
        } else {
            return "";
        }
    }

    public static String getAccountWitnessDescription(AccountAgeWitnessService accountAgeWitnessService,
                                               PaymentMethod paymentMethod,
                                               PaymentAccountPayload paymentAccountPayload,
                                               PubKeyRing pubKeyRing) {
        String description = Res.get("peerInfoIcon.tooltip.unknownAge");
        Optional<AccountAgeWitness> aaw = accountAgeWitnessService.findWitness(paymentAccountPayload, pubKeyRing);
        if (aaw.isPresent()) {
            long accountAge = accountAgeWitnessService.getAccountAge(aaw.get(), new Date());
            long signAge = -1L;
            if (PaymentMethod.hasChargebackRisk(paymentMethod)) {
                signAge = accountAgeWitnessService.getWitnessSignAge(aaw.get(), new Date());
            }
            if (signAge > -1) {
                description = Res.get("peerInfo.age.chargeBackRisk") + ": " + formatAccountAge(accountAge);
            } else if (accountAge > -1) {
                description = Res.get("peerInfoIcon.tooltip.age", formatAccountAge(accountAge));
                if (PaymentMethod.hasChargebackRisk(paymentMethod)) {
                    description += ", " + Res.get("offerbook.timeSinceSigning.notSigned");
                }
            }
        }
        return description;
    }

    public static String formatAccountAge(long durationMillis) {
        durationMillis = Math.max(0, durationMillis);
        String day = Res.get("time.day").toLowerCase();
        String days = Res.get("time.days");
        String format = " d' " + days + "'";
        return StringUtils.strip(StringUtils.replaceOnce(DurationFormatUtils.formatDuration(durationMillis, format), " 1 " + days, " 1 " + day));
    }

    public static String booleanToYesNo(boolean value) {
        return value ? Res.get("shared.yes") : Res.get("shared.no");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Offer direction
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String getDirectionWithCode(OfferDirection direction, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode))
            return (direction == OfferDirection.BUY) ? Res.get("shared.buyCurrency", Res.getBaseCurrencyCode()) : Res.get("shared.sellCurrency", Res.getBaseCurrencyCode());
        else
            return (direction == OfferDirection.SELL) ? Res.get("shared.buyCurrency", currencyCode) : Res.get("shared.sellCurrency", currencyCode);
    }

    public static String getDirectionBothSides(OfferDirection direction) {
        String currencyCode = Res.getBaseCurrencyCode();
        return direction == OfferDirection.BUY ?
                Res.get("formatter.makerTaker", currencyCode, Res.get("shared.buyer"), currencyCode, Res.get("shared.seller")) :
                Res.get("formatter.makerTaker", currencyCode, Res.get("shared.seller"), currencyCode, Res.get("shared.buyer"));
    }

    public static String getDirectionForBuyer(boolean isMyOffer, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            String code = Res.getBaseCurrencyCode();
            return isMyOffer ?
                    Res.get("formatter.youAreAsMaker", Res.get("shared.buyer"), code, Res.get("shared.seller"), code) :
                    Res.get("formatter.youAreAsTaker", Res.get("shared.buyer"), code, Res.get("shared.seller"), code);
        } else {
            return isMyOffer ?
                    Res.get("formatter.youAreAsMaker", Res.get("shared.seller"), currencyCode, Res.get("shared.buyer"), currencyCode) :
                    Res.get("formatter.youAreAsTaker", Res.get("shared.seller"), currencyCode, Res.get("shared.buyer"), currencyCode);
        }
    }

    public static String getDirectionForSeller(boolean isMyOffer, String currencyCode) {
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            String code = Res.getBaseCurrencyCode();
            return isMyOffer ?
                    Res.get("formatter.youAreAsMaker", Res.get("shared.seller"), code, Res.get("shared.buyer"), code) :
                    Res.get("formatter.youAreAsTaker", Res.get("shared.seller"), code, Res.get("shared.buyer"), code);
        } else {
            return isMyOffer ?
                    Res.get("formatter.youAreAsMaker", Res.get("shared.buyer"), currencyCode, Res.get("shared.seller"), currencyCode) :
                    Res.get("formatter.youAreAsTaker", Res.get("shared.buyer"), currencyCode, Res.get("shared.seller"), currencyCode);
        }
    }

    public static String getDirectionForTakeOffer(OfferDirection direction, String currencyCode) {
        String baseCurrencyCode = Res.getBaseCurrencyCode();
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            return direction == OfferDirection.BUY ?
                    Res.get("formatter.youAre", Res.get("shared.selling"), baseCurrencyCode, Res.get("shared.buying"), currencyCode) :
                    Res.get("formatter.youAre", Res.get("shared.buying"), baseCurrencyCode, Res.get("shared.selling"), currencyCode);
        } else {

            return direction == OfferDirection.SELL ?
                    Res.get("formatter.youAre", Res.get("shared.selling"), currencyCode, Res.get("shared.buying"), baseCurrencyCode) :
                    Res.get("formatter.youAre", Res.get("shared.buying"), currencyCode, Res.get("shared.selling"), baseCurrencyCode);
        }
    }

    public static String getOfferDirectionForCreateOffer(OfferDirection direction, String currencyCode) {
        String baseCurrencyCode = Res.getBaseCurrencyCode();
        if (CurrencyUtil.isFiatCurrency(currencyCode)) {
            return direction == OfferDirection.BUY ?
                    Res.get("formatter.youAreCreatingAnOffer.fiat", Res.get("shared.buy"), baseCurrencyCode) :
                    Res.get("formatter.youAreCreatingAnOffer.fiat", Res.get("shared.sell"), baseCurrencyCode);
        } else {
            return direction == OfferDirection.SELL ?
                    Res.get("formatter.youAreCreatingAnOffer.altcoin", Res.get("shared.buy"), currencyCode, Res.get("shared.selling"), baseCurrencyCode) :
                    Res.get("formatter.youAreCreatingAnOffer.altcoin", Res.get("shared.sell"), currencyCode, Res.get("shared.buying"), baseCurrencyCode);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Amount
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static String formatAmount(Offer offer, CoinFormatter coinFormatter) {
        return offer.isRange()
                ? coinFormatter.formatCoin(offer.getMinAmount()) + FormattingUtils.RANGE_SEPARATOR + coinFormatter.formatCoin(offer.getAmount())
                : coinFormatter.formatCoin(offer.getAmount());
    }

    public static String formatAmount(Offer offer,
                                      int decimalPlaces,
                                      boolean decimalAligned,
                                      int maxPlaces,
                                      CoinFormatter coinFormatter) {
        String formattedAmount = offer.isRange()
                ? coinFormatter.formatCoin(offer.getMinAmount(), decimalPlaces) + FormattingUtils.RANGE_SEPARATOR + coinFormatter.formatCoin(offer.getAmount(), decimalPlaces)
                : coinFormatter.formatCoin(offer.getAmount(), decimalPlaces);

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
        String feeInBtc = makerFeeAsCoin != null ? formatter.formatCoinWithCode(makerFeeAsCoin) : Res.get("shared.na");
        if (optionalFeeInFiat != null && optionalFeeInFiat.isPresent()) {
            String feeInFiat = VolumeUtil.formatAverageVolumeWithCode(optionalFeeInFiat.get());
            return Res.get("feeOptionWindow.fee", feeInBtc, feeInFiat);
        } else {
            return feeInBtc;
        }
    }


    /**
     * Converts to a coin with max. 4 decimal places. Last place gets rounded.
     * <p>0.01234 -> 0.0123
     * <p>0.01235 -> 0.0124
     *
     * @param input         the decimal coin value to parse and round
     * @param coinFormatter the coin formatter instance
     * @return the converted coin
     */
    public static Coin parseToCoinWith4Decimals(String input, CoinFormatter coinFormatter) {
        try {
            return Coin.valueOf(
                    new BigDecimal(ParsingUtils.parseToCoin(ParsingUtils.cleanDoubleInput(input), coinFormatter).value)
                            .setScale(-SCALE - 1, RoundingMode.HALF_UP)
                            .setScale(SCALE + 1, RoundingMode.HALF_UP)
                            .toBigInteger().longValue()
            );
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
     * @param coin          the coin which should be transformed
     * @param coinFormatter the coin formatter instance
     * @return the transformed coin
     */
    public static Coin reduceTo4Decimals(Coin coin, CoinFormatter coinFormatter) {
        return ParsingUtils.parseToCoin(coinFormatter.formatCoin(coin), coinFormatter);
    }

    public static String createAccountName(String paymentMethodId, String name) {
        name = name.trim();
        name = StringUtils.abbreviate(name, 9);
        String method = Res.get(paymentMethodId);
        return method.concat(": ").concat(name);
    }

    public static String createAssetsAccountName(PaymentAccount paymentAccount, String address) {
        String currency = paymentAccount.getSingleTradeCurrency() != null ? paymentAccount.getSingleTradeCurrency().getCode() : "";
        return createAssetsAccountName(currency, address);
    }

    public static String createAssetsAccountName(String currency, String address) {
        address = StringUtils.abbreviate(address, 9);
        return currency.concat(": ").concat(address);
    }
}
