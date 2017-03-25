/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.buyer;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.listeners.AddressConfidenceListener;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.TransactionConfidence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetupListenerForPayoutTx extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SetupListenerForPayoutTx.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public SetupListenerForPayoutTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            BtcWalletService walletService = processModel.getWalletService();
            Address payoutAddress = walletService.getOrCreateAddressEntry(processModel.getOffer().getId(),
                    AddressEntry.Context.TRADE_PAYOUT).getAddress();
            final AddressConfidenceListener confidenceListener = new AddressConfidenceListener(payoutAddress) {
                @Override
                public void onTransactionConfidenceChanged(TransactionConfidence confidence) {
                    checkConfidence(confidence);
                }
            };
            if (trade.getState().ordinal() < Trade.State.PAYOUT_BROAD_CASTED.ordinal()) {
                TransactionConfidence confidence = walletService.getConfidenceForAddress(payoutAddress);
                if (confidence != null) {
                    checkConfidence(confidence);
                } else if (processModel.getPayoutAddressConfidenceListener() == null) {
                    //TODO remove listener
                    processModel.setPayoutAddressConfidenceListener(confidenceListener);
                    walletService.addAddressConfidenceListener(confidenceListener);
                } else {
                    log.warn("We had already set up a payoutAddressConfidenceListener for {}", payoutAddress);
                }
            } else {
                complete();
            }
        } catch (Throwable t) {
            failed(t);
        }
    }

    private void checkConfidence(TransactionConfidence confidence) {
        log.error("onTransactionConfidenceChanged " + confidence);
        if (confidence != null) {
            TransactionConfidence.ConfidenceType confidenceType = confidence.getConfidenceType();
            log.error("payoutTx confidenceType:" + confidenceType);
            if (confidenceType.equals(TransactionConfidence.ConfidenceType.BUILDING) ||
                    confidenceType.equals(TransactionConfidence.ConfidenceType.PENDING)) {
                trade.setState(Trade.State.BUYER_SAW_PAYOUT_TX_IN_NETWORK);
                final AddressConfidenceListener listener = processModel.getPayoutAddressConfidenceListener();
                if (listener != null)
                    processModel.getWalletService().removeAddressConfidenceListener(listener);
                complete();
            } else {
                errorMessage = "Confidence of payout tx is " + confidenceType.name();
                failed();
            }
        }
    }
}
