/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.offerer;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.network.p2p.SendDirectMessageListener;
import io.bisq.protobuffer.message.trade.PublishDepositTxRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

public class SendPublishDepositTxRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SendPublishDepositTxRequest.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public SendPublishDepositTxRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            BtcWalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();

            Optional<AddressEntry> addressEntryOptional = walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            checkArgument(addressEntryOptional.isPresent(), "addressEntry must be set here.");
            AddressEntry offererPayoutAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT);
            byte[] offererMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(offererMultiSigPubKey,
                            addressEntryOptional.get().getPubKey()),
                    "offererMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            PublishDepositTxRequest tradeMessage = new PublishDepositTxRequest(
                    processModel.getId(),
                    processModel.getPaymentAccountPayload(trade),
                    processModel.getAccountId(),
                    offererMultiSigPubKey,
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
