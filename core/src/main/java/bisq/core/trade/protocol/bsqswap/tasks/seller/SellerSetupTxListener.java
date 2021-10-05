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

package bisq.core.trade.protocol.bsqswap.tasks.seller;

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.tasks.BsqSwapTask;

import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class SellerSetupTxListener extends BsqSwapTask {
    @Nullable
    private TxConfidenceListener listener;

    public SellerSetupTxListener(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            if (trade.isCompleted()) {
                complete();
                return;
            }

            BsqWalletService walletService = protocolModel.getBsqWalletService();
            String txId = Objects.requireNonNull(protocolModel.getTransaction()).getTxId().toString();
            TransactionConfidence confidence = walletService.getConfidenceForTxId(txId);

            if (processConfidence(confidence)) {
                complete();
                return;
            }

            listener = new TxConfidenceListener(txId) {
                @Override
                public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                    if (processConfidence(confidence)) {
                        UserThread.execute(() -> walletService.removeTxConfidenceListener(listener));
                    }
                }
            };
            walletService.addTxConfidenceListener(listener);

            // We complete immediately, our object stays alive because the listener has a reference in the walletService
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    protected abstract void onTradeCompleted();

    private boolean processConfidence(TransactionConfidence confidence) {
        if (trade.getTransaction(protocolModel.getBsqWalletService()) != null) {
            // If we have the tx already set we are done
            return true;
        }

        if (!isInNetwork(confidence)) {
            return false;
        }

        Transaction walletTx = protocolModel.getBsqWalletService().getTransaction(confidence.getTransactionHash());
        if (walletTx == null) {
            return false;
        }

        trade.applyTransaction(walletTx);
        trade.setState(BsqSwapTrade.State.COMPLETED);
        protocolModel.getTradeManager().onBsqSwapTradeCompleted(trade);
        onTradeCompleted();

        log.info("Received bsqSwapTx from network {}", walletTx);
        return true;
    }


    private boolean isInNetwork(TransactionConfidence confidence) {
        return confidence != null &&
                (confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                        confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.PENDING));
    }
}
