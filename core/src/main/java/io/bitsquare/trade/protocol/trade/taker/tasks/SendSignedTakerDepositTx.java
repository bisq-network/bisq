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
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.trade.protocol.trade.messages.RequestOffererPublishDepositTxMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendSignedTakerDepositTx extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(SendSignedTakerDepositTx.class);

    public SendSignedTakerDepositTx(TaskRunner taskHandler, TakerTrade model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            RequestOffererPublishDepositTxMessage tradeMessage = new RequestOffererPublishDepositTxMessage(
                    takerTradeProcessModel.id,
                    takerTradeProcessModel.taker.fiatAccount,
                    takerTradeProcessModel.taker.accountId,
                    takerTradeProcessModel.taker.p2pSigPubKey,
                    takerTradeProcessModel.taker.p2pEncryptPublicKey,
                    takerTrade.getContractAsJson(),
                    takerTrade.getTakerContractSignature(),
                    takerTradeProcessModel.taker.addressEntry.getAddressString(),
                    takerTradeProcessModel.taker.preparedDepositTx,
                    takerTradeProcessModel.taker.connectedOutputsForAllInputs,
                    takerTradeProcessModel.taker.outputs
            );

            takerTradeProcessModel.messageService.sendMessage(takerTrade.getTradingPeer(), tradeMessage, new SendMessageListener() {
                @Override
                public void handleResult() {
                    complete();
                }

                @Override
                public void handleFault() {
                    appendToErrorMessage("Sending RequestOffererDepositPublicationMessage failed");
                    takerTrade.setErrorMessage(errorMessage);
                    takerTrade.setProcessState(TakerTrade.TakerProcessState.MESSAGE_SENDING_FAILED);

                    failed();
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);
            failed(t);
        }
    }
}
