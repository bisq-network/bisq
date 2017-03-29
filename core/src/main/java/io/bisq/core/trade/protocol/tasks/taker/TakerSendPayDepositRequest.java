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
import io.bisq.core.trade.messages.PayDepositRequest;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.network.p2p.SendDirectMessageListener;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakerSendPayDepositRequest extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public TakerSendPayDepositRequest(TaskRunner taskHandler, Trade trade) {
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
            checkArgument(!walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG).isPresent(),
                    "addressEntry must not be set here.");
            AddressEntry addressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            byte[] takerMultiSigPubKey = addressEntry.getPubKey();
            String takerPayoutAddressString = takerPayoutAddressEntry.getAddressString();
            PayDepositRequest message = new PayDepositRequest(
                    processModel.getMyNodeAddress(),
                    processModel.getId(),
                    trade.getTradeAmount().value,
                    trade.getTradePrice().getValue(),
                    trade.getTxFee().getValue(),
                    trade.getTakeOfferFee().getValue(),
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
                    new ArrayList<>(processModel.getUser().getAcceptedMediatorAddresses()),
                    trade.getArbitratorNodeAddress(),
                    trade.getMediatorNodeAddress()
            );
            processModel.setMyMultiSigPubKey(takerMultiSigPubKey);

            processModel.getP2PService().sendEncryptedDirectMessage(
                    trade.getTradingPeerNodeAddress(),
                    processModel.tradingPeer.getPubKeyRing(),
                    message,
                    new SendDirectMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("Message arrived at peer. tradeId={}, message{}", id, message);
                            complete();
                        }

                        @Override
                        public void onFault() {
                            appendToErrorMessage("Sending message failed: message=" + message + "\nerrorMessage=" + errorMessage);
                            failed();
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }
}
