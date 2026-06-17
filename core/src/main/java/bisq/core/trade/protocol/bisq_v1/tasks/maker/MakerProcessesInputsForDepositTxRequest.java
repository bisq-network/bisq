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

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.offer.Offer;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.validation.DelayedPayoutTxValidation;
import bisq.core.trade.validation.DepositTxValidation;
import bisq.core.trade.validation.MinerFeeValidation;
import bisq.core.trade.validation.TradeAmountValidation;
import bisq.core.trade.validation.TradeFeeValidation;
import bisq.core.trade.validation.TradePriceValidation;
import bisq.core.trade.validation.TransactionValidation;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import com.google.common.base.Charsets;

import java.security.PublicKey;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.DsaSignatureValidation.checkDSASignature;
import static bisq.core.trade.validation.TradeValidation.checkPeersDate;
import static bisq.core.trade.validation.TradeValidation.getCheckedMediatorPubKeyRing;
import static bisq.core.trade.validation.TradeValidation.getCheckedRefundAgentPubKeyRing;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class MakerProcessesInputsForDepositTxRequest extends TradeTask {
    public MakerProcessesInputsForDepositTxRequest(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            InputsForDepositTxRequest request = (InputsForDepositTxRequest) processModel.getTradeMessage();
            checkNotNull(request);

            TradingPeer tradingPeer = processModel.getTradePeer();
            Offer offer = checkNotNull(trade.getOffer(), "Offer must not be null");
            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            DelayedPayoutTxReceiverService delayedPayoutTxReceiverService = processModel.getDelayedPayoutTxReceiverService();
            User user = checkNotNull(processModel.getUser(), "User must not be null");
            PriceFeedService priceFeedService = processModel.getPriceFeedService();
            FeeService feeService = processModel.getFeeService();

            tradingPeer.setHashOfPaymentAccountPayload(request.getHashOfTakersPaymentAccountPayload());
            tradingPeer.setPaymentMethodId(request.getTakersPaymentMethodId());

            Coin tradeAmount = TradeAmountValidation.checkTradeAmount(request.getTradeAmountAsCoin(), offer.getMinAmount(), offer.getAmount());
            Coin tradeTxFee = MinerFeeValidation.checkTradeTxFeeIsInTolerance(request.getTxFeeAsCoin(), feeService);
            checkArgument(tradeTxFee.equals(trade.getTradeTxFee()),
                    "Taker's tx fee from message (%s) must match the expected trade tx fee (%s)",
                    tradeTxFee.toFriendlyString(), trade.getTradeTxFee().toFriendlyString());

            TradeFeeValidation.checkTakerFee(request.getTakerFeeAsCoin(), request.isCurrencyForTakerFeeBtc(), tradeAmount);

            trade.setAmount(tradeAmount);

            List<RawTransactionInput> takerRawTransactionInputs = DepositTxValidation.checkTakersRawTransactionInputs(request.getRawTransactionInputs(),
                    btcWalletService,
                    offer,
                    tradeTxFee,
                    tradeAmount);
            tradingPeer.setRawTransactionInputs(takerRawTransactionInputs);

            byte[] takerMultiSigPubKey = TransactionValidation.checkMultiSigPubKey(request.getTakerMultiSigPubKey());
            tradingPeer.setMultiSigPubKey(takerMultiSigPubKey);

            String takerPayoutAddressString = TransactionValidation.checkBitcoinAddress(request.getTakerPayoutAddressString(), btcWalletService);
            tradingPeer.setPayoutAddressString(takerPayoutAddressString);

            PubKeyRing takerPubKeyRing = request.getTakerPubKeyRing();
            tradingPeer.setPubKeyRing(takerPubKeyRing);

            tradingPeer.setAccountId(request.getTakerAccountId());

            int takersBurningManSelectionHeight = DelayedPayoutTxValidation.checkBurningManSelectionHeight(request.getBurningManSelectionHeight(), delayedPayoutTxReceiverService);
            processModel.setBurningManSelectionHeight(takersBurningManSelectionHeight);
            List<Integer> supportedBurningManAddressListVersions = request.getSupportedBurningManAddressListVersions();
            tradingPeer.setSupportedBurningManAddressListVersions(supportedBurningManAddressListVersions);
            int burningManAddressListVersion = delayedPayoutTxReceiverService.selectBurningManAddressListVersion(
                    supportedBurningManAddressListVersions);
            processModel.setBurningManAddressListVersion(burningManAddressListVersion);

            // We set the taker fee only in the processModel yet not in the trade as the tx was only created but not
            // published yet. Once it was published we move it to trade. The takerFeeTx should be sent in a later
            // message but that cannot be changed due backward compatibility issues. It is a left over from the
            // old trade protocol.
            String takerFeeTxId = TransactionValidation.checkTransactionId(request.getTakerFeeTxId());
            processModel.setTakeOfferFeeTxId(takerFeeTxId);

            // Taker has to sign offerId (he cannot manipulate that - so we avoid to have a challenge protocol for
            // passing the nonce we want to get signed)
            byte[] accountAgeWitnessNonce = trade.getId().getBytes(Charsets.UTF_8);
            PublicKey takerSignatureKey = takerPubKeyRing.getSignaturePubKey();
            byte[] accountAgeWitnessSignature = checkDSASignature(request.getAccountAgeWitnessSignatureOfOfferId(),
                    accountAgeWitnessNonce,
                    takerSignatureKey);
            tradingPeer.setAccountAgeWitnessSignature(accountAgeWitnessSignature);

            tradingPeer.setAccountAgeWitnessNonce(accountAgeWitnessNonce);

            long currentDate = checkPeersDate(request.getCurrentDate());
            tradingPeer.setCurrentDate(currentDate);

            NodeAddress mediatorNodeAddress = request.getMediatorNodeAddress();
            trade.setMediatorNodeAddress(mediatorNodeAddress);

            PubKeyRing mediatorPubKeyRing = getCheckedMediatorPubKeyRing(mediatorNodeAddress, user);
            trade.setMediatorPubKeyRing(mediatorPubKeyRing);

            boolean contractDisputeAgentPubKeysSupported = request.hasDisputeAgentPubKeyRings();
            boolean contractDisputeAgentPubKeyVersionRequired =
                    Contract.requiresDisputeAgentPubKeyVersion(trade.getTakeOfferDate());
            if (contractDisputeAgentPubKeysSupported) {
                checkArgument(mediatorPubKeyRing.equals(request.getMediatorPubKeyRing()),
                        "Mediator pubKeyRing from request must match local mediator pubKeyRing");

                PubKeyRing refundAgentPubKeyRing = getCheckedRefundAgentPubKeyRing(trade.getRefundAgentNodeAddress(),
                        processModel.getRefundAgentManager());
                trade.setRefundAgentPubKeyRing(refundAgentPubKeyRing);
                checkArgument(refundAgentPubKeyRing.equals(request.getRefundAgentPubKeyRing()),
                        "Refund agent pubKeyRing from request must match local refund agent pubKeyRing");
            } else if (contractDisputeAgentPubKeyVersionRequired) {
                throw new IllegalArgumentException("InputsForDepositTxRequest must include dispute agent pubKeyRings");
            } else {
                // TODO Remove this legacy contract fallback after dispute-agent pub key activation.
                log.info("Processing legacy contract request without dispute agent pubKeyRings for trade {}",
                        trade.getId());
            }
            tradingPeer.setContractDisputeAgentPubKeysSupported(contractDisputeAgentPubKeysSupported);

            long takersTradePrice = TradePriceValidation.checkTakersTradePrice(request.getTradePrice(), priceFeedService, offer);
            trade.setPriceAsLong(takersTradePrice);

            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
