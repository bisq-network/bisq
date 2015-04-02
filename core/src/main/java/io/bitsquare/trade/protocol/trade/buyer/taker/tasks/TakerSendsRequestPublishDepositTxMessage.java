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
import io.bitsquare.trade.TakerState;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.RequestPublishDepositTxMessage;
import io.bitsquare.trade.protocol.trade.taker.tasks.TakerTradeTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerSendsRequestPublishDepositTxMessage extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerSendsRequestPublishDepositTxMessage.class);

    public TakerSendsRequestPublishDepositTxMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void doRun() {
        try {
            RequestPublishDepositTxMessage tradeMessage = new RequestPublishDepositTxMessage(
                    processModel.getId(),
                    processModel.getFiatAccount(),
                    processModel.getAccountId(),
                    processModel.getP2pSigPubKey(),
                    processModel.getP2pEncryptPublicKey(),
                    trade.getContractAsJson(),
                    trade.getTakerContractSignature(),
                    processModel.getAddressEntry().getAddressString(),
                    processModel.getPreparedDepositTx(),
                    processModel.getConnectedOutputsForAllInputs()
            );

            processModel.getMessageService().sendMessage(trade.getTradingPeer(), tradeMessage, new SendMessageListener() {
                @Override
                public void handleResult() {
                    complete();
                }

                @Override
                public void handleFault() {
                    appendToErrorMessage("Sending RequestOffererPublishDepositTxMessage failed");
                    trade.setErrorMessage(errorMessage);

                    if (trade instanceof TakerAsBuyerTrade)
                        trade.setProcessState(TakerState.ProcessState.MESSAGE_SENDING_FAILED);
                    else if (trade instanceof TakerAsSellerTrade)
                        trade.setProcessState(TakerState.ProcessState.MESSAGE_SENDING_FAILED);

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
