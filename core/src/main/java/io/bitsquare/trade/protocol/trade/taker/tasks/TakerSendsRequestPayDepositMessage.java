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

package io.bitsquare.trade.protocol.trade.taker.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.TakerAsBuyerTrade;
import io.bitsquare.trade.TakerAsSellerTrade;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.protocol.trade.messages.RequestPayDepositFromOffererMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerSendsRequestPayDepositMessage extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerSendsRequestPayDepositMessage.class);

    public TakerSendsRequestPayDepositMessage(TaskRunner taskHandler, TakerTrade takerTrade) {
        super(taskHandler, takerTrade);
    }

    @Override
    protected void doRun() {
        try {
            RequestPayDepositFromOffererMessage message = new RequestPayDepositFromOffererMessage(
                    takerTradeProcessModel.getId(),
                    model.getTradeAmount(),
                    takerTradeProcessModel.taker.getConnectedOutputsForAllInputs(),
                    takerTradeProcessModel.taker.getOutputs(),
                    takerTradeProcessModel.taker.getTradeWalletPubKey(),
                    takerTradeProcessModel.taker.getP2pSigPubKey(),
                    takerTradeProcessModel.taker.getP2pEncryptPubKey(),
                    takerTradeProcessModel.taker.getFiatAccount(),
                    takerTradeProcessModel.taker.getAccountId());

            takerTradeProcessModel.getMessageService().sendMessage(takerTrade.getTradingPeer(), message, new SendMessageListener() {
                @Override
                public void handleResult() {
                    log.trace("RequestTakerDepositPaymentMessage successfully arrived at peer");
                    complete();
                }

                @Override
                public void handleFault() {
                    appendToErrorMessage("Sending RequestTakerDepositPaymentMessage failed");
                    takerTrade.setErrorMessage(errorMessage);
                    if (takerTrade instanceof TakerAsBuyerTrade)
                        takerTrade.setProcessState(TakerAsBuyerTrade.ProcessState.MESSAGE_SENDING_FAILED);
                    else if (takerTrade instanceof TakerAsSellerTrade)
                        takerTrade.setProcessState(TakerAsSellerTrade.ProcessState.MESSAGE_SENDING_FAILED);

                    failed();
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);

            if (takerTrade instanceof TakerAsBuyerTrade) {
                takerTrade.setProcessState(TakerAsBuyerTrade.ProcessState.MESSAGE_SENDING_FAILED);
            }
            else if (takerTrade instanceof TakerAsSellerTrade) {
                takerTrade.setProcessState(TakerAsSellerTrade.ProcessState.MESSAGE_SENDING_FAILED);
            }

            failed(t);
        }
    }
}
