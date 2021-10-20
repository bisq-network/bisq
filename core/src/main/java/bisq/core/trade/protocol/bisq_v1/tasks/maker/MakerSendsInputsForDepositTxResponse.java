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

package bisq.core.trade.protocol.bisq_v1.tasks.maker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxResponse;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import java.security.PrivateKey;

import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public abstract class MakerSendsInputsForDepositTxResponse extends TradeTask {
    public MakerSendsInputsForDepositTxResponse(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    protected abstract byte[] getPreparedDepositTx();

    @Override
    protected void run() {
        try {
            runInterceptHook();
            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            Optional<AddressEntry> optionalMultiSigAddressEntry = walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            checkArgument(optionalMultiSigAddressEntry.isPresent(), "addressEntry must be set here.");
            AddressEntry makerPayoutAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT);
            byte[] makerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(makerMultiSigPubKey,
                    optionalMultiSigAddressEntry.get().getPubKey()),
                    "makerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            byte[] preparedDepositTx = getPreparedDepositTx();

            // Maker has to use preparedDepositTx as nonce.
            // He cannot manipulate the preparedDepositTx - so we avoid to have a challenge protocol for passing the
            // nonce we want to get signed.
            // This is used for verifying the peers account age witness
            PrivateKey privateKey = processModel.getKeyRing().getSignatureKeyPair().getPrivate();
            byte[] signatureOfNonce = Sig.sign(privateKey, preparedDepositTx);

            // From 1.7.0 on we do not send the payment account data but only the hash.
            // For backward compatibility we still keep the fields but set it to null
            byte[] hashOfMakersPaymentAccountPayload = ProcessModel.hashOfPaymentAccountPayload(processModel.getPaymentAccountPayload(trade));
            String makersPaymentMethodId = checkNotNull(processModel.getPaymentAccountPayload(trade)).getPaymentMethodId();
            InputsForDepositTxResponse message = new InputsForDepositTxResponse(
                    processModel.getOfferId(),
                    null,
                    processModel.getAccountId(),
                    makerMultiSigPubKey,
                    trade.getContractAsJson(),
                    trade.getMakerContractSignature(),
                    makerPayoutAddressEntry.getAddressString(),
                    preparedDepositTx,
                    processModel.getRawTransactionInputs(),
                    processModel.getMyNodeAddress(),
                    UUID.randomUUID().toString(),
                    signatureOfNonce,
                    new Date().getTime(),
                    trade.getLockTime(),
                    hashOfMakersPaymentAccountPayload,
                    makersPaymentMethodId);

            trade.setState(Trade.State.MAKER_SENT_PUBLISH_DEPOSIT_TX_REQUEST);
            processModel.getTradeManager().requestPersistence();
            NodeAddress peersNodeAddress = trade.getTradingPeerNodeAddress();
            log.info("Send {} to peer {}. tradeId={}, uid={}",
                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
            processModel.getP2PService().sendEncryptedDirectMessage(
                    peersNodeAddress,
                    processModel.getTradePeer().getPubKeyRing(),
                    message,
                    new SendDirectMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("{} arrived at peer {}. tradeId={}, uid={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                            trade.setState(Trade.State.MAKER_SAW_ARRIVED_PUBLISH_DEPOSIT_TX_REQUEST);
                            processModel.getTradeManager().requestPersistence();
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("{} failed: Peer {}. tradeId={}, uid={}, errorMessage={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid(), errorMessage);
                            trade.setState(Trade.State.MAKER_SEND_FAILED_PUBLISH_DEPOSIT_TX_REQUEST);
                            appendToErrorMessage("Sending message failed: message=" + message + "\nerrorMessage=" + errorMessage);
                            processModel.getTradeManager().requestPersistence();
                            failed(errorMessage);
                        }
                    }
            );
        } catch (Throwable t) {
            failed(t);
        }
    }
}
