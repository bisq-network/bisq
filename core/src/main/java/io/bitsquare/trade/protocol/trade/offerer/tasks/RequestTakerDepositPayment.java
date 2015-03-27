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
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.protocol.trade.messages.RequestTakerDepositPaymentMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestTakerDepositPayment extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(RequestTakerDepositPayment.class);

    public RequestTakerDepositPayment(TaskRunner taskHandler, OffererTrade offererTradeProcessModel) {
        super(taskHandler, offererTradeProcessModel);
    }

    @Override
    protected void doRun() {
        try {
            RequestTakerDepositPaymentMessage tradeMessage = new RequestTakerDepositPaymentMessage(
                    offererTradeProcessModel.id,
                    offererTradeProcessModel.offerer.connectedOutputsForAllInputs,
                    offererTradeProcessModel.offerer.outputs,
                    offererTradeProcessModel.offerer.tradeWalletPubKey,
                    offererTradeProcessModel.offerer.p2pSigPubKey,
                    offererTradeProcessModel.offerer.p2pEncryptPubKey,
                    offererTradeProcessModel.offerer.fiatAccount,
                    offererTradeProcessModel.offerer.accountId);

            offererTradeProcessModel.messageService.sendMessage(offererTrade.getTradingPeer(), tradeMessage, new SendMessageListener() {
                @Override
                public void handleResult() {
                    log.trace("RequestTakerDepositPaymentMessage successfully arrived at peer");
                    complete();
                }

                @Override
                public void handleFault() {
                    appendToErrorMessage("Sending RequestTakerDepositPaymentMessage failed");
                    offererTrade.setErrorMessage(errorMessage);
                    offererTrade.setProcessState(OffererTrade.OffererProcessState.MESSAGE_SENDING_FAILED);
                    offererTrade.setLifeCycleState(OffererTrade.OffererLifeCycleState.OFFER_OPEN);
                    failed();
                }
            });
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);
            offererTrade.setLifeCycleState(OffererTrade.OffererLifeCycleState.OFFER_OPEN);
            failed(t);
        }
    }
}
