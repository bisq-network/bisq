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

package io.bitsquare.trade.protocol.trade.tasks.buyer;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.BuyerAsTakerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.PayDepositRequest;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendPayDepositRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SendPayDepositRequest.class);

    public SendPayDepositRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            boolean isInitialRequest = trade instanceof BuyerAsTakerTrade;
            PayDepositRequest tradeMessage = new PayDepositRequest(
                    processModel.getId(),
                    trade.getTradeAmount(),
                    isInitialRequest,
                    processModel.getConnectedOutputsForAllInputs(),
                    processModel.getOutputs(),
                    processModel.getTradeWalletPubKey(),
                    processModel.getPubKeyRing(),
                    processModel.getFiatAccount(),
                    processModel.getAccountId(),
                    processModel.getTakeOfferFeeTxId());

            processModel.getMessageService().sendEncryptedMessage(
                    trade.getTradingPeer(),
                    processModel.tradingPeer.getPubKeyRing(),
                    tradeMessage,
                    false,
                    new SendMessageListener() {
                        @Override
                        public void handleResult() {
                            log.trace("PayDepositRequest successfully arrived at peer");
                            complete();
                        }

                        @Override
                        public void handleFault() {
                            appendToErrorMessage("Sending PayDepositRequest failed");
                            failed();
                        }
                    });
        } catch (Throwable t) {
            failed(t);
        }
    }
}
