/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.taker;

import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.messages.PayDepositRequest;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.core.user.User;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.SendDirectMessageListener;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

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
            checkNotNull(trade.getTakerFeeTxId(), "TakeOfferFeeTxId must not be null");
            final User user = processModel.getUser();
            checkNotNull(user, "User must not be null");
            final List<NodeAddress> acceptedArbitratorAddresses = user.getAcceptedArbitratorAddresses();
            final List<NodeAddress> acceptedMediatorAddresses = user.getAcceptedMediatorAddresses();
            checkNotNull(acceptedArbitratorAddresses, "acceptedArbitratorAddresses must not be null");
            checkNotNull(acceptedMediatorAddresses, "acceptedMediatorAddresses must not be null");

            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            checkArgument(!walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG).isPresent(),
                "addressEntry must not be set here.");
            AddressEntry addressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            byte[] takerMultiSigPubKey = addressEntry.getPubKey();
            processModel.setMyMultiSigPubKey(takerMultiSigPubKey);

            AddressEntry takerPayoutAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT);
            String takerPayoutAddressString = takerPayoutAddressEntry.getAddressString();

            final String offerId = processModel.getOfferId();

            // Taker has to use offerId as nonce (he cannot manipulate that - so we avoid to have a challenge protocol for passing the nonce we want to get signed)
            // He cannot manipulate the offerId - so we avoid to have a challenge protocol for passing the nonce we want to get signed.
            final PaymentAccountPayload paymentAccountPayload = checkNotNull(processModel.getPaymentAccountPayload(trade), "processModel.getPaymentAccountPayload(trade) must not be null");
            byte[] sig = Sig.sign(processModel.getKeyRing().getSignatureKeyPair().getPrivate(), offerId.getBytes());

            PayDepositRequest message = new PayDepositRequest(
                offerId,
                processModel.getMyNodeAddress(),
                trade.getTradeAmount().value,
                trade.getTradePrice().getValue(),
                trade.getTxFee().getValue(),
                trade.getTakerFee().getValue(),
                trade.isCurrencyForTakerFeeBtc(),
                processModel.getRawTransactionInputs(),
                processModel.getChangeOutputValue(),
                processModel.getChangeOutputAddress(),
                takerMultiSigPubKey,
                takerPayoutAddressString,
                processModel.getPubKeyRing(),
                paymentAccountPayload,
                processModel.getAccountId(),
                trade.getTakerFeeTxId(),
                new ArrayList<>(acceptedArbitratorAddresses),
                new ArrayList<>(acceptedMediatorAddresses),
                trade.getArbitratorNodeAddress(),
                trade.getMediatorNodeAddress(),
                UUID.randomUUID().toString(),
                Version.getP2PMessageVersion(),
                sig,
                new Date().getTime());

            processModel.getP2PService().sendEncryptedDirectMessage(
                trade.getTradingPeerNodeAddress(),
                processModel.getTradingPeer().getPubKeyRing(),
                message,
                new SendDirectMessageListener() {
                    @Override
                    public void onArrived() {
                        log.debug("Message arrived at peer. tradeId={}, message{}", id, message);
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
