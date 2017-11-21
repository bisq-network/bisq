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

package io.bisq.core.trade.protocol.tasks.taker;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.DepositTxPublishedMessage;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.network.p2p.SendMailboxMessageListener;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public class TakerSendDepositTxPublishedMessage extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public TakerSendDepositTxPublishedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            if (trade.getDepositTx() != null) {
                final String id = processModel.getOfferId();
                DepositTxPublishedMessage message = new DepositTxPublishedMessage(processModel.getOfferId(),
                    trade.getDepositTx().bitcoinSerialize(),
                    processModel.getMyNodeAddress(),
                    UUID.randomUUID().toString());
                trade.setState(Trade.State.TAKER_SENT_DEPOSIT_TX_PUBLISHED_MSG);

                processModel.getP2PService().sendEncryptedMailboxMessage(
                    trade.getTradingPeerNodeAddress(),
                    processModel.getTradingPeer().getPubKeyRing(),
                    message,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("Message arrived at peer. tradeId={}", id);
                            trade.setState(Trade.State.TAKER_SAW_ARRIVED_DEPOSIT_TX_PUBLISHED_MSG);
                            complete();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("Message stored in mailbox. tradeId={}", id);
                            trade.setState(Trade.State.TAKER_STORED_IN_MAILBOX_DEPOSIT_TX_PUBLISHED_MSG);
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("sendEncryptedMailboxMessage failed. message=" + message);
                            trade.setState(Trade.State.TAKER_SEND_FAILED_DEPOSIT_TX_PUBLISHED_MSG);
                            appendToErrorMessage("Sending message failed: message=" + message + "\nerrorMessage=" + errorMessage);
                            failed();
                        }
                    }
                );
            } else {
                log.error("trade.getDepositTx() = " + trade.getDepositTx());
                failed("DepositTx is null");
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
