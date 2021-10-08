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
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;

import org.bitcoinj.core.Coin;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * The fees can be paid either by adding them to the inputs or by reducing them from the outputs. As we want to avoid extra inputs only needed for the fees (tx fee in case of buyer and trade fee in case of seller) we mix the cases so that buyer adds the trade fee to the BSQ input and reduce the tx fee from the BTC output. For the seller its the other way round.
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
 * Input buyer: BSQ trade amount + buyer trade fee                                              5000000 + 50 = 5000050
 * Input seller: BTC trade amount + seller tx fee                                               100000000 + 1850 = 100001850
 * Output seller: BSQ trade amount - sellers trade fee                                          5000000 - 150 = 4999850
 * Output buyer:  BSQ change                                                                    0
 * Output buyer:  BTC trade amount - buyers tx fee                                              100000000 - 1950 = 99998050
 * Output seller:  BTC change                                                                   0
 * Tx fee: Buyer tx fee + seller tx fee + buyer trade fee + seller trade fee                    1950 + 1850 + 50 + 150 = 4000
 */
@Slf4j
public class BsqSwapCalculation {

    // Buyers BSQ Input
    public static Coin getBuyersRequiredBsqInputs(BsqSwapTrade trade) {
        return Coin.valueOf(trade.getBsqTradeAmount() + trade.getMakerFee() + trade.getTakerFee());
    }

    // Buyers BTC payout
    public static long getBuyersBtcPayoutAmount(long sellersTradeFee,
                                                BsqSwapTrade bsqSwapTrade,
                                                int buyersTxSize,
                                                long buyersTradeFee) {
        long sellersTradeFeeConvertedToBtc = getTradeFeeConvertedToBtc(bsqSwapTrade, sellersTradeFee);
        long buyersPartOfTxFee = bsqSwapTrade.getTxFeePerVbyte() * buyersTxSize;
        long result = bsqSwapTrade.getAmount()          // Expected trade amount
                + sellersTradeFeeConvertedToBtc         // Buyer has prefunded trade fee of seller with converted BSQ to BTC
                - buyersPartOfTxFee                     // The seller has prefunded my part of the miner fee
                + buyersTradeFee;                       // My burned BSQ fee was used to deduct from miner fee
        log.error("Buyers BtcPayoutAmount:\n" +
                        "TradeAmount={}\n" +
                        "sellersTradeFeeConvertedToBtc={}\n" +
                        "buyersPartOfTxFee={}\n" +
                        "buyersTradeFee={}\n" +
                        "result={}",
                bsqSwapTrade.getAmount(),
                sellersTradeFeeConvertedToBtc,
                buyersPartOfTxFee,
                buyersTradeFee,
                result
        );
        return result;
    }

    // Sellers inputs
    public static Coin getSellersRequiredBtcInput(BsqSwapTrade bsqSwapTrade, long sellersTradeFee, int sellersTxSize) {
        long sellersTradeFeeConvertedToBtc = BsqSwapCalculation.getTradeFeeConvertedToBtc(bsqSwapTrade, sellersTradeFee);
        // buyers part of the fee is deducted in output
        long sellersPartOfTxFee = bsqSwapTrade.getTxFeePerVbyte() * sellersTxSize;
        long result = bsqSwapTrade.getAmount()          // Trade amount
                + sellersTradeFeeConvertedToBtc         // Peer has prepaid my tradeFee. Converted fee in BTC goes to peers output
                + sellersPartOfTxFee                    // Sellers share of miner fee
                - sellersTradeFee;                      // Burned BSQ deducted from txFee, depending on role its maker or takerFee

        log.error("Seller RequiredBtcInput:\n" +
                        "TradeAmount={}\n" +
                        "sellersTradeFeeConvertedToBtc={}\n" +
                        "sellersPartOfTxFee={}\n" +
                        "sellersTradeFee={}\n" +
                        "result={}\n" +
                        "TxFeePerVbyte={}",
                bsqSwapTrade.getAmount(),
                sellersTradeFeeConvertedToBtc,
                sellersPartOfTxFee,
                sellersTradeFee,
                result,
                bsqSwapTrade.getTxFeePerVbyte()
        );
        return Coin.valueOf(result);
    }

    public static int getTxSize(TradeWalletService tradeWalletService, List<RawTransactionInput> inputs, long change) {
        int size = 10 / 2; // Half of base tx size
        size += inputs.stream()
                .map(rawInput -> tradeWalletService.getTransactionInput(null, new byte[]{}, rawInput))
                .mapToLong(transactionInput -> 41 + (transactionInput.hasWitness() ? 29 : 108))
                .sum();
        // Outputs: 31 (we only use segwit)
        size += change > 0 ? 2 * 31 : 31;
        return size;
    }

    private static long getTradeFeeConvertedToBtc(BsqSwapTrade bsqSwapTrade, long tradeFee) {
        return bsqSwapTrade.getPrice().getValue() * tradeFee / 100;
    }
}
