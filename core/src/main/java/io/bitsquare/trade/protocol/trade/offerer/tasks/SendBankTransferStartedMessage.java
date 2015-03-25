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

import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.listener.SendMessageListener;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.offerer.models.OffererAsBuyerModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendBankTransferStartedMessage extends Task<OffererAsBuyerModel> {
    private static final Logger log = LoggerFactory.getLogger(SendBankTransferStartedMessage.class);

    public SendBankTransferStartedMessage(TaskRunner taskHandler, OffererAsBuyerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            FiatTransferStartedMessage tradeMessage = new FiatTransferStartedMessage(model.id,
                    model.offerer.payoutTxSignature,
                    model.offerer.payoutAmount,
                    model.taker.payoutAmount,
                    model.offerer.addressEntry.getAddressString());

            model.messageService.sendMessage(model.trade.getTradingPeer(), tradeMessage,
                    model.taker.p2pSigPublicKey,
                    model.taker.p2pEncryptPubKey,
                    new SendMessageListener() {
                        @Override
                        public void handleResult() {
                            log.trace("Sending BankTransferInitedMessage succeeded.");
                            model.trade.setProcessState(OffererTrade.OffererProcessState.FIAT_PAYMENT_STARTED);
                            complete();
                        }

                        @Override
                        public void handleFault() {
                            failed("Sending BankTransferInitedMessage failed.");
                            model.trade.setProcessState(OffererTrade.OffererProcessState.UNSPECIFIC_FAULT);
                        }
                    });
        } catch (Throwable t) {
            failed("Sending BankTransferInitedMessage failed.");
            model.trade.setProcessState(OffererTrade.OffererProcessState.UNSPECIFIC_FAULT);
        }
    }
}
