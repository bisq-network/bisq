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

package bisq.core.trade.protocol.bsq_swap.tasks.buyer;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.trade.bsq_swap.BsqSwapCalculation;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapTradePeer;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public abstract class BuyerCreatesAndSignsFinalizedTx extends BsqSwapTask {
    @SuppressWarnings({"unused"})
    public BuyerCreatesAndSignsFinalizedTx(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            BsqSwapTradePeer tradePeer = protocolModel.getTradePeer();

            List<RawTransactionInput> buyersBsqInputs = Objects.requireNonNull(protocolModel.getInputs());
            List<TransactionInput> sellersBtcInputs = tradePeer.getTransactionInputs();

            Coin sellersBsqPayoutAmount = BsqSwapCalculation.getSellersBsqPayoutValue(trade, getSellersTradeFee());
            String sellersBsqPayoutAddress = tradePeer.getBsqAddress();

            Coin buyersBsqChangeAmount = Coin.valueOf(protocolModel.getChange());
            String buyersBsqChangeAddress = protocolModel.getBsqAddress();

            Coin buyersBtcPayoutAmount = Coin.valueOf(protocolModel.getPayout());
            String buyersBtcPayoutAddress = protocolModel.getBtcAddress();

            Coin sellersBtcChangeAmount = Coin.valueOf(tradePeer.getChange());
            String sellersBtcChangeAddress = tradePeer.getBtcAddress();

            Transaction transaction = protocolModel.getTradeWalletService().buyerBuildBsqSwapTx(
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

            // We cross check if the peers tx matches ours. If not the tx would be invalid anyway as we would have
            // signed different transactions.
            checkArgument(Arrays.equals(transaction.bitcoinSerialize(), tradePeer.getTx()),
                    "Buyers unsigned transaction does not match the sellers tx");

            // Sign my inputs
            int buyersInputSize = buyersBsqInputs.size();
            List<TransactionInput> myInputs = transaction.getInputs().stream()
                    .filter(input -> input.getIndex() < buyersInputSize)
                    .collect(Collectors.toList());
            protocolModel.getBsqWalletService().signBsqSwapTransaction(transaction, myInputs);

            log.info("Fully signed BSQ swap transaction {}", transaction);
            protocolModel.applyTransaction(transaction);
            protocolModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    protected abstract long getSellersTradeFee();

    protected abstract long getBuyersTradeFee();
}
