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

package bisq.core.trade.protocol.tasks.cancel;

import bisq.core.trade.Contract;
import bisq.core.trade.HandleCancelTradeRequestState;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.RequestCancelTradeMessage;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.tasks.SendMailboxMessageTask;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SendRequestCancelTradeMessage extends SendMailboxMessageTask {
    @SuppressWarnings({"unused"})
    public SendRequestCancelTradeMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected TradeMessage getMessage(String id) {
        PubKeyRing pubKeyRing = processModel.getPubKeyRing();
        Contract contract = checkNotNull(trade.getContract(), "contract must not be null");
        NodeAddress peersNodeAddress = contract.getPeersNodeAddress(pubKeyRing);
        P2PService p2PService = processModel.getP2PService();
        RequestCancelTradeMessage message = new RequestCancelTradeMessage(processModel.getCanceledTradePayoutTxSignature(),
                trade.getId(),
                p2PService.getAddress(),
                UUID.randomUUID().toString());
        log.info("Send {} to peer {}. tradeId={}, uid={}",
                message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());

        return message;
    }

    @Override
    protected void setStateSent() {
        trade.setHandleCancelTradeRequestState(HandleCancelTradeRequestState.REQUEST_MSG_SENT);
    }

    @Override
    protected void setStateArrived() {
        trade.setHandleCancelTradeRequestState(HandleCancelTradeRequestState.REQUEST_MSG_ARRIVED);
    }

    @Override
    protected void setStateStoredInMailbox() {
        trade.setHandleCancelTradeRequestState(HandleCancelTradeRequestState.REQUEST_MSG_IN_MAILBOX);
    }

    @Override
    protected void setStateFault() {
        trade.setHandleCancelTradeRequestState(HandleCancelTradeRequestState.REQUEST_MSG_SEND_FAILED);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            super.run();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
