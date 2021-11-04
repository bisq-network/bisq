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

package bisq.core.trade.protocol.bsq_swap.tasks.seller;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.trade.bsq_swap.BsqSwapCalculation;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapTradePeer;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public abstract class SellerCreatesAndSignsTx extends BsqSwapTask {
    public SellerCreatesAndSignsTx(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            BsqSwapTradePeer tradePeer = protocolModel.getTradePeer();
            TradeWalletService tradeWalletService = protocolModel.getTradeWalletService();

            List<RawTransactionInput> buyersBsqInputs = Objects.requireNonNull(tradePeer.getInputs());

            long sellersTradeFee = getSellersTradeFee();
            long txFeePerVbyte = trade.getTxFeePerVbyte();
            Tuple2<List<RawTransactionInput>, Coin> btcInputsAndChange = BsqSwapCalculation.getSellersBtcInputsAndChange(
                    protocolModel.getBtcWalletService(),
                    trade.getAmountAsLong(),
                    txFeePerVbyte,
                    sellersTradeFee);
            List<RawTransactionInput> sellersBtcInputs = btcInputsAndChange.first;
            protocolModel.setInputs(sellersBtcInputs);

            Coin sellersBsqPayoutAmount = Coin.valueOf(protocolModel.getPayout());
            String sellersBsqPayoutAddress = protocolModel.getBsqWalletService().getUnusedAddress().toString();
            protocolModel.setBsqAddress(sellersBsqPayoutAddress);

            Coin buyersBsqChangeAmount = Coin.valueOf(tradePeer.getChange());
            String buyersBsqChangeAddress = tradePeer.getBsqAddress();

            int buyersTxSize = BsqSwapCalculation.getVBytesSize(buyersBsqInputs, buyersBsqChangeAmount.getValue());
            Coin buyersBtcPayoutAmount = BsqSwapCalculation.getBuyersBtcPayoutValue(trade, buyersTxSize, getBuyersTradeFee());
            tradePeer.setPayout(buyersBtcPayoutAmount.getValue());
            String buyersBtcPayoutAddress = tradePeer.getBtcAddress();

            Coin sellersBtcChangeAmount = btcInputsAndChange.second;
            protocolModel.setChange(sellersBtcChangeAmount.getValue());

            String sellersBtcChangeAddress = protocolModel.getBtcWalletService().getFreshAddressEntry().getAddressString();
            protocolModel.setBtcAddress(sellersBtcChangeAddress);

            Transaction transaction = tradeWalletService.sellerBuildBsqSwapTx(
                    buyersBsqInputs,
                    sellersBtcInputs,
                    sellersBsqPayoutAmount,
                    sellersBsqPayoutAddress,
                    buyersBsqChangeAmount,
                    buyersBsqChangeAddress,
                    buyersBtcPayoutAmount,
                    buyersBtcPayoutAddress,
                    sellersBtcChangeAmount,
                    sellersBtcChangeAddress);

            // Sign my inputs
            int buyersInputSize = buyersBsqInputs.size();
            List<TransactionInput> myInputs = transaction.getInputs().stream()
                    .filter(input -> input.getIndex() >= buyersInputSize)
                    .collect(Collectors.toList());
            tradeWalletService.signBsqSwapTransaction(transaction, myInputs);

            log.info("Sellers signed his inputs of transaction {}", transaction);
            protocolModel.applyTransaction(transaction);

            int sellersTxSize = BsqSwapCalculation.getVBytesSize(sellersBtcInputs, sellersBtcChangeAmount.getValue());
            long sellersTxFee = BsqSwapCalculation.getAdjustedTxFee(txFeePerVbyte, sellersTxSize, sellersTradeFee);
            protocolModel.setTxFee(sellersTxFee);
            protocolModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    protected abstract long getBuyersTradeFee();

    protected abstract long getSellersTradeFee();
}
