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

package bisq.cli;

import bisq.proto.grpc.TxFeeRateInfo;

import com.google.common.annotations.VisibleForTesting;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;

import java.math.BigDecimal;

import java.util.Locale;

import static java.lang.String.format;
import static java.math.RoundingMode.HALF_UP;
import static java.math.RoundingMode.UNNECESSARY;

@VisibleForTesting
public class CurrencyFormat {

    // Use the US locale for all DecimalFormat objects.
    private static final DecimalFormatSymbols DECIMAL_FORMAT_SYMBOLS = DecimalFormatSymbols.getInstance(Locale.US);

    // Format all numbers displayed in the CLI console with the US locale (no i18n support for CLI).
    private static final NumberFormat US_LOCALE_NUMBER_FORMAT = NumberFormat.getInstance(Locale.US);

    // Formats numbers for internal use, i.e., grpc request parameters.
    private static final DecimalFormat INTERNAL_FIAT_DECIMAL_FORMAT = new DecimalFormat("##############0.0000");

    static final BigDecimal SATOSHI_DIVISOR = new BigDecimal(100_000_000);
    static final DecimalFormat SATOSHI_FORMAT = new DecimalFormat("###,##0.00000000", DECIMAL_FORMAT_SYMBOLS);
    static final DecimalFormat BTC_FORMAT = new DecimalFormat("###,##0.########", DECIMAL_FORMAT_SYMBOLS);
    static final DecimalFormat BTC_TX_FEE_FORMAT = new DecimalFormat("###,###,##0", DECIMAL_FORMAT_SYMBOLS);

    static final BigDecimal BSQ_SATOSHI_DIVISOR = new BigDecimal(100);
    static final DecimalFormat BSQ_FORMAT = new DecimalFormat("###,###,###,##0.00", DECIMAL_FORMAT_SYMBOLS);

    static final DecimalFormat ALTCOIN_VOLUME_FORMAT = new DecimalFormat("########0.00", DECIMAL_FORMAT_SYMBOLS);
    static final DecimalFormat FIAT_VOLUME_FORMAT = new DecimalFormat("###########0", DECIMAL_FORMAT_SYMBOLS);

    static final BigDecimal SECURITY_DEPOSIT_MULTIPLICAND = new BigDecimal("0.01");

    @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
    public static String formatSatoshis(long sats) {
        return SATOSHI_FORMAT.format(BigDecimal.valueOf(sats).divide(SATOSHI_DIVISOR));
    }

    @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
    public static String formatBtc(long sats) {
        return BTC_FORMAT.format(BigDecimal.valueOf(sats).divide(SATOSHI_DIVISOR));
    }

    @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
    public static String formatBsq(long sats) {
        return BSQ_FORMAT.format(BigDecimal.valueOf(sats).divide(BSQ_SATOSHI_DIVISOR));
    }

    public static String formatVolumeString(String volumeString, int precision) {
        if (volumeString == null || volumeString.isBlank())
            return "";

        US_LOCALE_NUMBER_FORMAT.setMinimumFractionDigits(precision);
        US_LOCALE_NUMBER_FORMAT.setMaximumFractionDigits(precision);
        US_LOCALE_NUMBER_FORMAT.setRoundingMode(HALF_UP);
        var volAsDouble = new BigDecimal(volumeString).doubleValue();
        if (precision == 0)
            return FIAT_VOLUME_FORMAT.format(volAsDouble);
        else
            return ALTCOIN_VOLUME_FORMAT.format(volAsDouble);
    }

    public static String formatTxFeeRateInfo(TxFeeRateInfo txFeeRateInfo) {
        if (txFeeRateInfo.getUseCustomTxFeeRate())
            return format("custom tx fee rate: %s sats/byte, network rate: %s sats/byte, min network rate: %s sats/byte",
                    formatFeeSatoshis(txFeeRateInfo.getCustomTxFeeRate()),
                    formatFeeSatoshis(txFeeRateInfo.getFeeServiceRate()),
                    formatFeeSatoshis(txFeeRateInfo.getMinFeeServiceRate()));
        else
            return format("tx fee rate: %s sats/byte, min tx fee rate: %s sats/byte",
                    formatFeeSatoshis(txFeeRateInfo.getFeeServiceRate()),
                    formatFeeSatoshis(txFeeRateInfo.getMinFeeServiceRate()));
    }

    public static String formatInternalFiatPrice(BigDecimal price) {
        INTERNAL_FIAT_DECIMAL_FORMAT.setMinimumFractionDigits(4);
        INTERNAL_FIAT_DECIMAL_FORMAT.setMaximumFractionDigits(4);
        return INTERNAL_FIAT_DECIMAL_FORMAT.format(price);
    }

    public static String formatInternalFiatPrice(double price) {
        US_LOCALE_NUMBER_FORMAT.setMinimumFractionDigits(4);
        US_LOCALE_NUMBER_FORMAT.setMaximumFractionDigits(4);
        return US_LOCALE_NUMBER_FORMAT.format(price);
    }

    @Deprecated
    public static String formatPrice(long price) {
        US_LOCALE_NUMBER_FORMAT.setMinimumFractionDigits(4);
        US_LOCALE_NUMBER_FORMAT.setMaximumFractionDigits(4);
        US_LOCALE_NUMBER_FORMAT.setRoundingMode(UNNECESSARY);
        return US_LOCALE_NUMBER_FORMAT.format((double) price / 10_000);
    }

    public static String formatCryptoCurrencyPrice(long price) {
        US_LOCALE_NUMBER_FORMAT.setMinimumFractionDigits(8);
        US_LOCALE_NUMBER_FORMAT.setMaximumFractionDigits(8);
        US_LOCALE_NUMBER_FORMAT.setRoundingMode(UNNECESSARY);
        return US_LOCALE_NUMBER_FORMAT.format((double) price / SATOSHI_DIVISOR.doubleValue());
    }

    public static String formatFiatVolume(long volume) {
        US_LOCALE_NUMBER_FORMAT.setMinimumFractionDigits(0);
        US_LOCALE_NUMBER_FORMAT.setMaximumFractionDigits(0);
        US_LOCALE_NUMBER_FORMAT.setRoundingMode(HALF_UP);
        return US_LOCALE_NUMBER_FORMAT.format((double) volume / 10_000);
    }

    public static String formatCryptoCurrencyVolume(long volume) {
        int defaultPrecision = 2;
        return formatCryptoCurrencyVolume(volume, defaultPrecision);
    }

    public static String formatCryptoCurrencyVolume(long volume, int precision) {
        US_LOCALE_NUMBER_FORMAT.setMinimumFractionDigits(precision);
        US_LOCALE_NUMBER_FORMAT.setMaximumFractionDigits(precision);
        US_LOCALE_NUMBER_FORMAT.setRoundingMode(HALF_UP);
        return US_LOCALE_NUMBER_FORMAT.format((double) volume / SATOSHI_DIVISOR.doubleValue());
    }

    @Deprecated
    public static long toInternalFiatPrice(BigDecimal fiatPrice) {
        return fiatPrice.multiply(new BigDecimal(10_000)).longValue();
    }

    @Deprecated
    public static long toInternalCryptoCurrencyPrice(BigDecimal altcoinPrice) {
        return altcoinPrice.multiply(new BigDecimal(100_000_000)).longValue();
    }

    public static long toSatoshis(String btc) {
        if (btc.startsWith("-"))
            throw new IllegalArgumentException(format("'%s' is not a positive number", btc));

        try {
            return new BigDecimal(btc).multiply(SATOSHI_DIVISOR).longValue();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(format("'%s' is not a number", btc));
        }
    }

    public static double toSecurityDepositAsPct(String securityDepositInput) {
        try {
            return new BigDecimal(securityDepositInput)
                    .multiply(SECURITY_DEPOSIT_MULTIPLICAND).doubleValue();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(format("'%s' is not a number", securityDepositInput));
        }
    }

    public static String formatFeeSatoshis(long sats) {
        return BTC_TX_FEE_FORMAT.format(BigDecimal.valueOf(sats));
    }
}
