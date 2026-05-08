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

package bisq.core.trade.validation;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.offer.Offer;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;

import org.bitcoinj.core.Coin;

import com.google.common.base.Charsets;

import java.security.PublicKey;

import static bisq.core.trade.validation.DsaSignatureValidation.checkDSASignature;
import static bisq.core.trade.validation.TradeValidation.checkPeersDate;
import static com.google.common.base.Preconditions.checkNotNull;

public final class InputsForDepositTxRequestValidation {
    private InputsForDepositTxRequestValidation() {
    }

    /* --------------------------------------------------------------------- */
    // InputsForDepositTxRequest
    /* --------------------------------------------------------------------- */

    public static InputsForDepositTxRequest checkInputsForDepositTxRequest(InputsForDepositTxRequest request,
                                                                           Offer offer,
                                                                           User user,
                                                                           BtcWalletService btcWalletService,
                                                                           PriceFeedService priceFeedService,
                                                                           DelayedPayoutTxReceiverService delayedPayoutTxReceiverService,
                                                                           FeeService feeService) {
        checkNotNull(request, "request must not be null");
        checkNotNull(offer, "offer must not be null");
        checkNotNull(user, "user must not be null");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        checkNotNull(priceFeedService, "priceFeedService must not be null");
        checkNotNull(delayedPayoutTxReceiverService, "delayedPayoutTxReceiverService must not be null");
        checkNotNull(feeService, "feeService must not be null");

        String checkedOfferId = TradeValidation.checkTradeId(offer.getId(), request);
        Coin tradeAmount = TradeAmountValidation.checkTradeAmount(request.getTradeAmountAsCoin(), offer.getMinAmount(), offer.getAmount());
        Coin tradeTxFee = MinerFeeValidation.checkTradeTxFeeIsInTolerance(request.getTxFeeAsCoin(), feeService);
        DepositTxValidation.checkTakersRawTransactionInputs(request.getRawTransactionInputs(),
                btcWalletService,
                offer,
                tradeTxFee,
                tradeAmount);
        TransactionValidation.checkMultiSigPubKey(request.getTakerMultiSigPubKey());
        TransactionValidation.checkBitcoinAddress(request.getTakerPayoutAddressString(), btcWalletService);
        PubKeyRing takerPubKeyRing = checkNotNull(request.getTakerPubKeyRing(),
                "takerPubKeyRing must not be null");
        DelayedPayoutTxValidation.checkBurningManSelectionHeight(request.getBurningManSelectionHeight(), delayedPayoutTxReceiverService);
        TransactionValidation.checkTransactionId(request.getTakerFeeTxId());
        byte[] accountAgeWitnessNonce = checkedOfferId.getBytes(Charsets.UTF_8);
        PublicKey takerSignatureKey = checkNotNull(takerPubKeyRing.getSignaturePubKey(),
                "takerSignatureKey must not be null");
        checkDSASignature(request.getAccountAgeWitnessSignatureOfOfferId(),
                accountAgeWitnessNonce,
                takerSignatureKey);
        checkPeersDate(request.getCurrentDate());
        NodeAddress mediatorNodeAddress = request.getMediatorNodeAddress();
        TradeValidation.getCheckedMediatorPubKeyRing(mediatorNodeAddress, user);
        TradePriceValidation.checkTakersTradePrice(request.getTradePrice(), priceFeedService, offer);
        TradeFeeValidation.checkTakerFee(request.getTakerFeeAsCoin(), request.isCurrencyForTakerFeeBtc(), tradeAmount);
        return request;
    }
}
