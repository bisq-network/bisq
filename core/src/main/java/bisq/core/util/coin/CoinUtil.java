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

package bisq.core.util.coin;

import bisq.core.btc.wallet.Restrictions;
import bisq.core.monetary.Price;
import bisq.core.monetary.Volume;
import bisq.core.provider.fee.FeeService;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

import com.google.common.annotations.VisibleForTesting;

import javax.annotation.Nullable;

import static bisq.core.util.VolumeUtil.getAdjustedFiatVolume;
import static com.google.common.base.Preconditions.checkArgument;

public class CoinUtil {

    // Get the fee per amount
    public static Coin getFeePerBtc(Coin feePerBtc, Coin amount) {
        double feePerBtcAsDouble = feePerBtc != null ? (double) feePerBtc.value : 0;
        double amountAsDouble = amount != null ? (double) amount.value : 0;
        double btcAsDouble = (double) Coin.COIN.value;
        double fact = amountAsDouble / btcAsDouble;
        return Coin.valueOf(Math.round(feePerBtcAsDouble * fact));
    }

    public static Coin minCoin(Coin a, Coin b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static Coin maxCoin(Coin a, Coin b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static double getFeePerVbyte(Coin miningFee, int txVsize) {
        double value = miningFee != null ? miningFee.value : 0;
        return MathUtils.roundDouble((value / (double) txVsize), 2);
    }

    /**
     * @param value Btc amount to be converted to percent value. E.g. 0.01 BTC is 1% (of 1 BTC)
     * @return The percentage value as double (e.g. 1% is 0.01)
     */
    public static double getAsPercentPerBtc(Coin value) {
        return getAsPercentPerBtc(value, Coin.COIN);
    }

    /**
     * @param part Btc amount to be converted to percent value, based on total value passed.
     *              E.g. 0.1 BTC is 25% (of 0.4 BTC)
     * @param total Total Btc amount the percentage part is calculated from
     *
     * @return The percentage value as double (e.g. 1% is 0.01)
     */
    public static double getAsPercentPerBtc(Coin part, Coin total) {
        double asDouble = part != null ? (double) part.value : 0;
        double btcAsDouble = total != null ? (double) total.value : 1;
        return MathUtils.roundDouble(asDouble / btcAsDouble, 4);
    }

    /**
     * @param percent       The percentage value as double (e.g. 1% is 0.01)
     * @param amount        The amount as Coin for the percentage calculation
     * @return The percentage as Coin (e.g. 1% of 1 BTC is 0.01 BTC)
     */
    public static Coin getPercentOfAmountAsCoin(double percent, Coin amount) {
        double amountAsDouble = amount != null ? (double) amount.value : 0;
        return Coin.valueOf(Math.round(percent * amountAsDouble));
    }


    /**
     * Calculates the maker fee for the given amount, marketPrice and marketPriceMargin.
     *
     * @param isCurrencyForMakerFeeBtc {@code true} to pay fee in BTC, {@code false} to pay fee in BSQ
     * @param amount                   the amount of BTC to trade
     * @return the maker fee for the given trade amount, or {@code null} if the amount is {@code null}
     */
    @Nullable
    public static Coin getMakerFee(boolean isCurrencyForMakerFeeBtc, @Nullable Coin amount) {
        if (amount == null) {
            return null;
        }
        Coin feePerBtc = getFeePerBtc(FeeService.getMakerFeePerBtc(isCurrencyForMakerFeeBtc), amount);
        Coin minMakerFee = FeeService.getMinMakerFee(isCurrencyForMakerFeeBtc);
        return maxCoin(feePerBtc, minMakerFee);
    }

    @Nullable
    public static Coin getTakerFee(boolean isCurrencyForTakerFeeBtc, @Nullable Coin amount) {
        if (amount == null) {
            return null;
        }
        Coin feePerBtc = getFeePerBtc(FeeService.getTakerFeePerBtc(isCurrencyForTakerFeeBtc), amount);
        Coin minTakerFee = FeeService.getMinTakerFee(isCurrencyForTakerFeeBtc);
        return maxCoin(feePerBtc, minTakerFee);
    }

    /**
     * Calculate the possibly adjusted amount for {@code amount}, taking into account the
     * {@code price} and {@code maxTradeLimit} and {@code factor}.
     *
     * @param amount            Bitcoin amount which is a candidate for getting rounded.
     * @param price             Price used in relation to that amount.
     * @param maxTradeLimit     The max. trade limit of the users account, in satoshis.
     * @return The adjusted amount
     */
    public static Coin getRoundedFiatAmount(Coin amount, Price price, long maxTradeLimit) {
        return getAdjustedAmount(amount, price, maxTradeLimit, 1);
    }

    public static Coin getAdjustedAmountForHalCash(Coin amount, Price price, long maxTradeLimit) {
        return getAdjustedAmount(amount, price, maxTradeLimit, 10);
    }

    /**
     * Calculate the possibly adjusted amount for {@code amount}, taking into account the
     * {@code price} and {@code maxTradeLimit} and {@code factor}.
     *
     * @param amount            Bitcoin amount which is a candidate for getting rounded.
     * @param price             Price used in relation to that amount.
     * @param maxTradeLimit     The max. trade limit of the users account, in satoshis.
     * @param factor            The factor used for rounding. E.g. 1 means rounded to units of
     *                          1 EUR, 10 means rounded to 10 EUR, etc.
     * @return The adjusted amount
     */
    @VisibleForTesting
    static Coin getAdjustedAmount(Coin amount, Price price, long maxTradeLimit, int factor) {
        checkArgument(
                amount.getValue() >= 10_000,
                "amount needs to be above minimum of 10k satoshis"
        );
        checkArgument(
                factor > 0,
                "factor needs to be positive"
        );
        // Amount must result in a volume of min factor units of the fiat currency, e.g. 1 EUR or
        // 10 EUR in case of HalCash.
        Volume smallestUnitForVolume = Volume.parse(String.valueOf(factor), price.getCurrencyCode());
        if (smallestUnitForVolume.getValue() <= 0)
            return Coin.ZERO;

        Coin smallestUnitForAmount = price.getAmountByVolume(smallestUnitForVolume);
        long minTradeAmount = Restrictions.getMinTradeAmount().value;

        // We use 10 000 satoshi as min allowed amount
        checkArgument(
                minTradeAmount >= 10_000,
                "MinTradeAmount must be at least 10k satoshis"
        );
        smallestUnitForAmount = Coin.valueOf(Math.max(minTradeAmount, smallestUnitForAmount.value));
        // We don't allow smaller amount values than smallestUnitForAmount
        boolean useSmallestUnitForAmount = amount.compareTo(smallestUnitForAmount) < 0;

        // We get the adjusted volume from our amount
        Volume volume = useSmallestUnitForAmount
                ? getAdjustedFiatVolume(price.getVolumeByAmount(smallestUnitForAmount), factor)
                : getAdjustedFiatVolume(price.getVolumeByAmount(amount), factor);
        if (volume.getValue() <= 0)
            return Coin.ZERO;

        // From that adjusted volume we calculate back the amount. It might be a bit different as
        // the amount used as input before due rounding.
        Coin amountByVolume = price.getAmountByVolume(volume);

        // For the amount we allow only 4 decimal places
        long adjustedAmount = Math.round((double) amountByVolume.value / 10000d) * 10000;

        // If we are above our trade limit we reduce the amount by the smallestUnitForAmount
        while (adjustedAmount > maxTradeLimit) {
            adjustedAmount -= smallestUnitForAmount.value;
        }
        adjustedAmount = Math.max(minTradeAmount, adjustedAmount);
        adjustedAmount = Math.min(maxTradeLimit, adjustedAmount);
        return Coin.valueOf(adjustedAmount);
    }
}
