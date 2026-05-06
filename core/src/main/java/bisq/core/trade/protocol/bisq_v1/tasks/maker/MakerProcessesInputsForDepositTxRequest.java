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
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

import com.google.common.base.Charsets;

import java.security.PublicKey;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.TradeValidation.*;
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
            PriceFeedService priceFeedService = processModel.getTradeManager().getPriceFeedService();

            tradingPeer.setHashOfPaymentAccountPayload(request.getHashOfTakersPaymentAccountPayload());
            tradingPeer.setPaymentMethodId(request.getTakersPaymentMethodId());

            Coin tradeAmount = checkTradeAmount(request.getTradeAmountAsCoin(), offer.getMinAmount(), offer.getAmount());
            trade.setAmount(tradeAmount);

            List<RawTransactionInput> takerRawTransactionInputs = checkTakersRawTransactionInputs(request.getRawTransactionInputs(),
                    btcWalletService,
                    offer,
                    trade.getTradeTxFee(),
                    tradeAmount);
            tradingPeer.setRawTransactionInputs(takerRawTransactionInputs);

            byte[] takerMultiSigPubKey = checkMultiSigPubKey(request.getTakerMultiSigPubKey());
            tradingPeer.setMultiSigPubKey(takerMultiSigPubKey);

            String takerPayoutAddressString = checkBitcoinAddress(request.getTakerPayoutAddressString(), btcWalletService);
            tradingPeer.setPayoutAddressString(takerPayoutAddressString);

            PubKeyRing takerPubKeyRing = request.getTakerPubKeyRing();
            tradingPeer.setPubKeyRing(takerPubKeyRing);

            tradingPeer.setAccountId(request.getTakerAccountId());

            int takersBurningManSelectionHeight = checkPeersBurningManSelectionHeight(request.getBurningManSelectionHeight(), delayedPayoutTxReceiverService);
            processModel.setBurningManSelectionHeight(takersBurningManSelectionHeight);

            // We set the taker fee only in the processModel yet not in the trade as the tx was only created but not
            // published yet. Once it was published we move it to trade. The takerFeeTx should be sent in a later
            // message but that cannot be changed due backward compatibility issues. It is a left over from the
            // old trade protocol.
            String takerFeeTxId = checkTransactionId(request.getTakerFeeTxId());
            processModel.setTakeOfferFeeTxId(takerFeeTxId);

            // Taker has to sign offerId (he cannot manipulate that - so we avoid to have a challenge protocol for
            // passing the nonce we want to get signed)
            byte[] accountAgeWitnessNonce = trade.getId().getBytes(Charsets.UTF_8);
            PublicKey takerSignatureKey = takerPubKeyRing.getSignaturePubKey();
            byte[] accountAgeWitnessSignature = checkSignature(request.getAccountAgeWitnessSignatureOfOfferId(),
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

            long takersTradePrice = checkTakersTradePrice(request.getTradePrice(), priceFeedService, offer);
            trade.setPriceAsLong(takersTradePrice);

            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
