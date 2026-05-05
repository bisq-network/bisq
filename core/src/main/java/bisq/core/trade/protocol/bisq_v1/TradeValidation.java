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

package bisq.core.trade.protocol.bisq_v1;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.exceptions.TradePriceOutOfToleranceException;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferValidation;
import bisq.core.offer.bisq_v1.MarketPriceNotAvailableException;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.trade.model.bisq_v1.Trade;
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

import java.security.PublicKey;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TradeValidation {
    public static final long MAX_DATE_DEVIATION = TimeUnit.HOURS.toMillis(4);
    public static final double MAX_TRADE_PRICE_DEVIATION = 1.5;
    public static final int MAX_LOCKTIME_BLOCK_DEVIATION = 3;

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

    public static String checkBase64Signature(String signatureBase64) {
        toEncodedSignature(signatureBase64);
        return signatureBase64;
    }

    public static byte[] toEncodedSignature(String signatureBase64) {
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

    public static List<RawTransactionInput> checkTakersRawTransactionInputs(List<RawTransactionInput> takerRawTransactionInputs,
                                                                            BtcWalletService btcWalletService,
                                                                            Trade trade,
                                                                            Coin tradeAmount
    ) {
        checkNotNull(takerRawTransactionInputs, "takerRawTransactionInputs must not be null");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        checkNotNull(trade, "trade must not be null");
        checkNotNull(tradeAmount, "tradeAmount must not be null");

        Offer offer = trade.getOffer();

        // Taker pays the miner fee for deposit tx and payout tx
        Coin takersDoubleMinerFee = trade.getTradeTxFee().multiply(2);
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

    public static Coin checkTxFee(Coin txFee) {
        checkTxFee(txFee.getValue());
        return txFee;
    }

    public static long checkTxFee(long txFee) {
        //todo add more narrow checks
        checkArgument(txFee > 0, "txFee must be positive");
        return txFee;
    }


    public static Coin checkTakerFee(Coin takerFee) {
        checkTakerFee(takerFee.getValue());
        return takerFee;
    }

    public static long checkTakerFee(long takerFee) {
        //todo add more narrow checks
        checkArgument(takerFee > 0, "takerFee must be positive");
        return takerFee;
    }

}
