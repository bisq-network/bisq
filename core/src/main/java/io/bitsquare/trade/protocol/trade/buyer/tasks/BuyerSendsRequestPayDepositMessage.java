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

package io.bitsquare.trade.protocol.trade.buyer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.StateUtil;
import io.bitsquare.trade.protocol.trade.TradeTask;
import io.bitsquare.trade.protocol.trade.messages.RequestPayDepositMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BuyerSendsRequestPayDepositMessage extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(BuyerSendsRequestPayDepositMessage.class);

    public BuyerSendsRequestPayDepositMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void doRun() {
        try {
            RequestPayDepositMessage tradeMessage = new RequestPayDepositMessage(
                    processModel.getId(),
                    trade.getTradeAmount(),
                    processModel.getConnectedOutputsForAllInputs(),
                    processModel.getOutputs(),
                    processModel.getTradeWalletPubKey(),
                    processModel.getP2pSigPubKey(),
                    processModel.getP2pEncryptPubKey(),
                    processModel.getFiatAccount(),
                    processModel.getAccountId());

            processModel.getMessageService().sendMessage(trade.getTradingPeer(), tradeMessage, new SendMessageListener() {
                @Override
                public void handleResult() {
                    log.trace("RequestTakerDepositPaymentMessage successfully arrived at peer");
                    complete();
                }

                @Override
                public void handleFault() {
                    appendToErrorMessage("Sending RequestTakerDepositPaymentMessage failed");
                    trade.setErrorMessage(errorMessage);
                    StateUtil.setOfferOpenState(trade);
                    StateUtil.setSendFailedState(trade);
                    failed();
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);
            StateUtil.setOfferOpenState(trade);
            StateUtil.setSendFailedState(trade);
            failed(t);
        }
    }
}
