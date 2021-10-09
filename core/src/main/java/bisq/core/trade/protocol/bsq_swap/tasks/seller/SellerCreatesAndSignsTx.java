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
import bisq.core.btc.wallet.Restrictions;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.BsqSwapCalculation;
import bisq.core.trade.protocol.bsq_swap.BsqSwapTradePeer;
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

import static com.google.common.base.Preconditions.checkNotNull;


@Slf4j
public abstract class SellerCreatesAndSignsTx extends BsqSwapTask {
    private static final int MIN_SELLERS_TX_SIZE = 104;

    public SellerCreatesAndSignsTx(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            TradeWalletService tradeWalletService = protocolModel.getTradeWalletService();
            BsqSwapTradePeer tradePeer = protocolModel.getTradePeer();
            long sellersTradeFee = getSellersTradeFee();

            // Figure out how large out tx will be
            int iterations = 0;
            Tuple2<List<RawTransactionInput>, Coin> inputsAndChange = null;
            Coin previous = null;

            // At first we try with min. tx size
            int sellersTxSize = MIN_SELLERS_TX_SIZE;
            Coin sellersBtcChangeAmount = Coin.ZERO;
            Coin required = BsqSwapCalculation.getSellersBtcInputValue(trade, sellersTxSize, sellersTradeFee);

            // We do one iteration here to get the size of the inputs (segwit or not)
            inputsAndChange = tradeWalletService.getSellersBtcInputsForBsqSwapTx(required);
            sellersTxSize = BsqSwapCalculation.getVBytesSize(inputsAndChange.first, 0);
            required = BsqSwapCalculation.getSellersBtcInputValue(trade, sellersTxSize, sellersTradeFee);

            // As fee calculation is not deterministic it could be that we toggle between a too small and too large
            // input. We would take the latest result before we break iteration. Worst case is that we under- or
            // overpay a bit. As fee rate is anyway an estimation we ignore that imperfection.
            while (iterations < 10 && !required.equals(previous)) {
                inputsAndChange = tradeWalletService.getSellersBtcInputsForBsqSwapTx(required);
                previous = required;

                // We calculate more exact tx size based on resulted inputs and change
                sellersBtcChangeAmount = inputsAndChange.second;
                if (!Restrictions.isAboveDust(sellersBtcChangeAmount)) {
                    log.warn("We got a change below dust. We ignore that and use it as miner fee.");
                    sellersBtcChangeAmount = Coin.ZERO;
                }

                sellersTxSize = BsqSwapCalculation.getVBytesSize(inputsAndChange.first, sellersBtcChangeAmount.getValue());
                required = BsqSwapCalculation.getSellersBtcInputValue(trade, sellersTxSize, sellersTradeFee);

                iterations++;
            }

            checkNotNull(inputsAndChange);

            List<RawTransactionInput> buyersBsqInputs = Objects.requireNonNull(tradePeer.getInputs());

            List<RawTransactionInput> sellersBtcInputs = inputsAndChange.first;
            protocolModel.setInputs(sellersBtcInputs);

            Coin sellersBsqPayoutAmount = Coin.valueOf(protocolModel.getPayout());
            String sellersBsqPayoutAddress = protocolModel.getBsqWalletService().getUnusedAddress().toString();
            protocolModel.setBsqAddress(sellersBsqPayoutAddress);

            Coin buyersBsqChangeAmount = Coin.valueOf(tradePeer.getChange());
            String buyersBsqChangeAddress = tradePeer.getBsqAddress();

            Coin buyersBtcPayoutAmount = BsqSwapCalculation.getBuyersBtcPayoutValue(trade, getBuyersTxSize(), getBuyersTradeFee());
            tradePeer.setPayout(buyersBtcPayoutAmount.getValue());
            String buyersBtcPayoutAddress = tradePeer.getBtcAddress();

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

            log.error("Sellers signed transaction {}", transaction);
            protocolModel.applyTransaction(transaction);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private int getBuyersTxSize() {
        return BsqSwapCalculation.getVBytesSize(Objects.requireNonNull(protocolModel.getTradePeer().getInputs()),
                protocolModel.getTradePeer().getChange());
    }

    protected abstract long getBuyersTradeFee();

    protected abstract long getSellersTradeFee();
}
