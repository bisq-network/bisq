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

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

public class CoinUtil {

    public static Coin getFeePerBtc(Coin feePerBtc, Coin amount) {
        double feePerBtcAsDouble = (double) feePerBtc.value;
        double amountAsDouble = (double) amount.value;
        double btcAsDouble = (double) Coin.COIN.value;
        return Coin.valueOf(Math.round(feePerBtcAsDouble * (amountAsDouble / btcAsDouble)));
    }

    public static Coin minCoin(Coin a, Coin b) {
        return a.compareTo(b) <= 0 ? a : b;
    }

    public static Coin maxCoin(Coin a, Coin b) {
        return a.compareTo(b) >= 0 ? a : b;
    }

    public static double getFeePerByte(Coin miningFee, int txSize) {
        return MathUtils.roundDouble(((double) miningFee.value / (double) txSize), 2);
    }

    /**
     * @param value Btc amount to be converted to percent value. E.g. 0.01 BTC is 1% (of 1 BTC)
     * @return The percentage value as double (e.g. 1% is 0.01)
     */
    public static double getAsPercentPerBtc(Coin value) {
        double asDouble = (double) value.value;
        double btcAsDouble = (double) Coin.COIN.value;
        return MathUtils.roundDouble(asDouble / btcAsDouble, 4);
    }

    /**
     * @param percent       The percentage value as double (e.g. 1% is 0.01)
     * @param amount        The amount as Coin for the percentage calculation
     * @return The percentage as Coin (e.g. 1% of 1 BTC is 0.01 BTC)
     */
    public static Coin getPercentOfAmountAsCoin(double percent, Coin amount) {
        double amountAsDouble = (double) amount.value;
        return Coin.valueOf(Math.round(percent * amountAsDouble));
    }
}
