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

package bisq.core.trade.protocol.bisq_v5.tasks.maker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.model.bisq_v1.BuyerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v5.messages.InputsForDepositTxResponse_v5;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.crypto.Sig;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;

import java.security.PrivateKey;

import java.util.Arrays;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

// Copy of BuyerAsMakerSendsInputsForDepositTxResponse with added buyersUnsignedWarningTx and buyersWarningTxSignature FIXME: stale comment
@Slf4j
public class MakerSendsInputsForDepositTxResponse_v5 extends TradeTask {
    public MakerSendsInputsForDepositTxResponse_v5(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            TradingPeer tradingPeer = processModel.getTradePeer();
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

            byte[] hashOfMakersPaymentAccountPayload = ProcessModel.hashOfPaymentAccountPayload(processModel.getPaymentAccountPayload(trade));
            String makersPaymentMethodId = checkNotNull(processModel.getPaymentAccountPayload(trade)).getPaymentMethodId();

            String makersWarningTxFeeBumpAddress = processModel.getWarningTxFeeBumpAddress();
            String makersRedirectTxFeeBumpAddress = processModel.getRedirectTxFeeBumpAddress();
            boolean isBuyerMaker = trade instanceof BuyerAsMakerTrade;
            byte[] buyersWarningTxMakerSignature = isBuyerMaker ? processModel.getWarningTxBuyerSignature() : tradingPeer.getWarningTxSellerSignature();
            byte[] sellersWarningTxMakerSignature = isBuyerMaker ? tradingPeer.getWarningTxBuyerSignature() : processModel.getWarningTxSellerSignature();
            byte[] buyersRedirectTxMakerSignature = isBuyerMaker ? processModel.getRedirectTxBuyerSignature() : tradingPeer.getRedirectTxSellerSignature();
            byte[] sellersRedirectTxMakerSignature = isBuyerMaker ? tradingPeer.getRedirectTxBuyerSignature() : processModel.getRedirectTxSellerSignature();

            InputsForDepositTxResponse_v5 message = new InputsForDepositTxResponse_v5(
                    processModel.getOfferId(),
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
                    makersPaymentMethodId,
                    makersWarningTxFeeBumpAddress,
                    makersRedirectTxFeeBumpAddress,
                    buyersWarningTxMakerSignature,
                    sellersWarningTxMakerSignature,
                    buyersRedirectTxMakerSignature,
                    sellersRedirectTxMakerSignature);

            trade.setState(Trade.State.MAKER_SENT_PUBLISH_DEPOSIT_TX_REQUEST);
            processModel.getTradeManager().requestPersistence();
            NodeAddress peersNodeAddress = trade.getTradingPeerNodeAddress();
            log.info("Send {} to peer {}. tradeId={}, uid={}",
                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
            processModel.getP2PService().sendEncryptedDirectMessage(
                    peersNodeAddress,
                    tradingPeer.getPubKeyRing(),
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

    private byte[] getPreparedDepositTx() {
        Transaction preparedDepositTx = processModel.getBtcWalletService().getTxFromSerializedTx(processModel.getPreparedDepositTx());
        // Remove witnesses from preparedDepositTx, so that the peer can still compute the final
        // tx id, but cannot publish it before we have all the finalized staged txs.
        return preparedDepositTx.bitcoinSerialize(false);
    }
}
