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

import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapFinalizedTxMessage;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;
import bisq.core.util.Validator;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionWitness;

import java.util.Arrays;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public abstract class ProcessBsqSwapFinalizedTxMessage extends BsqSwapTask {
    @SuppressWarnings({"unused"})
    public ProcessBsqSwapFinalizedTxMessage(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            BsqSwapFinalizedTxMessage message = checkNotNull((BsqSwapFinalizedTxMessage) protocolModel.getTradeMessage());
            checkNotNull(message);
            Validator.checkTradeId(protocolModel.getOfferId(), message);

            // We cross check if the tx matches our partially signed tx by removing the sigs from the buyers inputs
            Transaction buyersTransactionWithoutSigs = protocolModel.getBtcWalletService().getTxFromSerializedTx(message.getTx());
            int buyersInputSize = Objects.requireNonNull(protocolModel.getTradePeer().getInputs()).size();
            Objects.requireNonNull(buyersTransactionWithoutSigs.getInputs()).stream()
                    .filter(input -> input.getIndex() < buyersInputSize)
                    .forEach(input -> {
                        input.clearScriptBytes();
                        input.setWitness(TransactionWitness.EMPTY);
                    });
            byte[] sellersPartiallySignedTx = protocolModel.getTx();
            checkArgument(Arrays.equals(buyersTransactionWithoutSigs.bitcoinSerialize(), sellersPartiallySignedTx),
                    "Buyers unsigned transaction does not match the sellers tx");

            if (trade.getTransaction(protocolModel.getBsqWalletService()) != null) {
                // If we have the tx already set, we are done
                complete();
                return;
            }

            Transaction buyersTransaction = protocolModel.getBtcWalletService().getTxFromSerializedTx(message.getTx());
            trade.applyTransaction(buyersTransaction);
            trade.setState(BsqSwapTrade.State.COMPLETED);
            protocolModel.getTradeManager().onBsqSwapTradeCompleted(trade);
            protocolModel.getTradeManager().requestPersistence();
            onTradeCompleted();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    protected abstract void onTradeCompleted();
}
