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

package bisq.core.trade;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferValidation;
import bisq.core.offer.bisq_v1.MarketPriceNotAvailableException;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.bisq_v1.messages.InputsForDepositTxRequest;
import bisq.core.trade.protocol.bisq_v1.tasks.TradePeerTxInputValidator;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.Sig;
import bisq.common.util.Base64;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;

import com.google.common.base.Charsets;

import java.security.PublicKey;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkNonBlankString;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TradeValidation {
    // We have to account for clock drift. We use mostly 2 hours as max drift, but we prefer to be more tolerant here
    public static final long MAX_DATE_DEVIATION = TimeUnit.HOURS.toMillis(4);
    public static final double MAX_TRADE_PRICE_DEVIATION = 1.5; // 50% above or below price
    public static final double MAX_FEE_DEVIATION = 0.5; // 50% above or below peers fee
    public static final int MAX_LOCKTIME_BLOCK_DEVIATION = 3; // Peers latest block height must inside a +/- 3 blocks tolerance to ours.


    /* --------------------------------------------------------------------- */
    // Offer
    /* --------------------------------------------------------------------- */

    public static Coin checkTradeAmount(Coin tradeAmount, Coin offerMinAmount, Coin offerMaxAmount) {
        checkNotNull(tradeAmount, "tradeAmount must not be null");
        checkNotNull(offerMinAmount, "offerMinAmount must not be null");
        checkNotNull(offerMaxAmount, "offerMaxAmount must not be null");

        checkArgument(!tradeAmount.isLessThan(offerMinAmount),
                "Trade amount must not be less than minimum offer amount. tradeAmount=%s, offerMinAmount=%s",
                tradeAmount.toFriendlyString(), offerMinAmount.toFriendlyString());
        checkArgument(!tradeAmount.isGreaterThan(offerMaxAmount),
                "Trade amount must not be higher than maximum offer amount. tradeAmount=%s, offerMaxAmount=%s",
                tradeAmount.toFriendlyString(), offerMaxAmount.toFriendlyString());
        return tradeAmount;
    }

    public static long checkTakersTradePrice(long takersTradePrice,
                                             PriceFeedService priceFeedService,
                                             Offer offer) {
        try {
            offer.verifyTakersTradePrice(takersTradePrice);
            // We allow 50% tolerance to the max allowed price percentage to avoid failing trades in
            // high volatility environments
            OfferValidation.verifyPriceInBounds(priceFeedService, offer, MAX_TRADE_PRICE_DEVIATION);
            return takersTradePrice;
        } catch (TradePriceOutOfToleranceException | MarketPriceNotAvailableException e) {
            throw new RuntimeException(e);
        }
    }


    /* --------------------------------------------------------------------- */
    // Trade
    /* --------------------------------------------------------------------- */

    public static String checkTradeId(String tradeId, TradeMessage tradeMessage) {
        checkArgument(isTradeIdValid(tradeId, tradeMessage), "TradeId %s is not valid", tradeId);
        return tradeId;
    }

    public static boolean isTradeIdValid(String tradeId, TradeMessage tradeMessage) {
        checkNotNull(tradeId, "tradeId must not be null");
        checkNotNull(tradeMessage, "tradeMessage must not be null");
        return tradeId.equals(tradeMessage.getTradeId());
    }

    public static long checkPeersDate(long currentDate) {
        long now = System.currentTimeMillis();
        checkArgument(Math.abs(now - currentDate) <= MAX_DATE_DEVIATION, "currentDate is outside of allowed range.");
        return currentDate;
    }

    public static PubKeyRing getCheckedMediatorPubKeyRing(NodeAddress mediatorNodeAddress, User user) {
        checkNotNull(mediatorNodeAddress, "mediatorNodeAddress must not be null");
        checkNotNull(user, "user must not be null");
        Mediator mediator = checkNotNull(user.getAcceptedMediatorByAddress(mediatorNodeAddress),
                "user.getAcceptedMediatorByAddress(mediatorNodeAddress) must not be null");
        return checkNotNull(mediator.getPubKeyRing(),
                "mediator.getPubKeyRing() must not be null");
    }

    public static int checkPeersBurningManSelectionHeight(int peersBurningManSelectionHeight,
                                                          DelayedPayoutTxReceiverService delayedPayoutTxReceiverService) {
        checkArgument(peersBurningManSelectionHeight > 0,
                "peersBurningManSelectionHeight must be positive");
        checkNotNull(delayedPayoutTxReceiverService, "delayedPayoutTxReceiverService must not be null");

        int myBurningManSelectionHeight = delayedPayoutTxReceiverService.getBurningManSelectionHeight();
        checkArgument(myBurningManSelectionHeight > 0,
                "myBurningManSelectionHeight must be positive");

        if (peersBurningManSelectionHeight != myBurningManSelectionHeight) {
            // Allow SNAPSHOT_SELECTION_GRID_SIZE (10 blocks) as tolerance if traders had different heights.
            int diff = Math.abs(peersBurningManSelectionHeight - myBurningManSelectionHeight);
            checkArgument(diff == DelayedPayoutTxReceiverService.SNAPSHOT_SELECTION_GRID_SIZE,
                    "If Burning Man selection heights are not the same they have to differ by " +
                            "exactly the snapshot grid size, otherwise we fail. " +
                            "peersBurningManSelectionHeight=%s, myBurningManSelectionHeight=%s, diff=%s",
                    peersBurningManSelectionHeight, myBurningManSelectionHeight, diff);

        }
        return peersBurningManSelectionHeight;
    }


    /* --------------------------------------------------------------------- */
    // Bitcoin
    /* --------------------------------------------------------------------- */

    public static byte[] checkMultiSigPubKey(byte[] multiSigPubKey) {
        checkNotNull(multiSigPubKey, "multiSigPubKey must not be null");
        checkArgument(multiSigPubKey.length == 33, "multiSigPubKey must be compressed");

        // Check that the multisig key decompresses to a valid curve point:
        ECKey.fromPublicOnly(multiSigPubKey);
        return multiSigPubKey;
    }

    public static String checkBitcoinAddress(String bitcoinAddress, BtcWalletService btcWalletService) {
        checkNotNull(bitcoinAddress, "bitcoinAddress must not be null");
        checkNotNull(btcWalletService, "btcWalletService must not be null");

        try {
            Address.fromString(btcWalletService.getParams(), bitcoinAddress).getOutputScriptType();
            return bitcoinAddress;
        } catch (AddressFormatException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid bitcoin address: " + bitcoinAddress, e);
        }
    }

    public static byte[] checkSerializedTransaction(byte[] serializedTransaction,
                                                    BtcWalletService btcWalletService) {
        checkNotNull(serializedTransaction, "serializedTransaction must not be null");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        toTransaction(serializedTransaction, btcWalletService);
        return serializedTransaction;
    }

    public static Transaction toTransaction(byte[] serializedTransaction,
                                            BtcWalletService btcWalletService) {
        checkNotNull(serializedTransaction, "serializedTransaction must not be null");
        checkNotNull(btcWalletService, "btcWalletService must not be null");

        try {
            return new Transaction(btcWalletService.getParams(), serializedTransaction);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid serialized transaction", e);
        }
    }

    public static String checkTransactionId(String txId) {
        checkNotNull(txId, "txId must not be null");

        try {
            return Sha256Hash.wrap(txId.toLowerCase(Locale.ROOT)).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid transaction ID: " + txId, e);
        }
    }

    public static long checkLockTime(long lockTime, boolean isBlockchain, BtcWalletService btcWalletService) {
        if (Config.baseCurrencyNetwork().isMainnet()) {
            int myLockTime = btcWalletService.getBestChainHeight() + Restrictions.getLockTime(isBlockchain);
            // We allow a tolerance of 3 blocks as BestChainHeight might be a bit different on maker and taker in
            // case a new block was just found
            checkArgument(Math.abs(lockTime - myLockTime) <= MAX_LOCKTIME_BLOCK_DEVIATION,
                    "Lock time of maker is more than 3 blocks different to the lockTime I " +
                            "calculated. Makers lockTime= " + lockTime + ", myLockTime=" + myLockTime);
        }
        return lockTime;
    }

    public static List<RawTransactionInput> checkTakersRawTransactionInputs(List<RawTransactionInput> takerRawTransactionInputs,
                                                                            BtcWalletService btcWalletService,
                                                                            Offer offer,
                                                                            Coin tradeTxFee,
                                                                            Coin tradeAmount) {
        checkNotNull(takerRawTransactionInputs, "takerRawTransactionInputs must not be null");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        checkNotNull(offer, "offer must not be null");
        checkNotNull(tradeTxFee, "tradeTxFee must not be null");
        checkNotNull(tradeAmount, "tradeAmount must not be null");

        // Taker pays the miner fee for deposit tx and payout tx
        Coin takersDoubleMinerFee = tradeTxFee.multiply(2);
        Coin expectedTakersInputAmount;
        if (offer.isBuyOffer()) {
            // Taker is the seller.
            expectedTakersInputAmount = offer.getSellerSecurityDeposit()
                    .add(tradeAmount)
                    .add(takersDoubleMinerFee);
        } else {
            // Taker is buyer
            expectedTakersInputAmount = offer.getBuyerSecurityDeposit()
                    .add(takersDoubleMinerFee);
        }

        TradePeerTxInputValidator.validatePeersInputs(takerRawTransactionInputs,
                expectedTakersInputAmount,
                btcWalletService,
                "Taker");
        return takerRawTransactionInputs;
    }

    public static List<RawTransactionInput> checkMakersRawTransactionInputs(List<RawTransactionInput> makerRawTransactionInputs,
                                                                            BtcWalletService btcWalletService,
                                                                            Offer offer) {
        checkNotNull(makerRawTransactionInputs, "makerRawTransactionInputs must not be null");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        checkNotNull(offer, "offer must not be null");

        Coin expectedMakersInputAmount;
        if (offer.isBuyOffer()) {
            // maker is the buyer.
            expectedMakersInputAmount = offer.getBuyerSecurityDeposit();
        } else {
            // maker is seller
            // We use the offer amount not the trade amount as we compare with the inputs which come from the
            // makers fee tx which has the reserved funds for the max. offer amount.
            expectedMakersInputAmount = offer.getSellerSecurityDeposit()
                    .add(offer.getAmount());
        }

        TradePeerTxInputValidator.validatePeersInputs(makerRawTransactionInputs,
                expectedMakersInputAmount,
                btcWalletService,
                "Maker");
        return makerRawTransactionInputs;
    }


    /* --------------------------------------------------------------------- */
    // Trade tx fee  (miner fee for taker fee tx, deposit tx and payout tx)
    /* --------------------------------------------------------------------- */

    public static Coin checkTradeTxFee(Coin tradeTxFee) {
        checkNotNull(tradeTxFee, "tradeTxFee must not be null");
        checkTradeTxFee(tradeTxFee.getValue());
        return tradeTxFee;
    }

    public static long checkTradeTxFee(long tradeTxFee) {
        return checkIsPositive(tradeTxFee, "tradeTxFee");
    }

    public static Coin checkTradeTxFeeIsInTolerance(Coin tradeTxFee, FeeService feeService) {
        Coin txFeePerVbyte = feeService.getTxFeePerVbyte();
        Coin expectedTradeTxFee = TradeFeeFactory.getTradeTxFee(txFeePerVbyte);
        return checkTradeTxFeeIsInTolerance(tradeTxFee, expectedTradeTxFee);
    }

    public static Coin checkTradeTxFeeIsInTolerance(Coin tradeTxFee, Coin expectedTradeTxFee) {
        checkNotNull(tradeTxFee, "tradeTxFee must not be null");
        checkNotNull(expectedTradeTxFee, "expectedTradeTxFee must not be null");
        checkTradeTxFeeIsInTolerance(tradeTxFee.getValue(), expectedTradeTxFee.getValue());
        return tradeTxFee;
    }

    public static long checkTradeTxFeeIsInTolerance(long tradeTxFee, long expectedTradeTxFee) {
        return checkFeeIsInTolerance(tradeTxFee, expectedTradeTxFee);
    }


    /* --------------------------------------------------------------------- */
    // Miner fee rate
    /* --------------------------------------------------------------------- */

    public static long checkMinerFeeRateIsInTolerance(long minerFeeRate, long expectedMinerFeeRate) {
        return checkFeeIsInTolerance(minerFeeRate, expectedMinerFeeRate);
    }

    @VisibleForTesting
    static long checkFeeIsInTolerance(long actualValue, long expectedValue) {
        checkIsPositive(actualValue, "actualValue");
        checkIsPositive(expectedValue, "expectedValue");
        double deviation = Math.abs(1 - (double) expectedValue / actualValue);
        checkArgument(deviation < MAX_FEE_DEVIATION,
                "actualValue is outside of allowed tolerance. actualValue=%s, expectedValue=%s, deviation=%s",
                actualValue,
                expectedValue,
                deviation);
        return actualValue;
    }


    /* --------------------------------------------------------------------- */
    // Takers trade fee
    /* --------------------------------------------------------------------- */

    public static Coin checkTakerFee(Coin takerFee, boolean isCurrencyForTakerFeeBtc, Coin tradeAmount) {
        Coin expectedTakerFee = TradeFeeFactory.getTakerFee(isCurrencyForTakerFeeBtc, tradeAmount);
        return checkTakerFee(takerFee, expectedTakerFee);
    }

    public static Coin checkTakerFee(Coin takerFee, Coin expectedTakerFee) {
        checkNotNull(takerFee, "takerFee must not be null");
        checkNotNull(expectedTakerFee, "expectedTakerFee must not be null");
        checkFeeMatchesExpected(takerFee.getValue(), expectedTakerFee.getValue());
        return takerFee;
    }

    public static long checkTakerFee(long takerFee, boolean isCurrencyForTakerFeeBtc, Coin tradeAmount) {
        long expectedTakerFee = TradeFeeFactory.getTakerFee(isCurrencyForTakerFeeBtc, tradeAmount).value;
        return checkFeeMatchesExpected(takerFee, expectedTakerFee);
    }

    @VisibleForTesting
    static long checkFeeMatchesExpected(long fee, long expectedFee) {
        checkIsPositive(fee, "fee");
        checkIsPositive(expectedFee, "expectedFee");

        checkArgument(fee == expectedFee,
                "fee does not match expected fee. fee=%s, expectedFee=%s",
                fee,
                expectedFee);
        return fee;
    }


    /* --------------------------------------------------------------------- */
    // Makers trade fee
    /* --------------------------------------------------------------------- */

    public static Coin checkMakerFee(Coin makerFee, boolean isCurrencyForMakerFeeBtc, Coin tradeAmount) {
        Coin expectedMakerFee = TradeFeeFactory.getMakerFee(isCurrencyForMakerFeeBtc, tradeAmount);
        return checkMakerFee(makerFee, expectedMakerFee);
    }

    public static Coin checkMakerFee(Coin makerFee, Coin expectedMakerFee) {
        checkNotNull(makerFee, "makerFee must not be null");
        checkNotNull(expectedMakerFee, "expectedMakerFee must not be null");
        checkFeeMatchesExpected(makerFee.getValue(), expectedMakerFee.getValue());
        return makerFee;
    }

    public static long checkMakerFee(long makerFee, boolean isCurrencyForMakerFeeBtc, Coin tradeAmount) {
        long expectedMakerFee = TradeFeeFactory.getMakerFee(isCurrencyForMakerFeeBtc, tradeAmount).value;
        return checkFeeMatchesExpected(makerFee, expectedMakerFee);
    }



    /* --------------------------------------------------------------------- */
    // Crypto
    /* --------------------------------------------------------------------- */

    public static String checkBase64Signature(String signatureBase64) {
        byte[] encodedSignature = toEncodedSignature(signatureBase64);
        return Base64.encode(encodedSignature);
    }

    public static byte[] toEncodedSignature(String signatureBase64) {
        checkNotNull(signatureBase64, "signatureBase64 must not be null");
        checkNonBlankString(signatureBase64, "signatureBase64");
        return Base64.decode(signatureBase64);
    }

    public static byte[] checkSignature(byte[] signature,
                                        byte[] message,
                                        PublicKey signaturePubKey) {
        checkNonEmptyBytes(signature, "signature");
        checkNonEmptyBytes(message, "message");
        checkNotNull(signaturePubKey, "signaturePubKey must not be null");

        try {
            checkArgument(Sig.verify(signaturePubKey, message, signature), "Invalid signature");
            return signature;
        } catch (CryptoException e) {
            throw new IllegalArgumentException("Invalid signature", e);
        }
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

        Coin tradeAmount = checkTradeAmount(request.getTradeAmountAsCoin(), offer.getMinAmount(), offer.getAmount());
        Coin tradeTxFee = checkTradeTxFeeIsInTolerance(request.getTxFeeAsCoin(), feeService);
        checkTakersRawTransactionInputs(request.getRawTransactionInputs(),
                btcWalletService,
                offer,
                tradeTxFee,
                tradeAmount);
        checkMultiSigPubKey(request.getTakerMultiSigPubKey());
        checkBitcoinAddress(request.getTakerPayoutAddressString(), btcWalletService);
        PubKeyRing takerPubKeyRing = request.getTakerPubKeyRing();
        checkPeersBurningManSelectionHeight(request.getBurningManSelectionHeight(), delayedPayoutTxReceiverService);
        checkTransactionId(request.getTakerFeeTxId());
        byte[] accountAgeWitnessNonce = offer.getId().getBytes(Charsets.UTF_8);
        PublicKey takerSignatureKey = takerPubKeyRing.getSignaturePubKey();
        checkSignature(request.getAccountAgeWitnessSignatureOfOfferId(),
                accountAgeWitnessNonce,
                takerSignatureKey);
        checkPeersDate(request.getCurrentDate());
        NodeAddress mediatorNodeAddress = request.getMediatorNodeAddress();
        getCheckedMediatorPubKeyRing(mediatorNodeAddress, user);
        checkTakersTradePrice(request.getTradePrice(), priceFeedService, offer);
        checkTakerFee(request.getTakerFeeAsCoin(), request.isCurrencyForTakerFeeBtc(), tradeAmount);
        return request;
    }
}
