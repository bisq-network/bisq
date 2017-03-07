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

package io.bitsquare.trade.protocol.trade.tasks.offerer;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.messaging.SendDirectMessageListener;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.messages.PublishDepositTxRequest;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendPublishDepositTxRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SendPublishDepositTxRequest.class);

    public SendPublishDepositTxRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            WalletService walletService = processModel.getWalletService();
            AddressEntry offererMultiSigAddressEntry = walletService.getOrCreateAddressEntry(processModel.getOffer().getId(), AddressEntry.Context.MULTI_SIG);
            AddressEntry offererPayoutAddressEntry = walletService.getOrCreateAddressEntry(processModel.getOffer().getId(), AddressEntry.Context.TRADE_PAYOUT);
            PublishDepositTxRequest tradeMessage = new PublishDepositTxRequest(
                    processModel.getId(),
                    processModel.getPaymentAccountContractData(trade),
                    processModel.getAccountId(),
                    offererMultiSigAddressEntry.getPubKey(),
                    trade.getContractAsJson(),
                    trade.getOffererContractSignature(),
                    offererPayoutAddressEntry.getAddressString(),
                    processModel.getPreparedDepositTx(),
                    processModel.getRawTransactionInputs()
            );

            processModel.getP2PService().sendEncryptedDirectMessage(
                    trade.getTradingPeerNodeAddress(),
                    processModel.tradingPeer.getPubKeyRing(),
                    tradeMessage,
                    new SendDirectMessageListener() {
                        @Override
                        public void onArrived() {
                            log.trace("Message arrived at peer.");
                            trade.setState(Trade.State.OFFERER_SENT_PUBLISH_DEPOSIT_TX_REQUEST);
                            complete();
                        }

                        @Override
                        public void onFault() {
                            appendToErrorMessage("PublishDepositTxRequest sending failed");
                            failed();
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }
}
