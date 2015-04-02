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

package io.bitsquare.trade.protocol.trade.seller.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.OffererAsBuyerTrade;
import io.bitsquare.trade.OffererAsSellerTrade;
import io.bitsquare.trade.OffererState;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.PayoutTxPublishedMessage;
import io.bitsquare.trade.protocol.trade.offerer.tasks.OffererTradeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererSendsPayoutTxPublishedMessage extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererSendsPayoutTxPublishedMessage.class);

    public OffererSendsPayoutTxPublishedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void doRun() {
        try {
            PayoutTxPublishedMessage tradeMessage = new PayoutTxPublishedMessage(processModel.getId(), processModel.getPayoutTx());
            processModel.getMessageService().sendMessage(trade.getTradingPeer(),
                    tradeMessage,
                    processModel.getP2pSigPubKey(),
                    processModel.getP2pEncryptPubKey(),
                    new SendMessageListener() {
                        @Override
                        public void handleResult() {
                            log.trace("PayoutTxPublishedMessage successfully arrived at peer");
                            complete();
                        }

                        @Override
                        public void handleFault() {
                            appendToErrorMessage("Sending PayoutTxPublishedMessage failed");
                            trade.setErrorMessage(errorMessage);

                            if (trade instanceof OffererAsBuyerTrade)
                                trade.setProcessState(OffererState.ProcessState.MESSAGE_SENDING_FAILED);
                            else if (trade instanceof OffererAsSellerTrade)
                                trade.setProcessState(OffererState.ProcessState.MESSAGE_SENDING_FAILED);

                            failed();
                        }
                    });
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);
            failed(t);
        }
    }
}
