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

package bisq.core.trade.protocol.bsq_swap;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.monetary.Volume;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import bisq.common.util.MathUtils;

import org.bitcoinj.core.Coin;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * The fees can be paid either by adding them to the inputs or by reducing them from the outputs. As we want to avoid
 * extra inputs only needed for the fees (tx fee in case of buyer and trade fee in case of seller) we let
 * the buyer add the trade fee to the BSQ input and reduce the tx fee from the BTC output. For the seller its the
 * other way round.
 *
 *
 * The example numbers are:
 * BTC trade amount 100000000 sat (1 BTC)
 * BSQ trade amount: 5000000 sat (50000.00 BSQ)
 * Buyer trade fee: 50 sat (0.5 BSQ)
 * Seller trade fee: 150 sat (1.5 BSQ)
 * Buyer tx fee:  1950 sat (total tx fee would be 2000 but we subtract the 50 sat trade fee)
 * Seller tx fee:  1850 sat (total tx fee would be 2000 but we subtract the 150 sat trade fee)
 *
 * Input buyer: BSQ trade amount + buyer trade fee                                              5000000 + 50
 * Input seller: BTC trade amount + seller tx fee                                               100000000 + 1850
 * Output seller: BSQ trade amount - sellers trade fee                                          5000000 - 150
 * Output buyer:  BSQ change                                                                    0
 * Output buyer:  BTC trade amount - buyers tx fee                                              100000000 - 1950
 * Output seller:  BTC change                                                                   0
 * Tx fee: Buyer tx fee + seller tx fee + buyer trade fee + seller trade fee                    1950 + 1850 + 50 + 150
 */
@Slf4j
public class BsqSwapCalculation {

    // Buyer
    public static Coin getBuyersBsqInputValue(BsqSwapTrade trade, long buyerTradeFee) {
        return Coin.valueOf(trade.getBsqTradeAmount() + buyerTradeFee);
    }

    public static Coin getBuyersBtcPayoutValue(BsqSwapTrade trade, int buyerTxSize, long buyerTradeFee) {
        long buyerTxFee = getTxFee(trade, buyerTxSize, buyerTradeFee);
        return getBuyersBtcPayoutValue(trade, buyerTxFee);
    }

    private static Coin getBuyersBtcPayoutValue(BsqSwapTrade trade, long buyerTxFee) {
        return Coin.valueOf(trade.getAmount() - buyerTxFee);
    }

    // Seller
    public static Coin getSellersBtcInputValue(BsqSwapTrade trade, int sellersTxSize, long sellersTradeFee) {

        long sellerTxFee = getTxFee(trade, sellersTxSize, sellersTradeFee);
        return getSellersBtcInputValue(trade, sellerTxFee);
    }

    public static Coin getSellerBsqPayoutValue(BsqSwapTrade trade, long sellerTradeFee) {
        return Coin.valueOf(trade.getBsqTradeAmount() - sellerTradeFee);
    }

    private static Coin getSellersBtcInputValue(BsqSwapTrade trade, long sellerTxFee) {
        return Coin.valueOf(trade.getAmount() + sellerTxFee);
    }


    public static int getVBytesSize(TradeWalletService tradeWalletService,
                                    List<RawTransactionInput> inputs,
                                    long change) {
        int size = 10 / 2; // Half of base tx size
        size += inputs.stream()
                .map(rawInput -> tradeWalletService.getTransactionInput(null, new byte[]{}, rawInput))
                .mapToLong(transactionInput -> 41 + (transactionInput.hasWitness() ? 29 : 108))
                .sum();
        // Outputs: 31 (we only use segwit)
        size += change > 0 ? 2 * 31 : 31;
        return size;
    }

    public static Coin getBsqTradeAmount(Volume volume) {
        // We treat BSQ as altcoin with smallest unit exponent 8 but we use 2 instead.
        // To avoid a larger refactoring of the monetary domain we just hack in the conversion here
        // by removing the last 6 digits.
        return Coin.valueOf(MathUtils.roundDoubleToLong(MathUtils.scaleDownByPowerOf10(volume.getValue(), 6)));
    }

    private static long getTxFee(BsqSwapTrade trade, int vBytes, long tradeFee) {
        return trade.getTxFeePerVbyte() * vBytes - tradeFee;
    }
}
