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

package bisq.core.trade.protocol.bisq_v1.tasks;

import bisq.core.btc.listeners.AddressConfidenceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public abstract class SetupPayoutTxListener extends TradeTask {
    // Use instance fields to not get eaten up by the GC
    private Subscription tradeStateSubscription;
    private AddressConfidenceListener confidenceListener;

    public SetupPayoutTxListener(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }


    protected abstract void setState();

    protected abstract void validatePayoutTx(Transaction payoutTx);

    @Override
    protected void run() {
        try {
            runInterceptHook();
            if (!trade.isPayoutPublished()) {
                BtcWalletService walletService = processModel.getBtcWalletService();
                String id = processModel.getOffer().getId();
                Address address = checkNotNull(
                        walletService.getAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT).orElse(null),
                        "Payout address entry must exist. tradeId=%s",
                        id).getAddress();
                Transaction depositTx = checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");

                // check if the payout already happened (ensuring it was > deposit block height, see GH #5725)
                TransactionConfidence confidence = walletService.getConfidenceForAddressFromBlockHeight(address,
                        depositTx.getConfidence().getAppearedAtChainHeight());
                if (!isInNetwork(confidence) || !applyConfidence(confidence)) {
                    confidenceListener = new AddressConfidenceListener(address) {
                        @Override
                        public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                            if (isInNetwork(confidence))
                                applyConfidence(confidence);
                        }
                    };
                    walletService.addAddressConfidenceListener(confidenceListener);

                    tradeStateSubscription = EasyBind.subscribe(trade.stateProperty(), newValue -> {
                        if (trade.isPayoutPublished()) {
                            processModel.getBtcWalletService().resetCoinLockedInMultiSigAddressEntry(trade.getId());

                            // hack to remove tradeStateSubscription at callback
                            UserThread.execute(this::unSubscribe);
                        }
                    });
                }
            }

            // we complete immediately, our object stays alive because the balanceListener is stored in the WalletService
            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private boolean applyConfidence(TransactionConfidence confidence) {
        if (trade.getPayoutTx() == null) {
            Transaction walletTx = processModel.getTradeWalletService().getWalletTx(confidence.getTransactionHash());
            if (!isPayoutTxValid(walletTx)) {
                return false;
            }
            trade.setPayoutTx(walletTx);
            processModel.getTradeManager().requestPersistence();
            BtcWalletService.printTx("payoutTx received from network", walletTx);
            setState();
        } else {
            log.info("We had the payout tx already set. tradeId={}, state={}", trade.getId(), trade.getTradeState());
        }

        processModel.getBtcWalletService().resetCoinLockedInMultiSigAddressEntry(trade.getId());

        // need delay as it can be called inside the handler before the listener and tradeStateSubscription are actually set.
        UserThread.execute(this::unSubscribe);
        return true;
    }

    private boolean isPayoutTxValid(Transaction payoutTx) {
        try {
            validatePayoutTx(payoutTx);
            return true;
        } catch (Exception e) {
            String txId = payoutTx != null ? payoutTx.getTxId().toString() : "null";
            log.warn("Ignoring invalid payoutTx from network. tradeId={}, txId={}, error={}",
                    trade.getId(),
                    txId,
                    e.toString());
            return false;
        }
    }

    private boolean isInNetwork(TransactionConfidence confidence) {
        return confidence != null &&
                (confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                        confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.PENDING));
    }

    private void unSubscribe() {
        if (tradeStateSubscription != null)
            tradeStateSubscription.unsubscribe();

        if (confidenceListener != null)
            processModel.getBtcWalletService().removeAddressConfidenceListener(confidenceListener);
    }
}
