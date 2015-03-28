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
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendFiatTransferStartedMessage extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(SendFiatTransferStartedMessage.class);

    public SendFiatTransferStartedMessage(TaskRunner taskHandler, OffererTrade offererTradeProcessModel) {
        super(taskHandler, offererTradeProcessModel);
    }

    @Override
    protected void doRun() {
        try {
            FiatTransferStartedMessage tradeMessage = new FiatTransferStartedMessage(offererTradeProcessModel.getId(),
                    offererTradeProcessModel.offerer.getPayoutTxSignature(),
                    offererTradeProcessModel.offerer.getPayoutAmount(),
                    offererTradeProcessModel.taker.getPayoutAmount(),
                    offererTradeProcessModel.offerer.getAddressEntry().getAddressString());

            offererTradeProcessModel.getMessageService().sendMessage(offererTrade.getTradingPeer(), tradeMessage,
                    offererTradeProcessModel.taker.getP2pSigPublicKey(),
                    offererTradeProcessModel.taker.getP2pEncryptPubKey(),
                    new SendMessageListener() {
                        @Override
                        public void handleResult() {
                            log.trace("Sending FiatTransferStartedMessage succeeded.");
                            offererTrade.setProcessState(OffererTrade.OffererProcessState.FIAT_PAYMENT_STARTED);
                            complete();
                        }

                        @Override
                        public void handleFault() {
                            appendToErrorMessage("Sending FiatTransferStartedMessage failed");
                            offererTrade.setErrorMessage(errorMessage);
                            offererTrade.setProcessState(OffererTrade.OffererProcessState.MESSAGE_SENDING_FAILED);
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
