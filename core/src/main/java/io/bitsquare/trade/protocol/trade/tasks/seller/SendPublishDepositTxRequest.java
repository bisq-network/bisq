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
import io.bitsquare.trade.protocol.trade.messages.PublishDepositTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendPublishDepositTxRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SendPublishDepositTxRequest.class);

    public SendPublishDepositTxRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            PublishDepositTxRequest tradeMessage = new PublishDepositTxRequest(
                    processModel.getId(),
                    processModel.getFiatAccount(),
                    processModel.getAccountId(),
                    processModel.getTradeWalletPubKey(),
                    trade.getContractAsJson(),
                    trade.getSellerContractSignature(),
                    processModel.getAddressEntry().getAddressString(),
                    processModel.getPreparedDepositTx(),
                    processModel.getConnectedOutputsForAllInputs()
            );

            processModel.getMessageService().sendEncryptedMessage(
                    trade.getTradingPeer(),
                    processModel.tradingPeer.getPubKeyRing(),
                    tradeMessage,
                    new SendMessageListener() {
                        @Override
                        public void handleResult() {
                            complete();
                        }

                        @Override
                        public void handleFault() {
                            appendToErrorMessage("Sending PublishDepositTxRequest failed");
                            failed();
                        }
                    });
        } catch (Throwable t) {
            failed(t);
        }
    }
}
