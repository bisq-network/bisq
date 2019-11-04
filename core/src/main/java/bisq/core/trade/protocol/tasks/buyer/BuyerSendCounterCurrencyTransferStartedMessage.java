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

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendMailboxMessageListener;

import bisq.common.taskrunner.TaskRunner;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerSendCounterCurrencyTransferStartedMessage extends TradeTask {
    @SuppressWarnings({"unused"})
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
            NodeAddress peersNodeAddress = trade.getTradingPeerNodeAddress();
            log.info("Send {} to peer {}. tradeId={}, uid={}",
                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
            trade.setState(Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG);
            processModel.getP2PService().sendEncryptedMailboxMessage(
                    peersNodeAddress,
                    processModel.getTradingPeer().getPubKeyRing(),
                    message,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("{} arrived at peer {}. tradeId={}, uid={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                            trade.setState(Trade.State.BUYER_SAW_ARRIVED_FIAT_PAYMENT_INITIATED_MSG);
                            complete();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("{} stored in mailbox for peer {}. tradeId={}, uid={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                            trade.setState(Trade.State.BUYER_STORED_IN_MAILBOX_FIAT_PAYMENT_INITIATED_MSG);
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("{} failed: Peer {}. tradeId={}, uid={}, errorMessage={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid(), errorMessage);
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
