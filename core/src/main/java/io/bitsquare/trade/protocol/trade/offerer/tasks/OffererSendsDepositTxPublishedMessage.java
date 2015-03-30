/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.OffererAsBuyerTrade;
import io.bitsquare.trade.OffererAsSellerTrade;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.protocol.trade.messages.DepositTxPublishedMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererSendsDepositTxPublishedMessage extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererSendsDepositTxPublishedMessage.class);

    public OffererSendsDepositTxPublishedMessage(TaskRunner taskHandler, OffererTrade offererTradeProcessModel) {
        super(taskHandler, offererTradeProcessModel);
    }

    @Override
    protected void doRun() {
        try {
            DepositTxPublishedMessage tradeMessage = new DepositTxPublishedMessage(offererTradeProcessModel.getId(), offererTrade.getDepositTx());

            offererTradeProcessModel.getMessageService().sendMessage(offererTrade.getTradingPeer(), tradeMessage, new SendMessageListener() {
                @Override
                public void handleResult() {
                    log.trace("DepositTxPublishedMessage successfully arrived at peer");
                    complete();
                }

                @Override
                public void handleFault() {
                    appendToErrorMessage("Sending DepositTxPublishedMessage failed");
                    offererTrade.setErrorMessage(errorMessage);

                    if (offererTrade instanceof OffererAsBuyerTrade)
                        offererTrade.setProcessState(OffererAsBuyerTrade.ProcessState.MESSAGE_SENDING_FAILED);
                    else if (offererTrade instanceof OffererAsSellerTrade)
                        offererTrade.setProcessState(OffererAsSellerTrade.ProcessState.MESSAGE_SENDING_FAILED);

                    failed();
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);
            failed(t);
        }
    }
}
