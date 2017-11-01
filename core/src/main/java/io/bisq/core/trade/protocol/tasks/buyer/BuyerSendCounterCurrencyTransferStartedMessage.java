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

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.CounterCurrencyTransferStartedMessage;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.network.p2p.SendMailboxMessageListener;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class BuyerSendCounterCurrencyTransferStartedMessage extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public BuyerSendCounterCurrencyTransferStartedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            BtcWalletService walletService = processModel.getBtcWalletService();
            final String id = processModel.getOfferId();
            AddressEntry payoutAddressEntry = walletService.getOrCreateAddressEntry(id,
                    AddressEntry.Context.TRADE_PAYOUT);
            final CounterCurrencyTransferStartedMessage message = new CounterCurrencyTransferStartedMessage(
                    id,
                    payoutAddressEntry.getAddressString(),
                    processModel.getMyNodeAddress(),
                    processModel.getPayoutTxSignature(),
                    trade.getCounterCurrencyTxId(),
                    UUID.randomUUID().toString()
            );
            log.info("Send message to peer. tradeId={}, message{}", id, message);
            trade.setState(Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG);

            processModel.getP2PService().sendEncryptedMailboxMessage(
                    trade.getTradingPeerNodeAddress(),
                    processModel.getTradingPeer().getPubKeyRing(),
                    message,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("Message arrived at peer. tradeId={}", id);
                            trade.setState(Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG);
                            complete();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("Message stored in mailbox. tradeId={}", id);
                            trade.setState(Trade.State.BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG);
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("sendEncryptedMailboxMessage failed. message=" + message);
                            trade.setState(Trade.State.BUYER_SEND_FAILED_FIAT_PAYMENT_INITIATED_MSG);
                            appendToErrorMessage("Sending message failed: message=" + message + "\nerrorMessage=" + errorMessage);
                            failed(errorMessage);
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }
}
