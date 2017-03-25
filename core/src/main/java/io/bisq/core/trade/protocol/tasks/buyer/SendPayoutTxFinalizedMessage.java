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
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.protobuffer.message.trade.PayoutTxFinalizedMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
// TODO remove 
// TODO: locktime
public class SendPayoutTxFinalizedMessage extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public SendPayoutTxFinalizedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            if (trade.getPayoutTx() != null) {
                final String id = processModel.getId();
                final PayoutTxFinalizedMessage message = new PayoutTxFinalizedMessage(
                        id,
                        trade.getPayoutTx().bitcoinSerialize(),
                        processModel.getMyNodeAddress(),
                        UUID.randomUUID().toString()
                );
                log.info("Send message to peer. tradeId={}, message{}", id, message);
               /* processModel.getP2PService().sendEncryptedMailboxMessage(
                        trade.getTradingPeerNodeAddress(),
                        processModel.tradingPeer.getPubKeyRing(),
                        message,
                        new SendMailboxMessageListener() {
                            @Override
                            public void onArrived() {
                                log.info("Message arrived at peer. tradeId={}, message{}", id, message);
                                complete();
                            }

                            @Override
                            public void onStoredInMailbox() {
                                log.info("Message stored in mailbox. tradeId={}, message{}", id, message);
                                complete();
                            }

                            @Override
                            public void onFault(String errorMessage) {
                                appendToErrorMessage("PayoutTxFinalizedMessage sending failed. errorMessage=" + errorMessage);
                                failed(errorMessage);
                            }
                        }
                );*/
                // state must not be set in onArrived or onStoredInMailbox handlers as we would get that 
                // called delayed and would overwrite the broad cast state set by the next task
                trade.setState(Trade.State.BUYER_STARTED_SEND_PAYOUT_TX);
            } else {
                log.error("trade.getPayoutTx() = " + trade.getPayoutTx());
                failed("PayoutTx is null");
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
