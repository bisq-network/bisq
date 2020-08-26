package bisq.cli;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.Locale;

class CurrencyFormat {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.US);

    static final BigDecimal SATOSHI_DIVISOR = new BigDecimal(100000000);
    static final DecimalFormat BTC_FORMAT = new DecimalFormat("###,##0.00000000");

    @SuppressWarnings("BigDecimalMethodWithoutRoundingCalled")
    static String formatSatoshis(long sats) {
        return BTC_FORMAT.format(BigDecimal.valueOf(sats).divide(SATOSHI_DIVISOR));
    }

    static String formatAmountRange(long minAmount, long amount) {
        return minAmount != amount
                ? formatSatoshis(minAmount) + " - " + formatSatoshis(amount)
                : formatSatoshis(amount);
    }

    static String formatVolumeRange(long minVolume, long volume) {
        return minVolume != volume
                ? formatOfferVolume(minVolume) + " - " + formatOfferVolume(volume)
                : formatOfferVolume(volume);
    }

    static String formatOfferPrice(long price) {
        NUMBER_FORMAT.setMaximumFractionDigits(4);
        NUMBER_FORMAT.setMinimumFractionDigits(4);
        NUMBER_FORMAT.setRoundingMode(RoundingMode.UNNECESSARY);
        return NUMBER_FORMAT.format((double) price / 10000);
    }

    static String formatOfferVolume(long volume) {
        NUMBER_FORMAT.setMaximumFractionDigits(0);
        NUMBER_FORMAT.setRoundingMode(RoundingMode.UNNECESSARY);
        return NUMBER_FORMAT.format((double) volume / 10000);
    }
}
