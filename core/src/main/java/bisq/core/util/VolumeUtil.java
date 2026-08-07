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

import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Volume;
import bisq.core.offer.Offer;

import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.Fiat;
import org.bitcoinj.utils.MonetaryFormat;

import com.google.common.math.LongMath;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import java.util.Locale;

public class VolumeUtil {

    private static final MonetaryFormat FIAT_VOLUME_FORMAT = new MonetaryFormat().shift(0).minDecimals(0).repeatOptionalDecimals(0, 0);

    public static Volume getRoundedFiatVolume(Volume volumeByAmount) {
        // We want to get rounded to 1 unit of the fiat currency, e.g. 1 EUR.
        return getAdjustedFiatVolume(volumeByAmount, 1);
    }

    public static Volume getAdjustedVolumeForHalCash(Volume volumeByAmount) {
        // EUR has precision 4 and we want multiple of 10 so we divide by 100000 then
        // round and multiply with 10
        return getAdjustedFiatVolume(volumeByAmount, 10);
    }

    /**
     *
     * @param volumeByAmount      The volume generated from an amount
     * @param factor              The factor used for rounding. E.g. 1 means rounded to
     *                            units of 1 EUR, 10 means rounded to 10 EUR.
     * @return The adjusted Fiat volume
     */
    public static Volume getAdjustedFiatVolume(Volume volumeByAmount, int factor) {
        // Fiat currencies use precision 4 and we want multiple of factor so we divide by 10000 * factor then
        // round and multiply with factor
        long roundedVolume = Math.round((double) volumeByAmount.getValue() / (10000d * factor)) * factor;
        // Smallest allowed volume is factor (e.g. 10 EUR or 1 EUR,...)
        roundedVolume = Math.max(factor, roundedVolume);
        return Volume.parse(String.valueOf(roundedVolume), volumeByAmount.getCurrencyCode());
    }

    /**
     * Round an altcoin volume to the coin's own precision so the resulting amount can
     * actually be transferred on that coin's chain. Altcoin volumes are stored at
     * 10^-8 precision, but the coin may support fewer decimals; one on-chain unit is
     * 10^(8 - precision) internal units. Unlike {@link #getAdjustedFiatVolume} this does
     * NOT coarsen to whole coins - it rounds to the finest unit the coin can represent.
     *
     * @param volumeByAmount    The volume generated from an amount.
     * @param precision         The number of decimals the coin supports on its chain.
     * @return The adjusted altcoin volume.
     */
    public static Volume getAdjustedAltcoinVolume(Volume volumeByAmount, int precision) {
        int cappedPrecision = Math.min(precision, Altcoin.SMALLEST_UNIT_EXPONENT);
        long step = LongMath.pow(10, Altcoin.SMALLEST_UNIT_EXPONENT - cappedPrecision);
        long value = volumeByAmount.getValue();
        // Round to the nearest multiple of step (half up) using exact integer arithmetic. We
        // avoid floating point so large volumes stay bit-exact and precision 8 is a true no-op
        // (a long -> double cast would lose precision above 2^53).
        long roundedVolume = ((value + step / 2) / step) * step;
        // Never round down to zero: a volume below one on-chain unit (e.g. a fraction of a
        // whole coin on a 0-decimal coin) is raised to exactly one unit, so it stays sendable.
        roundedVolume = Math.max(step, roundedVolume);
        return new Volume(Altcoin.valueOf(volumeByAmount.getCurrencyCode(), roundedVolume));
    }


    public static String formatVolume(Offer offer, Boolean decimalAligned, int maxNumberOfDigits) {
        return formatVolume(offer, decimalAligned, maxNumberOfDigits, true);
    }

    public static String formatVolume(Offer offer, Boolean decimalAligned, int maxNumberOfDigits, boolean showRange) {
        String formattedVolume = offer.isRange() && showRange
                ? formatVolume(offer.getMinVolume()) + FormattingUtils.RANGE_SEPARATOR + formatVolume(offer.getVolume())
                : formatVolume(offer.getVolume());

        if (decimalAligned) {
            formattedVolume = FormattingUtils.fillUpPlacesWithEmptyStrings(formattedVolume, maxNumberOfDigits);
        }
        return formattedVolume;
    }

    public static String formatLargeFiat(double value, String currency) {
        if (value <= 0) {
            return "0";
        }
        NumberFormat numberFormat = DecimalFormat.getInstance(Locale.US);
        numberFormat.setGroupingUsed(true);
        return numberFormat.format(value) + " " + currency;
    }

    public static String formatLargeFiatWithUnitPostFix(double value, String currency) {
        if (value <= 0) {
            return "0";
        }
        String[] units = new String[]{"", "K", "M", "B"};
        int digitGroups = (int) (Math.log10(value) / Math.log10(1000));
        return new DecimalFormat("#,##0.###")
                .format(value / Math.pow(1000, digitGroups)) + units[digitGroups] + " " + currency;
    }

    public static String formatVolume(Volume volume) {
        return formatVolume(volume, FIAT_VOLUME_FORMAT, false);
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
        return formatVolume(volume, true);
    }

    public static String formatVolume(Volume volume, boolean appendCode) {
        return formatVolume(volume, FIAT_VOLUME_FORMAT, appendCode);
    }

    public static String formatAverageVolumeWithCode(Volume volume) {
        return formatVolume(volume, FIAT_VOLUME_FORMAT.minDecimals(2), true);
    }

    public static String formatVolumeLabel(String currencyCode) {
        return formatVolumeLabel(currencyCode, "");
    }

    public static String formatVolumeLabel(String currencyCode, String postFix) {
        return Res.get("formatter.formatVolumeLabel",
                currencyCode, postFix);
    }
}
