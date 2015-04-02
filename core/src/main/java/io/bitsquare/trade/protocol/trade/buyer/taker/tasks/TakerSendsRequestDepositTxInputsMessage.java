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

package io.bitsquare.trade.protocol.trade.buyer.taker.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.TakerAsBuyerTrade;
import io.bitsquare.trade.TakerAsSellerTrade;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.protocol.trade.messages.RequestDepositTxInputsMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerTradeTask;

import javafx.application.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerSendsRequestDepositTxInputsMessage extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerSendsRequestDepositTxInputsMessage.class);

    public TakerSendsRequestDepositTxInputsMessage(TaskRunner taskHandler, TakerTrade takerTrade) {
        super(taskHandler, takerTrade);
    }

    private int retryCounter = 0;

    @Override
    protected void doRun() {
        try {
            assert processModel.getTakeOfferFeeTx() != null;
            RequestDepositTxInputsMessage message = new RequestDepositTxInputsMessage(
                    processModel.getId(),
                    processModel.getTakeOfferFeeTx().getHashAsString(),
                    takerTrade.getTradeAmount(),
                    processModel.getTradeWalletPubKey());

            processModel.getMessageService().sendMessage(takerTrade.getTradingPeer(), message, new SendMessageListener() {
                @Override
                public void handleResult() {
                    log.trace("Sending TakeOfferFeePayedMessage succeeded.");
                    complete();
                }

                @Override
                public void handleFault() {
                    log.warn("Sending TakeOfferFeePayedMessage failed. We try a second time.");
                    // Take offer fee is already paid, so we need to try to get that trade to succeed.
                    // We try to repeat once and if that fails as well we persist the state for a later retry.
                    if (retryCounter == 0) {
                        retryCounter++;
                        Platform.runLater(TakerSendsRequestDepositTxInputsMessage.this::doRun);
                    }
                    else {
                        appendToErrorMessage("Sending TakeOfferFeePayedMessage to offerer failed. Maybe the network connection was " +
                                "lost or the offerer lost his connection. We persisted the state of the trade, please try again later " +
                                "or cancel that trade.");

                        takerTrade.setErrorMessage(errorMessage);

                        if (takerTrade instanceof TakerAsBuyerTrade)
                            takerTrade.setProcessState(TakerAsBuyerTrade.ProcessState.MESSAGE_SENDING_FAILED);
                        else if (takerTrade instanceof TakerAsSellerTrade)
                            takerTrade.setProcessState(TakerAsSellerTrade.ProcessState.MESSAGE_SENDING_FAILED);


                        failed();
                    }
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);
            failed(t);
        }
    }
}