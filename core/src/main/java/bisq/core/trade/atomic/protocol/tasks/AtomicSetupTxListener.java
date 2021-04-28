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

package bisq.core.trade.atomic.protocol.tasks;

import bisq.core.btc.listeners.AddressConfidenceListener;
import bisq.core.btc.wallet.WalletService;
import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.protocol.tasks.AtomicTradeTask;

import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public abstract class AtomicSetupTxListener extends AtomicTradeTask {
    // Use instance fields to not get eaten up by the GC
    private AddressConfidenceListener confidenceListener;
    protected Address myAddress;
    protected WalletService walletService;

    @SuppressWarnings({"unused"})
    public AtomicSetupTxListener(TaskRunner<AtomicTrade> taskHandler, AtomicTrade atomicTrade) {
        super(taskHandler, atomicTrade);
    }


    @Override
    protected void run() {
        try {
            runInterceptHook();

            TransactionConfidence confidence = walletService.getConfidenceForAddress(myAddress);
            if (isInNetwork(confidence)) {
                applyConfidence(confidence);
            } else {
                confidenceListener = new AddressConfidenceListener(myAddress) {
                    @Override
                    public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                        if (isInNetwork(confidence))
                            applyConfidence(confidence);
                    }
                };
                walletService.addAddressConfidenceListener(confidenceListener);
            }

            // we complete immediately, our object stays alive because the balanceListener is stored in the WalletService
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private void applyConfidence(TransactionConfidence confidence) {
        Transaction walletTx = walletService.getTransaction(confidence.getTransactionHash());
        checkNotNull(walletTx, "Tx from network should not be null");
        if (atomicProcessModel.getAtomicTx() != null) {
            atomicTrade.setTxId(walletTx.getTxId().toString());
            WalletService.printTx("atomicTx received from network", walletTx);
            setState();
            atomicProcessModel.getTradeManager().onTradeCompleted(atomicTrade);
        } else {
            log.info("We had the atomic tx already set. tradeId={}, state={}", atomicTrade.getId(),
                    atomicTrade.getState());
        }

        // need delay as it can be called inside the handler before the listener and tradeStateSubscription are actually set.
        UserThread.execute(this::unSubscribe);
    }

    private boolean isInNetwork(TransactionConfidence confidence) {
        return confidence != null &&
                (confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                        confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.PENDING));
    }

    private void unSubscribe() {
        if (confidenceListener != null)
            atomicProcessModel.getBtcWalletService().removeAddressConfidenceListener(confidenceListener);
    }

    protected void setState() {
        atomicTrade.setState(AtomicTrade.State.TX_CONFIRMED);
//        atomicProcessModel.getTradeManager(). addTradeToClosedTrades(atomicTrade);
    }

}
