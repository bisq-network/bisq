package bisq.cli;

import bisq.proto.grpc.OfferInfo;

import com.google.common.annotations.VisibleForTesting;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static bisq.cli.ColumnHeaderConstants.*;
import static bisq.cli.CurrencyFormat.*;
import static bisq.cli.DirectionFormat.directionFormat;
import static bisq.cli.DirectionFormat.getLongestDirectionColWidth;
import static bisq.cli.TableFormat.formatTimestamp;
import static bisq.cli.TableFormat.getLongestColumnSize;
import static com.google.common.base.Strings.padEnd;
import static com.google.common.base.Strings.padStart;
import static java.lang.String.format;

@Deprecated
@VisibleForTesting
public class OfferFormat {

    public static String formatOfferTable(List<OfferInfo> offers, String currencyCode) {
        if (offers == null || offers.isEmpty())
            throw new IllegalArgumentException(format("%s offer list is empty", currencyCode.toLowerCase()));

        String baseCurrencyCode = offers.get(0).getBaseCurrencyCode();
        boolean isMyOffer = offers.get(0).getIsMyOffer();
        return baseCurrencyCode.equalsIgnoreCase("BTC")
                ? formatFiatOfferTable(offers, currencyCode, isMyOffer)
                : formatCryptoCurrencyOfferTable(offers, baseCurrencyCode, isMyOffer);
    }

    private static String formatFiatOfferTable(List<OfferInfo> offers,
                                               String fiatCurrencyCode,
                                               boolean isMyOffer) {
        // Some column values might be longer than header, so we need to calculate them.
        int amountColWith = getLongestAmountColWidth(offers);
        int volumeColWidth = getLongestVolumeColWidth(offers);
        int paymentMethodColWidth = getLongestPaymentMethodColWidth(offers);
        // "Enabled" and "Trigger Price" columns are displayed for my offers only.
        String enabledHeaderFormat = isMyOffer ?
                COL_HEADER_ENABLED + COL_HEADER_DELIMITER
                : "";
        String triggerPriceHeaderFormat = isMyOffer ?
                // COL_HEADER_TRIGGER_PRICE includes %s -> fiatCurrencyCode
                COL_HEADER_TRIGGER_PRICE + COL_HEADER_DELIMITER
                : "";
        String headersFormat = enabledHeaderFormat
                + COL_HEADER_DIRECTION + COL_HEADER_DELIMITER
                // COL_HEADER_PRICE includes %s -> fiatCurrencyCode
                + COL_HEADER_PRICE + COL_HEADER_DELIMITER
                + padStart(COL_HEADER_AMOUNT, amountColWith, ' ') + COL_HEADER_DELIMITER
                // COL_HEADER_VOLUME includes %s -> fiatCurrencyCode
                + padStart(COL_HEADER_VOLUME, volumeColWidth, ' ') + COL_HEADER_DELIMITER
                + triggerPriceHeaderFormat
                + padEnd(COL_HEADER_PAYMENT_METHOD, paymentMethodColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_CREATION_DATE + COL_HEADER_DELIMITER
                + COL_HEADER_UUID.trim() + "%n";
        String headerLine = format(headersFormat,
                fiatCurrencyCode.toUpperCase(),
                fiatCurrencyCode.toUpperCase(),
                // COL_HEADER_TRIGGER_PRICE includes %s -> fiatCurrencyCode
                isMyOffer ? fiatCurrencyCode.toUpperCase() : "");
        String colDataFormat = getFiatOfferColDataFormat(isMyOffer,
                amountColWith,
                volumeColWidth,
                paymentMethodColWidth);
        return formattedFiatOfferTable(offers, isMyOffer, headerLine, colDataFormat);
    }

    private static String formattedFiatOfferTable(List<OfferInfo> offers,
                                                  boolean isMyOffer,
                                                  String headerLine,
                                                  String colDataFormat) {
        if (isMyOffer) {
            return headerLine
                    + offers.stream()
                    .map(o -> format(colDataFormat,
                            formatEnabled(o),
                            o.getDirection(),
                            formatPrice(o.getPrice()),
                            formatAmountRange(o.getMinAmount(), o.getAmount()),
                            formatVolumeRange(o.getMinVolume(), o.getVolume()),
                            o.getTriggerPrice() == 0 ? "" : formatPrice(o.getTriggerPrice()),
                            o.getPaymentMethodShortName(),
                            formatTimestamp(o.getDate()),
                            o.getId()))
                    .collect(Collectors.joining("\n"));
        } else {
            return headerLine
                    + offers.stream()
                    .map(o -> format(colDataFormat,
                            o.getDirection(),
                            formatPrice(o.getPrice()),
                            formatAmountRange(o.getMinAmount(), o.getAmount()),
                            formatVolumeRange(o.getMinVolume(), o.getVolume()),
                            o.getPaymentMethodShortName(),
                            formatTimestamp(o.getDate()),
                            o.getId()))
                    .collect(Collectors.joining("\n"));
        }
    }

    private static String getFiatOfferColDataFormat(boolean isMyOffer,
                                                    int amountColWith,
                                                    int volumeColWidth,
                                                    int paymentMethodColWidth) {
        if (isMyOffer) {
            return "%-" + (COL_HEADER_ENABLED.length() + COL_HEADER_DELIMITER.length()) + "s"
                    + "%-" + (COL_HEADER_DIRECTION.length() + COL_HEADER_DELIMITER.length()) + "s"
                    + "%" + (COL_HEADER_PRICE.length() - 1) + "s"
                    + "  %" + amountColWith + "s"
                    + "  %" + (volumeColWidth - 1) + "s"
                    + "  %" + (COL_HEADER_TRIGGER_PRICE.length() - 1) + "s"
                    + "  %-" + paymentMethodColWidth + "s"
                    + "  %-" + (COL_HEADER_CREATION_DATE.length()) + "s"
                    + "  %-" + COL_HEADER_UUID.length() + "s";
        } else {
            return "%-" + (COL_HEADER_DIRECTION.length() + COL_HEADER_DELIMITER.length()) + "s"
                    + "%" + (COL_HEADER_PRICE.length() - 1) + "s"
                    + "  %" + amountColWith + "s"
                    + "  %" + (volumeColWidth - 1) + "s"
                    + "  %-" + paymentMethodColWidth + "s"
                    + "  %-" + (COL_HEADER_CREATION_DATE.length()) + "s"
                    + "  %-" + COL_HEADER_UUID.length() + "s";
        }
    }

    private static String formatCryptoCurrencyOfferTable(List<OfferInfo> offers,
                                                         String cryptoCurrencyCode,
                                                         boolean isMyOffer) {
        // Some column values might be longer than header, so we need to calculate them.
        int directionColWidth = getLongestDirectionColWidth(offers);
        int amountColWith = getLongestAmountColWidth(offers);
        int volumeColWidth = getLongestCryptoCurrencyVolumeColWidth(offers);
        int paymentMethodColWidth = getLongestPaymentMethodColWidth(offers);
        // "Enabled" column is displayed for my offers only.
        String enabledHeaderFormat = isMyOffer ?
                COL_HEADER_ENABLED + COL_HEADER_DELIMITER
                : "";
        Supplier<Boolean> shouldShowTriggerPrice = () -> isMyOffer && !cryptoCurrencyCode.equalsIgnoreCase("BSQ");
        String triggerPriceHeaderFormat = shouldShowTriggerPrice.get()
                ? COL_HEADER_TRIGGER_PRICE + COL_HEADER_DELIMITER
                : "";
        // TODO use memoize function to avoid duplicate the formatting done above?
        String headersFormat = enabledHeaderFormat
                + padEnd(COL_HEADER_DIRECTION, directionColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_PRICE_OF_ALTCOIN + COL_HEADER_DELIMITER   // includes %s -> cryptoCurrencyCode
                + padStart(COL_HEADER_AMOUNT, amountColWith, ' ') + COL_HEADER_DELIMITER
                // COL_HEADER_VOLUME  includes %s -> cryptoCurrencyCode
                + padStart(COL_HEADER_VOLUME, volumeColWidth, ' ') + COL_HEADER_DELIMITER
                + triggerPriceHeaderFormat  // COL_HEADER_TRIGGER_PRICE includes %s -> cryptoCurrencyCode
                + padEnd(COL_HEADER_PAYMENT_METHOD, paymentMethodColWidth, ' ') + COL_HEADER_DELIMITER
                + COL_HEADER_CREATION_DATE + COL_HEADER_DELIMITER
                + COL_HEADER_UUID.trim() + "%n";
        String headerLine = format(headersFormat,
                cryptoCurrencyCode.toUpperCase(),
                cryptoCurrencyCode.toUpperCase(),
                shouldShowTriggerPrice.get() ? cryptoCurrencyCode.toUpperCase() : "");
        String colDataFormat = getCryptoCurrencyOfferColDataFormat(isMyOffer,
                shouldShowTriggerPrice.get(),
                directionColWidth,
                amountColWith,
                volumeColWidth,
                paymentMethodColWidth);
        if (isMyOffer) {
            if (shouldShowTriggerPrice.get()) {
                // Is my non-BSQ altcoin offer.  Show ENABLED and TRIGGER_PRICE data.
                return headerLine
                        + offers.stream()
                        .map(o -> format(colDataFormat,
                                formatEnabled(o),
                                directionFormat.apply(o),
                                formatCryptoCurrencyPrice(o.getPrice()),
                                formatAmountRange(o.getMinAmount(), o.getAmount()),
                                formatCryptoCurrencyVolumeRange(o.getMinVolume(), o.getVolume()),
                                o.getTriggerPrice() == 0 ? "" : formatCryptoCurrencyPrice(o.getTriggerPrice()),
                                o.getPaymentMethodShortName(),
                                formatTimestamp(o.getDate()),
                                o.getId()))
                        .collect(Collectors.joining("\n"));
            } else {
                // Is my BSQ altcoin offer.  Show ENABLED, but not TRIGGER_PRICE data.
                return headerLine
                        + offers.stream()
                        .map(o -> format(colDataFormat,
                                formatEnabled(o),
                                directionFormat.apply(o),
                                formatCryptoCurrencyPrice(o.getPrice()),
                                formatAmountRange(o.getMinAmount(), o.getAmount()),
                                formatCryptoCurrencyVolumeRange(o.getMinVolume(), o.getVolume()),
                                o.getPaymentMethodShortName(),
                                formatTimestamp(o.getDate()),
                                o.getId()))
                        .collect(Collectors.joining("\n"));
            }
        } else {
            // Not my offer.  Do not show ENABLED and TRIGGER_PRICE cols.
            return headerLine
                    + offers.stream()
                    .map(o -> format(colDataFormat,
                            directionFormat.apply(o),
                            formatCryptoCurrencyPrice(o.getPrice()),
                            formatAmountRange(o.getMinAmount(), o.getAmount()),
                            formatCryptoCurrencyVolumeRange(o.getMinVolume(), o.getVolume()),
                            o.getPaymentMethodShortName(),
                            formatTimestamp(o.getDate()),
                            o.getId()))
                    .collect(Collectors.joining("\n"));
        }
    }

    private static String getCryptoCurrencyOfferColDataFormat(boolean isMyOffer,
                                                              boolean shouldShowTriggerPrice,
                                                              int directionColWidth,
                                                              int amountColWith,
                                                              int volumeColWidth,
                                                              int paymentMethodColWidth) {
        if (isMyOffer) {
            return getMyCryptoOfferColDataFormat(shouldShowTriggerPrice,
                    directionColWidth,
                    amountColWith,
                    volumeColWidth,
                    paymentMethodColWidth);
        } else {
            // Not my offer.  Do not show ENABLED and TRIGGER_PRICE cols.
            return "%-" + directionColWidth + "s"
                    + "%" + (COL_HEADER_PRICE_OF_ALTCOIN.length() + 1) + "s"
                    + "  %" + amountColWith + "s"
                    + "  %" + (volumeColWidth - 1) + "s"
                    + "  %-" + paymentMethodColWidth + "s"
                    + "  %-" + (COL_HEADER_CREATION_DATE.length()) + "s"
                    + "  %-" + COL_HEADER_UUID.length() + "s";
        }
    }

    private static String getMyCryptoOfferColDataFormat(boolean shouldShowTriggerPrice,
                                                        int directionColWidth,
                                                        int amountColWith,
                                                        int volumeColWidth,
                                                        int paymentMethodColWidth) {
        if (shouldShowTriggerPrice) {
            // Is my non-BSQ altcoin offer.  Show ENABLED and TRIGGER_PRICE cols.
            return "%-" + (COL_HEADER_ENABLED.length() + COL_HEADER_DELIMITER.length()) + "s"
                    + "%-" + directionColWidth + "s"
                    + "%" + (COL_HEADER_PRICE_OF_ALTCOIN.length() + 1) + "s"
                    + "  %" + amountColWith + "s"
                    + "  %" + (volumeColWidth - 1) + "s"
                    + "  %" + (COL_HEADER_TRIGGER_PRICE.length() - 1) + "s"
                    + "  %-" + paymentMethodColWidth + "s"
                    + "  %-" + (COL_HEADER_CREATION_DATE.length()) + "s"
                    + "  %-" + COL_HEADER_UUID.length() + "s";
        } else {
            // Is my BSQ altcoin offer.  Show ENABLED, but not TRIGGER_PRICE col.
            return "%-" + (COL_HEADER_ENABLED.length() + COL_HEADER_DELIMITER.length()) + "s"
                    + "%-" + directionColWidth + "s"
                    + "%" + (COL_HEADER_PRICE_OF_ALTCOIN.length() + 1) + "s"
                    + "  %" + amountColWith + "s"
                    + "  %" + (volumeColWidth - 1) + "s"
                    + "  %-" + paymentMethodColWidth + "s"
                    + "  %-" + (COL_HEADER_CREATION_DATE.length()) + "s"
                    + "  %-" + COL_HEADER_UUID.length() + "s";
        }
    }

    private static String formatEnabled(OfferInfo offerInfo) {
        if (offerInfo.getIsMyOffer() && offerInfo.getIsMyPendingOffer())
            return "PENDING";
        else
            return offerInfo.getIsActivated() ? "YES" : "NO";
    }

    private static int getLongestPaymentMethodColWidth(List<OfferInfo> offers) {
        return getLongestColumnSize(
                COL_HEADER_PAYMENT_METHOD.length(),
                offers.stream()
                        .map(OfferInfo::getPaymentMethodShortName)
                        .collect(Collectors.toList()));
    }

    private static int getLongestAmountColWidth(List<OfferInfo> offers) {
        return getLongestColumnSize(
                COL_HEADER_AMOUNT.length(),
                offers.stream()
                        .map(o -> formatAmountRange(o.getMinAmount(), o.getAmount()))
                        .collect(Collectors.toList()));
    }

    private static int getLongestVolumeColWidth(List<OfferInfo> offers) {
        // Pad this col width by 1 space.
        return 1 + getLongestColumnSize(
                COL_HEADER_VOLUME.length(),
                offers.stream()
                        .map(o -> formatVolumeRange(o.getMinVolume(), o.getVolume()))
                        .collect(Collectors.toList()));
    }

    private static int getLongestCryptoCurrencyVolumeColWidth(List<OfferInfo> offers) {
        // Pad this col width by 1 space.
        return 1 + getLongestColumnSize(
                COL_HEADER_VOLUME.length(),
                offers.stream()
                        .map(o -> formatCryptoCurrencyVolumeRange(o.getMinVolume(), o.getVolume()))
                        .collect(Collectors.toList()));
    }
}
