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

package io.bitsquare.trade.protocol.trade.tasks.seller;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.TradeTask;
import io.bitsquare.trade.protocol.trade.messages.DepositTxInputsRequest;
import io.bitsquare.trade.states.StateUtil;

import org.bitcoinj.utils.Threading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendDepositTxInputsRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SendDepositTxInputsRequest.class);

    public SendDepositTxInputsRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    private int retryCounter = 0;

    @Override
    protected void doRun() {
        try {
            assert processModel.getTakeOfferFeeTx() != null;
            DepositTxInputsRequest message = new DepositTxInputsRequest(
                    processModel.getId(),
                    processModel.getPubKeyRing(),
                    processModel.getTakeOfferFeeTx().getHashAsString(),
                    trade.getTradeAmount(),
                    processModel.getTradeWalletPubKey());

            processModel.getMessageService().sendEncryptedMessage(
                    trade.getTradingPeer(),
                    processModel.tradingPeer.getPubKeyRing(),
                    message,
                    new SendMessageListener() {
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
                                Threading.USER_THREAD.execute(SendDepositTxInputsRequest.this::doRun);
                            }
                            else {
                                appendToErrorMessage("Sending TakeOfferFeePayedMessage to offerer failed. Maybe the network connection was " +
                                        "lost or the offerer lost his connection. We persisted the state of the trade, please try again later " +
                                        "or cancel that trade.");

                                trade.setErrorMessage(errorMessage);
                                StateUtil.setSendFailedState(trade);
                                failed();
                            }
                        }
                    });
        } catch (Throwable t) {
            t.printStackTrace();
            trade.setThrowable(t);
            failed(t);
        }
    }
}