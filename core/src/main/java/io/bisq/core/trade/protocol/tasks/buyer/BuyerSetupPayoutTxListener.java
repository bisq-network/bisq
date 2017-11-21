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

package io.bisq.core.trade.protocol.tasks.buyer;

import io.bisq.common.UserThread;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.listeners.AddressConfidenceListener;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

@Slf4j
public class BuyerSetupPayoutTxListener extends TradeTask {
    // Use instance fields to not get eaten up by the GC
    private Subscription tradeStateSubscription;
    private AddressConfidenceListener confidenceListener;

    @SuppressWarnings({"WeakerAccess", "unused"})
    public BuyerSetupPayoutTxListener(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            if (!trade.isPayoutPublished()) {
                BtcWalletService walletService = processModel.getBtcWalletService();
                final String id = processModel.getOffer().getId();
                Address address = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT).getAddress();

                final TransactionConfidence confidence = walletService.getConfidenceForAddress(address);
                if (isInNetwork(confidence)) {
                    applyConfidence(confidence);
                } else {
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
                            swapMultiSigEntry();

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

    private void applyConfidence(TransactionConfidence confidence) {
        if (trade.getPayoutTx() == null) {
            Transaction walletTx = processModel.getTradeWalletService().getWalletTx(confidence.getTransactionHash());
            trade.setPayoutTx(walletTx);
            BtcWalletService.printTx("payoutTx received from network", walletTx);
            trade.setState(Trade.State.BUYER_SAW_PAYOUT_TX_IN_NETWORK);
        } else {
            log.info("We got the payout tx already set from BuyerProcessPayoutTxPublishedMessage. tradeId={}, state={}", trade.getId(), trade.getState());
        }

        swapMultiSigEntry();

        // need delay as it can be called inside the handler before the listener and tradeStateSubscription are actually set.
        UserThread.execute(this::unSubscribe);
    }

    private void swapMultiSigEntry() {
        processModel.getBtcWalletService().swapTradeEntryToAvailableEntry(trade.getId(), AddressEntry.Context.MULTI_SIG);
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
