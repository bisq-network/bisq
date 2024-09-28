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

import org.jetbrains.annotations.Contract;

@Slf4j
public class SetupStagedTxListeners extends TradeTask {
    private OutputSpendConfidenceListener warningConfidenceListener;
    private OutputSpendConfidenceListener redirectOrClaimConfidenceListener;

    public SetupStagedTxListeners(TaskRunner<Trade> taskHandler, Trade trade) {
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
                if (isWarningTxInNetwork(confidence)) {
                    applyWarningConfidence(confidence);
                } else {
                    warningConfidenceListener = new OutputSpendConfidenceListener(depositTxOutput) {
                        @Override
                        public void onOutputSpendConfidenceChanged(TransactionConfidence confidence) {
                            if (isWarningTxInNetwork(confidence)) {
                                applyWarningConfidence(confidence);
                            }
                        }
                    };
                    walletService.addSpendConfidenceListener(warningConfidenceListener);
                }
            }
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private void applyWarningConfidence(TransactionConfidence confidence) {
        BtcWalletService walletService = processModel.getBtcWalletService();
        Transaction warningTx = Objects.requireNonNull(walletService.getTransaction(confidence.getTransactionHash()));
        BtcWalletService.printTx("warningTx received from network", warningTx);
        if (warningConfidenceListener != null) {
            walletService.removeSpendConfidenceListener(warningConfidenceListener);
            warningConfidenceListener = null;
        }
        if (isFundsUnreleased()) {
            Transaction myWarningTx = walletService.getTxFromSerializedTx(processModel.getFinalizedWarningTx());
            boolean isMine = warningTx.equals(myWarningTx);
            Trade.DisputeState newDisputeState = isMine ? Trade.DisputeState.WARNING_SENT : Trade.DisputeState.WARNING_SENT_BY_PEER;
            if (!isMine && processModel.getTradePeer().getFinalizedWarningTx() == null) {
                // In case the peer's warning tx was cleared out as sensitive data, restore it.
                processModel.getTradePeer().setFinalizedWarningTx(warningTx.bitcoinSerialize());
            }
            log.info("Setting dispute state to {} for tradeId {}.", newDisputeState, processModel.getOfferId());
            trade.setDisputeState(newDisputeState);
            processModel.getTradeManager().requestPersistence();

            TransactionOutput warningTxOutput = warningTx.getOutput(0);
            TransactionInput spentBy = warningTxOutput.getSpentBy();
            TransactionConfidence spendConfidence = spentBy != null ? spentBy.getParentTransaction().getConfidence() : null;
            // TODO: Should sanity check that we really do have a redirect or claim tx, and not any kind of custom
            //  payout from the warning escrow output, cooperatively constructed with the peer:
            if (isTxInNetwork(spendConfidence)) {
                applyRedirectOrClaimConfidence(spendConfidence);
            } else {
                redirectOrClaimConfidenceListener = new OutputSpendConfidenceListener(warningTxOutput) {
                    @Override
                    public void onOutputSpendConfidenceChanged(TransactionConfidence confidence) {
                        if (isTxInNetwork(spendConfidence)) {
                            applyRedirectOrClaimConfidence(spendConfidence);
                        }
                    }
                };
                walletService.addSpendConfidenceListener(redirectOrClaimConfidenceListener);
            }
        } else {
            log.warn("Received warning tx but trade funds have already been released.");
        }
    }

    private void applyRedirectOrClaimConfidence(TransactionConfidence confidence) {
        BtcWalletService walletService = processModel.getBtcWalletService();
        Transaction redirectOrClaimTx = Objects.requireNonNull(walletService.getTransaction(confidence.getTransactionHash()));
        boolean isClaimTx = redirectOrClaimTx.hasRelativeLockTime();
        BtcWalletService.printTx((isClaimTx ? "claimTx" : "redirectTx") + " received from network", redirectOrClaimTx);
        if (redirectOrClaimConfidenceListener != null) {
            walletService.removeSpendConfidenceListener(redirectOrClaimConfidenceListener);
            redirectOrClaimConfidenceListener = null;
        }
        if (isFundsUnreleased()) {
            Trade.DisputeState newDisputeState;
            if (isClaimTx) {
                Transaction myClaimTx = processModel.getSignedClaimTx() != null ?
                        walletService.getTxFromSerializedTx(processModel.getSignedClaimTx()) : null;
                boolean isMine = redirectOrClaimTx.equals(myClaimTx);
                newDisputeState = isMine ? Trade.DisputeState.ESCROW_CLAIMED : Trade.DisputeState.ESCROW_CLAIMED_BY_PEER;
                if (!isMine) {
                    // Set the peer's claim tx, so that it shows up in the details window for past trades.
                    processModel.getTradePeer().setClaimTx(redirectOrClaimTx.bitcoinSerialize());
                }
            } else {
                Transaction myRedirectTx = walletService.getTxFromSerializedTx(processModel.getFinalizedRedirectTx());
                boolean isMine = redirectOrClaimTx.equals(myRedirectTx);
                newDisputeState = isMine ? Trade.DisputeState.REFUND_REQUESTED : Trade.DisputeState.REFUND_REQUEST_STARTED_BY_PEER;
                if (!isMine && processModel.getTradePeer().getFinalizedRedirectTx() == null) {
                    // In case the peer's redirect tx was cleared out as sensitive data, restore it.
                    processModel.getTradePeer().setFinalizedRedirectTx(redirectOrClaimTx.bitcoinSerialize());
                }
            }
            log.info("Setting dispute state to {} for tradeId {}.", newDisputeState, processModel.getOfferId());
            trade.setDisputeState(newDisputeState);
            processModel.getTradeManager().requestPersistence();
        } else {
            log.info("Ignoring received redirect/claim tx, as trade funds have already been released.");
        }
    }

    private boolean isFundsUnreleased() {
        return trade.isFundsLockedIn() || !trade.isDepositConfirmed() && !trade.getDisputeState().isArbitrated();
    }

    @Contract("null -> false") // (IDEA really should be able to deduce this by itself.)
    private boolean isWarningTxInNetwork(TransactionConfidence confidence) {
        if (isTxInNetwork(confidence)) {
            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            Transaction tx = Objects.requireNonNull(btcWalletService.getTransaction(confidence.getTransactionHash()));
            return tx.getLockTime() != 0;
        }
        return false;
    }

    private static boolean isTxInNetwork(TransactionConfidence confidence) {
        return confidence != null &&
                (confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                        confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.PENDING));
    }
}
