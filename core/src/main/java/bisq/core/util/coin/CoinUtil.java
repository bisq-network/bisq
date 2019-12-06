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

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

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

    public static double getFeePerByte(Coin miningFee, int txSize) {
        double value = miningFee != null ? miningFee.value : 0;
        return MathUtils.roundDouble((value / (double) txSize), 2);
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
}
