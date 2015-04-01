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
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererSendsFiatTransferStartedMessage extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererSendsFiatTransferStartedMessage.class);

    public OffererSendsFiatTransferStartedMessage(TaskRunner taskHandler, OffererTrade offererTrade) {
        super(taskHandler, offererTrade);
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
                    offererTradeProcessModel.taker.getP2pSigPubKey(),
                    offererTradeProcessModel.taker.getP2pEncryptPubKey(),
                    new SendMessageListener() {
                        @Override
                        public void handleResult() {
                            log.trace("Sending FiatTransferStartedMessage succeeded.");

                            if (offererTrade instanceof OffererAsBuyerTrade) {
                                ((OffererAsBuyerTrade) offererTrade).setProcessState(OffererAsBuyerTrade.ProcessState.FIAT_PAYMENT_STARTED);
                            }
                            else if (offererTrade instanceof OffererAsSellerTrade) {
                                ((OffererAsSellerTrade) offererTrade).setProcessState(OffererAsSellerTrade.ProcessState.FIAT_PAYMENT_STARTED);
                            }
                            
                            complete();
                        }

                        @Override
                        public void handleFault() {
                            appendToErrorMessage("Sending FiatTransferStartedMessage failed");
                            offererTrade.setErrorMessage(errorMessage);

                            if (offererTrade instanceof OffererAsBuyerTrade) {
                                ((OffererAsBuyerTrade) offererTrade).setProcessState(OffererAsBuyerTrade.ProcessState.MESSAGE_SENDING_FAILED);
                            }
                            else if (offererTrade instanceof OffererAsSellerTrade) {
                                ((OffererAsSellerTrade) offererTrade).setProcessState(OffererAsSellerTrade.ProcessState.MESSAGE_SENDING_FAILED);
                            }
                            
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
