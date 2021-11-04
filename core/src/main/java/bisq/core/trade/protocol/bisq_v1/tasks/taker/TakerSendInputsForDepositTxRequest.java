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

package bisq.core.trade.protocol.bisq_v1.tasks.taker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.app.Version;
import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import com.google.common.base.Charsets;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TakerSendInputsForDepositTxRequest extends TradeTask {
    public TakerSendInputsForDepositTxRequest(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            Coin tradeAmount = checkNotNull(trade.getAmount(), "TradeAmount must not be null");
            String takerFeeTxId = checkNotNull(processModel.getTakeOfferFeeTxId(), "TakeOfferFeeTxId must not be null");
            User user = checkNotNull(processModel.getUser(), "User must not be null");
            List<NodeAddress> acceptedArbitratorAddresses = user.getAcceptedArbitratorAddresses() == null ?
                    new ArrayList<>() :
                    user.getAcceptedArbitratorAddresses();
            List<NodeAddress> acceptedMediatorAddresses = user.getAcceptedMediatorAddresses();
            List<NodeAddress> acceptedRefundAgentAddresses = user.getAcceptedRefundAgentAddresses() == null ?
                    new ArrayList<>() :
                    user.getAcceptedRefundAgentAddresses();
            // We don't check for arbitrators as they should vanish soon
            checkNotNull(acceptedMediatorAddresses, "acceptedMediatorAddresses must not be null");
            // We also don't check for refund agents yet as we don't want to restrict us too much. They are not mandatory.

            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            Optional<AddressEntry> optionalMultiSigAddressEntry = walletService.getAddressEntry(id,
                    AddressEntry.Context.MULTI_SIG);
            checkArgument(optionalMultiSigAddressEntry.isPresent(),
                    "MULTI_SIG addressEntry must have been already set here.");
            AddressEntry multiSigAddressEntry = optionalMultiSigAddressEntry.get();
            byte[] takerMultiSigPubKey = multiSigAddressEntry.getPubKey();
            processModel.setMyMultiSigPubKey(takerMultiSigPubKey);

            Optional<AddressEntry> optionalPayoutAddressEntry = walletService.getAddressEntry(id,
                    AddressEntry.Context.TRADE_PAYOUT);
            checkArgument(optionalPayoutAddressEntry.isPresent(),
                    "TRADE_PAYOUT multiSigAddressEntry must have been already set here.");
            AddressEntry payoutAddressEntry = optionalPayoutAddressEntry.get();
            String takerPayoutAddressString = payoutAddressEntry.getAddressString();

            String offerId = processModel.getOfferId();

            // From 1.7.0 on we do not send the payment account data but only the hash.
            // For backward compatibility we still keep the fields but set it to null
            byte[] hashOfTakersPaymentAccountPayload = ProcessModel.hashOfPaymentAccountPayload(processModel.getPaymentAccountPayload(trade));

            // We still send the signatureOfNonce below to not get too many changes. It will be needed later but it
            // does no harm to have that data earlier.

            // Taker has to use offerId as nonce (he cannot manipulate that - so we avoid to have a challenge
            // protocol for passing the nonce we want to get signed)
            // This is used for verifying the peers account age witness
            byte[] signatureOfNonce = Sig.sign(processModel.getKeyRing().getSignatureKeyPair().getPrivate(),
                    offerId.getBytes(Charsets.UTF_8));

            String takersPaymentMethodId = checkNotNull(processModel.getPaymentAccountPayload(trade)).getPaymentMethodId();
            InputsForDepositTxRequest request = new InputsForDepositTxRequest(
                    offerId,
                    processModel.getMyNodeAddress(),
                    tradeAmount.value,
                    trade.getPrice().getValue(),
                    trade.getTradeTxFee().getValue(),
                    trade.getTakerFee().getValue(),
                    trade.isCurrencyForTakerFeeBtc(),
                    processModel.getRawTransactionInputs(),
                    processModel.getChangeOutputValue(),
                    processModel.getChangeOutputAddress(),
                    takerMultiSigPubKey,
                    takerPayoutAddressString,
                    processModel.getPubKeyRing(),
                    null,
                    processModel.getAccountId(),
                    takerFeeTxId,
                    acceptedArbitratorAddresses,
                    acceptedMediatorAddresses,
                    acceptedRefundAgentAddresses,
                    trade.getArbitratorNodeAddress(),
                    trade.getMediatorNodeAddress(),
                    trade.getRefundAgentNodeAddress(),
                    UUID.randomUUID().toString(),
                    Version.getP2PMessageVersion(),
                    signatureOfNonce,
                    new Date().getTime(),
                    hashOfTakersPaymentAccountPayload,
                    takersPaymentMethodId);
            log.info("Send {} with offerId {} and uid {} to peer {}",
                    request.getClass().getSimpleName(), request.getTradeId(),
                    request.getUid(), trade.getTradingPeerNodeAddress());

            processModel.getTradeManager().requestPersistence();

            processModel.getP2PService().sendEncryptedDirectMessage(
                    trade.getTradingPeerNodeAddress(),
                    processModel.getTradePeer().getPubKeyRing(),
                    request,
                    new SendDirectMessageListener() {
                        public void onArrived() {
                            log.info("{} arrived at peer: offerId={}; uid={}",
                                    request.getClass().getSimpleName(), request.getTradeId(), request.getUid());
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("Sending {} failed: uid={}; peer={}; error={}",
                                    request.getClass().getSimpleName(), request.getUid(),
                                    trade.getTradingPeerNodeAddress(), errorMessage);
                            appendToErrorMessage("Sending message failed: message=" + request + "\nerrorMessage=" + errorMessage);
                            failed();
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }
}
