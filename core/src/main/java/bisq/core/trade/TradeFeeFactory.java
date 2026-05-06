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

package bisq.core.trade;

import bisq.core.provider.fee.FeeService;
import bisq.core.util.coin.CoinUtil;

import org.bitcoinj.core.Coin;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class TradeFeeFactory {
    // Average estimated values
    public static final int TAKER_FEE_TX_VSIZE = 192;
    public static final int DEPOSIT_TX_VSIZE = 233;

    // Taker pays miner fee for deposit transaction and payout transaction
    public static Coin getTradeTxFee(Coin txFeePerVbyte) {
        // We use the sum of the size of the trade fee and the deposit tx to get an average.
        // Miners will take the trade fee tx if the total fee of both dependent txs are good enough.
        int averageSize = (TAKER_FEE_TX_VSIZE + DEPOSIT_TX_VSIZE) / 2;
        return getMinerFeeByVsize(txFeePerVbyte, averageSize);
    }

    public static Coin getMinerFeeByVsize(Coin minerFeePerVbyte, int txVsize) {
        checkNotNull(minerFeePerVbyte, "minerFeePerVbyte must not be null");
        checkArgument(minerFeePerVbyte.isPositive(), "minerFeePerVbyte must be positive");
        checkArgument(txVsize > 0, "txVsize must be positive");
        return minerFeePerVbyte.multiply(txVsize);
    }

    public static Coin getMakerFee(boolean isCurrencyForMakerFeeBtc, Coin amount) {
        checkNotNull(amount, "amount must not be null");
        Coin makerFeePerBtc = FeeService.getMakerFeePerBtc(isCurrencyForMakerFeeBtc);
        Coin minMakerFee = FeeService.getMinMakerFee(isCurrencyForMakerFeeBtc);
        return getTradeFee(makerFeePerBtc, minMakerFee, amount);
    }

    public static Coin getTakerFee(boolean isCurrencyForTakerFeeBtc, Coin amount) {
        checkNotNull(amount, "amount must not be null");
        Coin takerFeePerBtc = FeeService.getTakerFeePerBtc(isCurrencyForTakerFeeBtc);
        Coin minTakerFee = FeeService.getMinTakerFee(isCurrencyForTakerFeeBtc);
        return getTradeFee(takerFeePerBtc, minTakerFee, amount);
    }

    private static Coin getTradeFee(Coin feePerBtc, Coin minFee, Coin amount) {
        checkNotNull(feePerBtc, "feePerBtc must not be null");
        checkNotNull(minFee, "minFee must not be null");
        checkNotNull(amount, "amount must not be null");
        checkArgument(feePerBtc.isPositive(), "feePerBtc must be positive");
        checkArgument(minFee.isPositive(), "minFee must be positive");
        checkArgument(amount.isPositive(), "amount must be positive");
        Coin fee = CoinUtil.getFeePerBtc(feePerBtc, amount);
        return CoinUtil.maxCoin(fee, minFee);
    }
}
