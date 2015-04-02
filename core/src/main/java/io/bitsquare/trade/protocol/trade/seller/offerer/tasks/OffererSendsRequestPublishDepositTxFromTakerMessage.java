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

package io.bitsquare.trade.protocol.trade.seller.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.BuyerAsOffererTrade;
import io.bitsquare.trade.SellerAsOffererTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.TradeTask;
import io.bitsquare.trade.protocol.trade.messages.RequestPublishDepositTxFromSellerMessage;
import io.bitsquare.trade.states.OffererState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererSendsRequestPublishDepositTxFromTakerMessage extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererSendsRequestPublishDepositTxFromTakerMessage.class);

    public OffererSendsRequestPublishDepositTxFromTakerMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void doRun() {
        try {
            RequestPublishDepositTxFromSellerMessage tradeMessage = new RequestPublishDepositTxFromSellerMessage(
                    processModel.getId(),
                    processModel.getFiatAccount(),
                    processModel.getAccountId(),
                    processModel.getTradeWalletPubKey(),
                    processModel.getP2pSigPubKey(),
                    processModel.getP2pEncryptPublicKey(),
                    trade.getContractAsJson(),
                    trade.getOffererContractSignature(),
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

                    if (trade instanceof BuyerAsOffererTrade)
                        trade.setProcessState(OffererState.ProcessState.MESSAGE_SENDING_FAILED);
                    else if (trade instanceof SellerAsOffererTrade)
                        trade.setProcessState(OffererState.ProcessState.MESSAGE_SENDING_FAILED);

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
