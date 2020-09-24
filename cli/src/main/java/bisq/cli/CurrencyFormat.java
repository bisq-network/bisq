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

import java.text.DecimalFormat;
import java.text.NumberFormat;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.Locale;

import static java.lang.String.format;

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

    static long toSatoshis(String btc) {
        if (btc.startsWith("-"))
            throw new IllegalArgumentException(format("'%s' is not a positive number", btc));

        try {
            return new BigDecimal(btc).multiply(SATOSHI_DIVISOR).longValue();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(format("'%s' is not a number", btc));
        }
    }
}
