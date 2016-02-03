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

package io.bitsquare.trade.protocol.trade.tasks.taker;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.messaging.SendMailboxMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.PayDepositRequest;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class SendPayDepositRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SendPayDepositRequest.class);

    public SendPayDepositRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            checkNotNull(trade.getTradeAmount(), "TradeAmount must not be null");
            checkNotNull(trade.getTakeOfferFeeTxId(), "TakeOfferFeeTxId must not be null");
            checkNotNull(processModel.getAddressEntry(), "AddressEntry must not be null");

            PayDepositRequest payDepositRequest = new PayDepositRequest(
                    processModel.getMyAddress(),
                    processModel.getId(),
                    trade.getTradeAmount().value,
                    processModel.getRawInputs(),
                    processModel.getChangeOutputValue(),
                    processModel.getChangeOutputAddress(),
                    processModel.getTradeWalletPubKey(),
                    processModel.getAddressEntry().getAddressString(),
                    processModel.getPubKeyRing(),
                    processModel.getPaymentAccountContractData(trade),
                    processModel.getAccountId(),
                    trade.getTakeOfferFeeTxId(),
                    processModel.getUser().getAcceptedArbitratorAddresses(),
                    trade.getArbitratorNodeAddress()
            );

            processModel.getP2PService().sendEncryptedMailboxMessage(
                    trade.getTradingPeerNodeAddress(),
                    processModel.tradingPeer.getPubKeyRing(),
                    payDepositRequest,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.trace("Message arrived at peer.");
                            complete();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.trace("Message stored in mailbox.");
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            appendToErrorMessage("PayDepositRequest sending failed");
                            failed();
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }
}
