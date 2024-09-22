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

package bisq.core.trade.protocol.bisq_v5.tasks;

import bisq.core.btc.listeners.OutputSpendConfidenceListener;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;

import java.util.Objects;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SetupWarningTxListener extends TradeTask {
    private OutputSpendConfidenceListener confidenceListener;

    public SetupWarningTxListener(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            Transaction depositTx = trade.getDepositTx();
            if (depositTx != null && isFundsUnreleased()) {
                BtcWalletService walletService = processModel.getBtcWalletService();
                TransactionOutput depositTxOutput = depositTx.getOutput(0);

                TransactionInput spentBy = depositTxOutput.getSpentBy();
                TransactionConfidence confidence = spentBy != null ? spentBy.getParentTransaction().getConfidence() : null;
                if (isMatchingTxInNetwork(confidence)) {
                    applyConfidence(confidence);
                } else {
                    confidenceListener = new OutputSpendConfidenceListener(depositTxOutput) {
                        @Override
                        public void onOutputSpendConfidenceChanged(TransactionConfidence confidence) {
                            if (isMatchingTxInNetwork(confidence)) {
                                applyConfidence(confidence);
                            }
                        }
                    };
                    walletService.addSpendConfidenceListener(confidenceListener);
                }
            }
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private void applyConfidence(TransactionConfidence confidence) {
        BtcWalletService walletService = processModel.getBtcWalletService();
        Transaction warningTx = Objects.requireNonNull(walletService.getTransaction(confidence.getTransactionHash()));
        Transaction myWarningTx = walletService.getTxFromSerializedTx(processModel.getFinalizedWarningTx());
        BtcWalletService.printTx("warningTx received from network", warningTx);
        if (isFundsUnreleased()) {
            Trade.DisputeState newDisputeState = warningTx.equals(myWarningTx) ?
                    Trade.DisputeState.WARNING_SENT : Trade.DisputeState.WARNING_SENT_BY_PEER;
            log.info("Setting dispute state to {} for tradeId {}.", newDisputeState, processModel.getOfferId());
            trade.setDisputeState(newDisputeState);
            processModel.getTradeManager().requestPersistence();
        } else {
            log.warn("Received warning tx but trade funds have already been released.");
        }
        if (confidenceListener != null) {
            walletService.removeSpendConfidenceListener(confidenceListener);
            confidenceListener = null;
        }
    }

    private boolean isFundsUnreleased() {
        return trade.isFundsLockedIn() || !trade.isDepositConfirmed() && !trade.getDisputeState().isArbitrated();
    }

    private boolean isMatchingTxInNetwork(TransactionConfidence confidence) {
        if (confidence == null ||
                !confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING) &&
                        !confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.PENDING)) {
            return false;
        }
        BtcWalletService btcWalletService = processModel.getBtcWalletService();
        Transaction tx = Objects.requireNonNull(btcWalletService.getTransaction(confidence.getTransactionHash()));
        return tx.getLockTime() != 0;
    }
}
