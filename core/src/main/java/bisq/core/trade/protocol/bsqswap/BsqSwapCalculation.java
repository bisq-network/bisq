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

package bisq.core.trade.protocol.bsqswap;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;

import org.bitcoinj.core.Coin;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

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
