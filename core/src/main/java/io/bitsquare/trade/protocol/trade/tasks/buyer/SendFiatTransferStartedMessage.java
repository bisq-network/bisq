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
import io.bitsquare.p2p.messaging.SendMailboxMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.FiatTransferStartedMessage;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendFiatTransferStartedMessage extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SendFiatTransferStartedMessage.class);

    public SendFiatTransferStartedMessage(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            processModel.getP2PService().sendEncryptedMailboxMessage(
                    trade.getTradingPeerNodeAddress(),
                    processModel.tradingPeer.getPubKeyRing(),
                    new FiatTransferStartedMessage(
                            processModel.getId(),
                            processModel.getAddressEntry().getAddressString(),
                            processModel.getMyAddress()
                    ),
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("Message arrived at peer.");
                            trade.setState(Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG);
                            complete();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("Message stored in mailbox.");
                            trade.setState(Trade.State.BUYER_SENT_FIAT_PAYMENT_INITIATED_MSG);
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            appendToErrorMessage("FiatTransferStartedMessage sending failed");
                            failed(errorMessage);
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }
}
