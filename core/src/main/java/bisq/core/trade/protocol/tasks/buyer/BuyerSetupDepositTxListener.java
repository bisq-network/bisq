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

package bisq.core.trade.protocol.tasks.buyer;

import bisq.core.btc.listeners.AddressConfidenceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class BuyerSetupDepositTxListener extends TradeTask {
    // Use instance fields to not get eaten up by the GC
    private Subscription tradeStateSubscription;
    private AddressConfidenceListener confidenceListener;

    public BuyerSetupDepositTxListener(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            if (trade.getDepositTx() == null && processModel.getPreparedDepositTx() != null) {
                BtcWalletService walletService = processModel.getBtcWalletService();
                NetworkParameters params = walletService.getParams();
                Transaction preparedDepositTx = new Transaction(params, processModel.getPreparedDepositTx());
                checkArgument(!preparedDepositTx.getOutputs().isEmpty(), "preparedDepositTx.getOutputs() must not be empty");
                Address depositTxAddress = preparedDepositTx.getOutput(0).getScriptPubKey().getToAddress(params);
                TransactionConfidence confidence = walletService.getConfidenceForAddress(depositTxAddress);
                if (isVisibleInNetwork(confidence)) {
                    applyConfidence(confidence);
                } else {
                    confidenceListener = new AddressConfidenceListener(depositTxAddress) {
                        @Override
                        public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                            if (isVisibleInNetwork(confidence)) {
                                applyConfidence(confidence);
                            }
                        }
                    };
                    walletService.addAddressConfidenceListener(confidenceListener);

                    tradeStateSubscription = EasyBind.subscribe(trade.stateProperty(), newValue -> {
                        if (trade.isDepositPublished()) {
                            swapReservedForTradeEntry();

                            // hack to remove tradeStateSubscription at callback
                            UserThread.execute(this::unSubscribeAndRemoveListener);
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
        if (trade.getDepositTx() == null) {
            Transaction walletTx = processModel.getTradeWalletService().getWalletTx(confidence.getTransactionHash());
            trade.applyDepositTx(walletTx);
            BtcWalletService.printTx("depositTx received from network", walletTx);

            // We don't want to trigger the tradeStateSubscription when setting the state, so we unsubscribe before
            unSubscribeAndRemoveListener();
            trade.setState(Trade.State.BUYER_SAW_DEPOSIT_TX_IN_NETWORK);
        } else {
            unSubscribeAndRemoveListener();
        }

        swapReservedForTradeEntry();
    }

    private boolean isVisibleInNetwork(TransactionConfidence confidence) {
        return confidence != null &&
                (confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                        confidence.getConfidenceType().equals(TransactionConfidence.ConfidenceType.PENDING));
    }

    private void swapReservedForTradeEntry() {
        processModel.getBtcWalletService().swapTradeEntryToAvailableEntry(trade.getId(),
                AddressEntry.Context.RESERVED_FOR_TRADE);
    }

    private void unSubscribeAndRemoveListener() {
        if (tradeStateSubscription != null) {
            tradeStateSubscription.unsubscribe();
            tradeStateSubscription = null;
        }

        if (confidenceListener != null) {
            processModel.getBtcWalletService().removeAddressConfidenceListener(confidenceListener);
            confidenceListener = null;
        }
    }
}
