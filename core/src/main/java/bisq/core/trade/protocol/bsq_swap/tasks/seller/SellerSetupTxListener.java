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

import bisq.core.btc.listeners.TxConfidenceListener;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;

import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import javafx.beans.value.ChangeListener;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.BUILDING;
import static org.bitcoinj.core.TransactionConfidence.ConfidenceType.PENDING;

@Slf4j
public abstract class SellerSetupTxListener extends BsqSwapTask {
    @Nullable
    private TxConfidenceListener confidenceListener;
    private ChangeListener<BsqSwapTrade.State> stateListener;

    public SellerSetupTxListener(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            if (trade.isCompleted()) {
                complete();
                return;
            }

            BsqWalletService walletService = protocolModel.getBsqWalletService();

            // The confidence listener based on the txId only works if all buyers inputs are segWit inputs
            // As we expect to receive anyway the buyers message with the finalized tx we can ignore the
            // rare cases where an input is not segwit and therefore the txId not matching.
            String txId = Objects.requireNonNull(protocolModel.getTransaction()).getTxId().toString();
            TransactionConfidence confidence = walletService.getConfidenceForTxId(txId);

            if (processConfidence(confidence)) {
                complete();
                return;
            }

            confidenceListener = new TxConfidenceListener(txId) {
                @Override
                public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                    if (processConfidence(confidence)) {
                        cleanup();
                    }
                }
            };
            walletService.addTxConfidenceListener(confidenceListener);

            // In case we received the message from the peer with the tx we get the trade state set to completed
            // and we stop listening on the network for the tx
            stateListener = (observable, oldValue, newValue) -> {
                if (newValue == BsqSwapTrade.State.COMPLETED) {
                    cleanup();
                }
            };
            trade.stateProperty().addListener(stateListener);

            // We complete immediately, our object stays alive because the listener has a reference in the walletService
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private boolean processConfidence(TransactionConfidence confidence) {
        if (confidence == null) {
            return false;
        }

        if (trade.getTransaction(protocolModel.getBsqWalletService()) != null) {
            // If we have the tx already set, we are done
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
        protocolModel.getTradeManager().requestPersistence();
        onTradeCompleted();

        log.info("Received buyers tx from network {}", walletTx);
        return true;
    }


    private boolean isInNetwork(TransactionConfidence confidence) {
        return confidence != null &&
                (confidence.getConfidenceType().equals(BUILDING) ||
                        confidence.getConfidenceType().equals(PENDING));
    }

    private void cleanup() {
        UserThread.execute(() -> {
            if (confidenceListener != null) {
                protocolModel.getBsqWalletService().removeTxConfidenceListener(confidenceListener);
            }
            if (stateListener != null) {
                trade.stateProperty().removeListener(stateListener);
            }
        });
    }

    protected abstract void onTradeCompleted();
}
