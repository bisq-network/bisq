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
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.network.p2p.SendMailboxMessageListener;
import io.bisq.protobuffer.message.trade.FiatTransferStartedMessage;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendFiatTransferStartedMessage extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public SendFiatTransferStartedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            BtcWalletService walletService = processModel.getWalletService();
            final String id = processModel.getId();
            AddressEntry payoutAddressEntry = walletService.getOrCreateAddressEntry(id,
                    AddressEntry.Context.TRADE_PAYOUT);
            final FiatTransferStartedMessage message = new FiatTransferStartedMessage(
                    id,
                    payoutAddressEntry.getAddressString(),
                    processModel.getMyNodeAddress(),
                    processModel.getPayoutTxSignature()
            );
            log.info("Send message to peer. tradeId={}, message{}", id, message);
            processModel.getP2PService().sendEncryptedMailboxMessage(
                    trade.getTradingPeerNodeAddress(),
                    processModel.tradingPeer.getPubKeyRing(),
                    message,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("Message arrived at peer. tradeId={}, message{}", id, message);
                            trade.setState(Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG);
                            complete();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("Message stored in mailbox. tradeId={}, message{}", id, message);
                            trade.setState(Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG);
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            appendToErrorMessage("FiatTransferStartedMessage sending failed");
                            failed(errorMessage);
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }
}
