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

package bisq.core.trade.protocol.tasks.taker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.InputsForDepositTxRequest;
import bisq.core.trade.protocol.tasks.TradeTask;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.app.Version;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import com.google.common.base.Charsets;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakerSendInputsForDepositTxRequest extends TradeTask {
    @SuppressWarnings({"unused"})
    public TakerSendInputsForDepositTxRequest(TaskRunner taskHandler, Trade trade) {
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
            final List<NodeAddress> acceptedRefundAgentAddresses = user.getAcceptedRefundAgentAddresses();
            // We don't check for arbitrators as they should vanish soon
            checkNotNull(acceptedMediatorAddresses, "acceptedMediatorAddresses must not be null");
            // We also don't check for refund agents yet as we don't want to restict us too much. They are not mandatory.

            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            checkArgument(walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG).isPresent(),
                    "MULTI_SIG addressEntry must have been already set here.");
            AddressEntry addressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            byte[] takerMultiSigPubKey = addressEntry.getPubKey();
            processModel.setMyMultiSigPubKey(takerMultiSigPubKey);

            checkArgument(walletService.getAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT).isPresent(),
                    "TRADE_PAYOUT addressEntry must have been already set here.");
            AddressEntry takerPayoutAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT);
            String takerPayoutAddressString = takerPayoutAddressEntry.getAddressString();

            final String offerId = processModel.getOfferId();

            // Taker has to use offerId as nonce (he cannot manipulate that - so we avoid to have a challenge protocol for passing the nonce we want to get signed)
            // He cannot manipulate the offerId - so we avoid to have a challenge protocol for passing the nonce we want to get signed.
            final PaymentAccountPayload paymentAccountPayload = checkNotNull(processModel.getPaymentAccountPayload(trade), "processModel.getPaymentAccountPayload(trade) must not be null");
            byte[] sig = Sig.sign(processModel.getKeyRing().getSignatureKeyPair().getPrivate(), offerId.getBytes(Charsets.UTF_8));

            InputsForDepositTxRequest message = new InputsForDepositTxRequest(
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
                    acceptedArbitratorAddresses == null ? new ArrayList<>() : new ArrayList<>(acceptedArbitratorAddresses),
                    new ArrayList<>(acceptedMediatorAddresses),
                    acceptedRefundAgentAddresses == null ? new ArrayList<>() : new ArrayList<>(acceptedRefundAgentAddresses),
                    trade.getArbitratorNodeAddress(),
                    trade.getMediatorNodeAddress(),
                    trade.getRefundAgentNodeAddress(),
                    UUID.randomUUID().toString(),
                    Version.getP2PMessageVersion(),
                    sig,
                    new Date().getTime());
            log.info("Send {} with offerId {} and uid {} to peer {}",
                    message.getClass().getSimpleName(), message.getTradeId(),
                    message.getUid(), trade.getTradingPeerNodeAddress());
            processModel.getP2PService().sendEncryptedDirectMessage(
                    trade.getTradingPeerNodeAddress(),
                    processModel.getTradingPeer().getPubKeyRing(),
                    message,
                    new SendDirectMessageListener() {
                        public void onArrived() {
                            log.info("{} arrived at peer: offerId={}; uid={}",
                                    message.getClass().getSimpleName(), message.getTradeId(), message.getUid());
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("Sending {} failed: uid={}; peer={}; error={}",
                                    message.getClass().getSimpleName(), message.getUid(),
                                    trade.getTradingPeerNodeAddress(), errorMessage);
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
