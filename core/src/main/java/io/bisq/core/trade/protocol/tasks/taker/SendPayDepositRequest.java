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

package io.bisq.core.trade.protocol.tasks.taker;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.network.p2p.SendMailboxMessageListener;
import io.bisq.protobuffer.message.trade.PayDepositRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class SendPayDepositRequest extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SendPayDepositRequest.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public SendPayDepositRequest(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            checkNotNull(trade.getTradeAmount(), "TradeAmount must not be null");
            checkNotNull(trade.getTakeOfferFeeTxId(), "TakeOfferFeeTxId must not be null");

            BtcWalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();
            AddressEntry takerPayoutAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT);
            checkArgument(!walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG).isPresent(), "addressEntry must not be set here.");
            AddressEntry addressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            byte[] takerMultiSigPubKey = addressEntry.getPubKey();
            String takerPayoutAddressString = takerPayoutAddressEntry.getAddressString();
            PayDepositRequest payDepositRequest = new PayDepositRequest(
                    processModel.getMyNodeAddress(),
                    processModel.getId(),
                    trade.getTradeAmount().value,
                    trade.getTradePrice().getValue(),
                    trade.getTxFee(),
                    trade.getTakeOfferFee(),
                    processModel.getRawTransactionInputs(),
                    processModel.getChangeOutputValue(),
                    processModel.getChangeOutputAddress(),
                    takerMultiSigPubKey,
                    takerPayoutAddressString,
                    processModel.getPubKeyRing(),
                    processModel.getPaymentAccountPayload(trade),
                    processModel.getAccountId(),
                    trade.getTakeOfferFeeTxId(),
                    new ArrayList<>(processModel.getUser().getAcceptedArbitratorAddresses()),
                    trade.getArbitratorNodeAddress()
            );
            processModel.setMyMultiSigPubKey(takerMultiSigPubKey);

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
