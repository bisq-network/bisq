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

package io.bitsquare.trade.protocol.trade.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.OffererAsBuyerTrade;
import io.bitsquare.trade.OffererAsSellerTrade;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.protocol.trade.messages.RequestPublishDepositTxMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererSendsRequestPublishDepositTxMessage extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererSendsRequestPublishDepositTxMessage.class);

    public OffererSendsRequestPublishDepositTxMessage(TaskRunner taskHandler, OffererTrade model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            RequestPublishDepositTxMessage tradeMessage = new RequestPublishDepositTxMessage(
                    offererTradeProcessModel.getId(),
                    offererTradeProcessModel.offerer.getFiatAccount(),
                    offererTradeProcessModel.offerer.getAccountId(),
                    offererTradeProcessModel.offerer.getP2pSigPubKey(),
                    offererTradeProcessModel.offerer.getP2pEncryptPublicKey(),
                    offererTrade.getContractAsJson(),
                    offererTrade.getOffererContractSignature(),
                    offererTradeProcessModel.offerer.getAddressEntry().getAddressString(),
                    offererTradeProcessModel.offerer.getPreparedDepositTx(),
                    offererTradeProcessModel.offerer.getConnectedOutputsForAllInputs()
            );

            offererTradeProcessModel.getMessageService().sendMessage(offererTrade.getTradingPeer(), tradeMessage, new SendMessageListener() {
                @Override
                public void handleResult() {
                    complete();
                }

                @Override
                public void handleFault() {
                    appendToErrorMessage("Sending RequestOffererPublishDepositTxMessage failed");
                    offererTrade.setErrorMessage(errorMessage);

                    if (offererTrade instanceof OffererAsBuyerTrade)
                        offererTrade.setProcessState(OffererAsBuyerTrade.ProcessState.MESSAGE_SENDING_FAILED);
                    else if (offererTrade instanceof OffererAsSellerTrade)
                        offererTrade.setProcessState(OffererAsSellerTrade.ProcessState.MESSAGE_SENDING_FAILED);

                    failed();
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);
            failed(t);
        }
    }
}
