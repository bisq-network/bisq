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

import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.bisq_v1.messages.TradeMailboxMessage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendMailboxMessageListener;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class SendMailboxMessageTask extends TradeTask {
    public SendMailboxMessageTask(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    protected abstract TradeMailboxMessage getTradeMailboxMessage(String id);

    protected abstract void setStateSent();

    protected abstract void setStateArrived();

    protected abstract void setStateStoredInMailbox();

    protected abstract void setStateFault();

    @Override
    protected void run() {
        try {
            runInterceptHook();
            String id = processModel.getOfferId();
            TradeMailboxMessage message = getTradeMailboxMessage(id);
            setStateSent();
            NodeAddress peersNodeAddress = trade.getTradingPeerNodeAddress();
            log.info("Send {} to peer {}. tradeId={}, uid={}",
                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());

            processModel.getP2PService().getMailboxMessageService().sendEncryptedMailboxMessage(
                    peersNodeAddress,
                    processModel.getTradePeer().getPubKeyRing(),
                    message,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("{} arrived at peer {}. tradeId={}, uid={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                            setStateArrived();
                            complete();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("{} stored in mailbox for peer {}. tradeId={}, uid={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                            SendMailboxMessageTask.this.onStoredInMailbox();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("{} failed: Peer {}. tradeId={}, uid={}, errorMessage={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid(), errorMessage);
                            SendMailboxMessageTask.this.onFault(errorMessage, message);
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }

    protected void onStoredInMailbox() {
        setStateStoredInMailbox();
        complete();
    }

    protected void onFault(String errorMessage, TradeMessage message) {
        setStateFault();
        appendToErrorMessage("Sending message failed: message=" + message + "\nerrorMessage=" + errorMessage);
        failed(errorMessage);
    }
}
