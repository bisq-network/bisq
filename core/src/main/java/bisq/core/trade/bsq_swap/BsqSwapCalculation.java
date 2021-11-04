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

package bisq.core.trade.bsq_swap;

import bisq.core.btc.exceptions.InsufficientBsqException;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.monetary.Volume;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import bisq.common.util.MathUtils;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

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
    private static final int MIN_SELLERS_TX_SIZE = 104;

    // Estimated size in case we do not have enough funds to calculate it from wallet inputs.
    // We use 3 non segwit inputs. 5 + 3*149 + 62 = 514
    public static final int ESTIMATED_V_BYTES = 514;

    // Buyer
    public static Coin getBuyersBsqInputValue(BsqSwapTrade trade, long buyersTradeFee) {
        return getBuyersBsqInputValue(trade.getBsqTradeAmount(), buyersTradeFee);
    }

    public static Coin getBuyersBsqInputValue(long bsqTradeAmount, long buyersTradeFee) {
        return Coin.valueOf(bsqTradeAmount + buyersTradeFee);
    }

    public static Coin getBuyersBtcPayoutValue(BsqSwapTrade trade, int buyersVBytesSize, long buyerTradeFee) {
        return getBuyersBtcPayoutValue(trade.getAmountAsLong(), trade.getTxFeePerVbyte(), buyersVBytesSize, buyerTradeFee);
    }

    public static Coin getBuyersBtcPayoutValue(long btcTradeAmount,
                                               long txFeePerVbyte,
                                               int buyersVBytesSize,
                                               long buyerTradeFee) {
        long buyersTxFee = getAdjustedTxFee(txFeePerVbyte, buyersVBytesSize, buyerTradeFee);
        return getBuyersBtcPayoutValue(btcTradeAmount, buyersTxFee);
    }

    public static Coin getBuyersBtcPayoutValue(BsqWalletService bsqWalletService,
                                               Coin bsqTradeAmount,
                                               Coin btcTradeAmount,
                                               long txFeePerVbyte,
                                               long buyerTradeFee) throws InsufficientBsqException {
        Tuple2<List<RawTransactionInput>, Coin> inputsAndChange = getBuyersBsqInputsAndChange(bsqWalletService, bsqTradeAmount.getValue(), buyerTradeFee);
        int buyersVBytesSize = BsqSwapCalculation.getVBytesSize(inputsAndChange.first, inputsAndChange.second.getValue());
        long buyersTxFee = getAdjustedTxFee(txFeePerVbyte, buyersVBytesSize, buyerTradeFee);
        return getBuyersBtcPayoutValue(btcTradeAmount.getValue(), buyersTxFee);
    }

    public static Tuple2<List<RawTransactionInput>, Coin> getBuyersBsqInputsAndChange(BsqWalletService bsqWalletService,
                                                                                      long amount,
                                                                                      long buyersTradeFee)
            throws InsufficientBsqException {
        Coin required = getBuyersBsqInputValue(amount, buyersTradeFee);
        return bsqWalletService.getBuyersBsqInputsForBsqSwapTx(required);
    }

    public static Coin getEstimatedBuyersBtcPayoutValue(Coin btcTradeAmount,
                                                        long txFeePerVbyte,
                                                        long buyerTradeFee) {
        // Use estimated size. This is used in case the wallet has not enough fund so we cannot calculate the exact
        // amount but we still want to provide some estimated value.
        long buyersTxFee = getAdjustedTxFee(txFeePerVbyte, ESTIMATED_V_BYTES, buyerTradeFee);
        return getBuyersBtcPayoutValue(btcTradeAmount.getValue(), buyersTxFee);
    }

    private static Coin getBuyersBtcPayoutValue(long btcTradeAmount, long buyerTxFee) {
        return Coin.valueOf(btcTradeAmount - buyerTxFee);
    }

    // Seller
    public static Coin getSellersBtcInputValue(BsqSwapTrade trade, int sellersTxSize, long sellersTradeFee) {
        return getSellersBtcInputValue(trade.getAmountAsLong(), trade.getTxFeePerVbyte(), sellersTxSize, sellersTradeFee);
    }

    public static Coin getSellersBtcInputValue(long btcTradeAmount,
                                               long txFeePerVbyte,
                                               int sellersVBytesSize,
                                               long sellersTradeFee) {
        long sellersTxFee = getAdjustedTxFee(txFeePerVbyte, sellersVBytesSize, sellersTradeFee);
        return getSellersBtcInputValue(btcTradeAmount, sellersTxFee);
    }

    public static Coin getSellersBtcInputValue(BtcWalletService btcWalletService,
                                               Coin btcTradeAmount,
                                               long txFeePerVbyte,
                                               long sellersTradeFee) throws InsufficientMoneyException {
        Tuple2<List<RawTransactionInput>, Coin> inputsAndChange = getSellersBtcInputsAndChange(btcWalletService,
                btcTradeAmount.getValue(),
                txFeePerVbyte,
                sellersTradeFee);
        int sellersVBytesSize = getVBytesSize(inputsAndChange.first, inputsAndChange.second.getValue());
        long sellersTxFee = getAdjustedTxFee(txFeePerVbyte, sellersVBytesSize, sellersTradeFee);
        return getSellersBtcInputValue(btcTradeAmount.getValue(), sellersTxFee);
    }

    public static Coin getEstimatedSellersBtcInputValue(Coin btcTradeAmount,
                                                        long txFeePerVbyte,
                                                        long sellersTradeFee) {
        // Use estimated size. This is used in case the wallet has not enough fund so we cannot calculate the exact
        // amount but we still want to provide some estimated value.
        long sellersTxFee = getAdjustedTxFee(txFeePerVbyte, ESTIMATED_V_BYTES, sellersTradeFee);
        return getSellersBtcInputValue(btcTradeAmount.getValue(), sellersTxFee);
    }

    public static Coin getSellersBtcInputValue(long btcTradeAmount, long sellerTxFee) {
        return Coin.valueOf(btcTradeAmount + sellerTxFee);
    }

    public static Coin getSellersBsqPayoutValue(BsqSwapTrade trade, long sellerTradeFee) {
        return getSellersBsqPayoutValue(trade.getBsqTradeAmount(), sellerTradeFee);
    }

    public static Coin getSellersBsqPayoutValue(long bsqTradeAmount, long sellerTradeFee) {
        return Coin.valueOf(bsqTradeAmount - sellerTradeFee);
    }

    // Tx fee estimation
    public static Tuple2<List<RawTransactionInput>, Coin> getSellersBtcInputsAndChange(BtcWalletService btcWalletService,
                                                                                       long amount,
                                                                                       long txFeePerVbyte,
                                                                                       long sellersTradeFee)
            throws InsufficientMoneyException {
        // Figure out how large out tx will be
        int iterations = 0;
        Tuple2<List<RawTransactionInput>, Coin> inputsAndChange;
        Coin previous = null;

        // At first we try with min. tx size
        int sellersTxSize = MIN_SELLERS_TX_SIZE;
        Coin change = Coin.ZERO;
        Coin required = getSellersBtcInputValue(amount, txFeePerVbyte, sellersTxSize, sellersTradeFee);

        // We do a first calculation here to get the size of the inputs (segwit or not) and we adjust the sellersTxSize
        // so that we avoid to get into dangling states.
        inputsAndChange = btcWalletService.getInputsAndChange(required);
        sellersTxSize = getVBytesSize(inputsAndChange.first, 0);
        required = getSellersBtcInputValue(amount, txFeePerVbyte, sellersTxSize, sellersTradeFee);

        // As fee calculation is not deterministic it could be that we toggle between a too small and too large
        // inputs. We would take the latest result before we break iteration. Worst case is that we under- or
        // overpay a bit. As fee rate is anyway an estimation we ignore that imperfection.
        while (iterations < 10 && !required.equals(previous)) {
            inputsAndChange = btcWalletService.getInputsAndChange(required);
            previous = required;

            // We calculate more exact tx size based on resulted inputs and change
            change = inputsAndChange.second;
            if (Restrictions.isDust(change)) {
                log.warn("We got a change below dust. We ignore that and use it as miner fee.");
                change = Coin.ZERO;
            }

            sellersTxSize = getVBytesSize(inputsAndChange.first, change.getValue());
            required = getSellersBtcInputValue(amount, txFeePerVbyte, sellersTxSize, sellersTradeFee);

            iterations++;
        }

        checkNotNull(inputsAndChange);

        return new Tuple2<>(inputsAndChange.first, change);
    }

    // Tx fee

    // See https://bitcoin.stackexchange.com/questions/87275/how-to-calculate-segwit-transaction-fee-in-bytes
    public static int getVBytesSize(List<RawTransactionInput> inputs, long change) {
        int size = 5; // Half of base tx size (10)
        size += inputs.stream()
                .mapToLong(input -> input.isSegwit() ? 68 : 149)
                .sum();
        size += change > 0 ? 62 : 31;
        return size;
    }

    public static long getAdjustedTxFee(BsqSwapTrade trade, int vBytes, long tradeFee) {
        return getAdjustedTxFee(trade.getTxFeePerVbyte(), vBytes, tradeFee);
    }

    public static long getAdjustedTxFee(long txFeePerVbyte, int vBytes, long tradeFee) {
        return txFeePerVbyte * vBytes - tradeFee;
    }

    // Convert BTC trade amount to BSQ amount
    public static Coin getBsqTradeAmount(Volume volume) {
        // We treat BSQ as altcoin with smallest unit exponent 8 but we use 2 instead.
        // To avoid a larger refactoring of the monetary domain we just hack in the conversion here
        // by removing the last 6 digits.
        return Coin.valueOf(MathUtils.roundDoubleToLong(MathUtils.scaleDownByPowerOf10(volume.getValue(), 6)));
    }
}
