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
import io.bitsquare.trade.TradeState;
import io.bitsquare.trade.protocol.trade.messages.FinalizePayoutTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import io.bitsquare.trade.states.StateUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendFinalizePayoutTxRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SendFinalizePayoutTxRequest.class);

    public SendFinalizePayoutTxRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            FinalizePayoutTxRequest message = new FinalizePayoutTxRequest(
                    processModel.getId(),
                    processModel.getPayoutTxSignature(),
                    processModel.getAddressEntry().getAddressString(),
                    trade.getLockTime()
            );

            processModel.getMessageService().sendEncryptedMessage(
                    trade.getTradingPeer(),
                    processModel.tradingPeer.getPubKeyRing(),
                    message,
                    new SendMessageListener() {
                        @Override
                        public void handleResult() {
                            log.trace("PayoutTxPublishedMessage successfully arrived at peer");

                            trade.setTradeState(TradeState.SellerState.FIAT_PAYMENT_RECEIPT_MSG_SENT);

                            complete();
                        }

                        @Override
                        public void handleFault() {
                            appendToErrorMessage("Sending PayoutTxPublishedMessage failed");
                            trade.setErrorMessage(errorMessage);
                            StateUtil.setSendFailedState(trade);
                            failed();
                        }
                    });
        } catch (Throwable t) {
            t.printStackTrace();

            failed(t);
        }
    }
}
