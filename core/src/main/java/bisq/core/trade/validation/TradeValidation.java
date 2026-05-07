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

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.offer.Offer;
import bisq.core.support.dispute.mediation.mediator.Mediator;
import bisq.core.trade.protocol.TradeMessage;
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
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.VerificationException;

import com.google.common.annotations.VisibleForTesting;

import java.security.PublicKey;

import java.math.BigInteger;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.util.Validator.checkIsPositive;
import static bisq.core.util.Validator.checkNonBlankString;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public final class TradeValidation {
    // We have to account for clock drift. We use mostly 2 hours as max drift, but we prefer to be more tolerant here
    public static final long MAX_DATE_DEVIATION = TimeUnit.HOURS.toMillis(4);
    public static final int MAX_LOCKTIME_BLOCK_DEVIATION = 3; // Peers latest block height must inside a +/- 3 blocks tolerance to ours.
    public static final double MAX_TRADE_PRICE_DEVIATION = 1.5;
    public static final double MAX_FEE_DEVIATION_FACTOR = 2; // Max change by factor 2 (expected / 2 or expected * 2)
    public static final double MAX_MAKER_FEE_DEVIATION_FACTOR = 2; // Max change by factor 2 (expected / 2 or expected * 2)
    public static final double MAX_TAKER_FEE_DEVIATION_FACTOR = 1.5; // Max change by factor 1.5 (expected / 1.5 or expected * 1.5)

    private TradeValidation() {
    }


    /* --------------------------------------------------------------------- */
    // Trade
    /* --------------------------------------------------------------------- */

    public static String checkTradeId(String tradeId, TradeMessage tradeMessage) {
        checkArgument(isTradeIdValid(tradeId, tradeMessage), "TradeId %s is not valid", tradeId);
        return tradeId;
    }

    public static boolean isTradeIdValid(String tradeId, TradeMessage tradeMessage) {
        checkNonBlankString(tradeId, "tradeId");
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


    /* --------------------------------------------------------------------- */
    // Bitcoin
    /* --------------------------------------------------------------------- */

    public static String checkBitcoinAddress(String bitcoinAddress, BtcWalletService btcWalletService) {
        checkNonBlankString(bitcoinAddress, "bitcoinAddress");
        checkNotNull(btcWalletService, "btcWalletService must not be null");

        try {
            Address.fromString(btcWalletService.getParams(), bitcoinAddress).getOutputScriptType();
            return bitcoinAddress;
        } catch (AddressFormatException | IllegalStateException e) {
            throw new IllegalArgumentException("Invalid bitcoin address: " + bitcoinAddress, e);
        }
    }

    public static byte[] checkTransactionIsUnsigned(byte[] unsignedSerializedTransaction,
                                                    BtcWalletService btcWalletService) {
        checkNonEmptyBytes(unsignedSerializedTransaction, "unsignedSerializedTransaction");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        Transaction unsignedTransaction = toVerifiedTransaction(unsignedSerializedTransaction, btcWalletService);
        checkArgument(unsignedTransaction.getInputs().stream().noneMatch(TradeValidation::hasSignatureData),
                "unsignedSerializedTransaction must not be signed");
        return unsignedSerializedTransaction;
    }

    @VisibleForTesting
    static boolean hasSignatureData(TransactionInput input) {
        return input.getScriptBytes().length > 0 || input.hasWitness();
    }

    public static byte[] checkDerEncodedEcdsaSignature(byte[] bitcoinSignature) {
        checkNonEmptyBytes(bitcoinSignature, "bitcoinSignature");
        try {
            ECKey.ECDSASignature signature = ECKey.ECDSASignature.decodeFromDER(bitcoinSignature);
            checkArgument(Arrays.equals(bitcoinSignature, signature.encodeToDER()),
                    "bitcoinSignature must be strictly DER encoded");
            checkArgument(isValidSignatureValue(signature.r),
                    "bitcoinSignature r value is outside of allowed range");
            checkArgument(isValidSignatureValue(signature.s),
                    "bitcoinSignature s value is outside of allowed range");
            checkArgument(signature.isCanonical(),
                    "bitcoinSignature must use low-S canonical encoding");
            return bitcoinSignature;
        } catch (SignatureDecodeException e) {
            throw new IllegalArgumentException("Invalid bitcoin signature", e);
        }
    }

    @VisibleForTesting
    static boolean isValidSignatureValue(BigInteger value) {
        return value.signum() > 0 && value.compareTo(ECKey.CURVE.getN()) < 0;
    }

    public static Transaction checkTransaction(Transaction transaction) {
        checkNotNull(transaction, "transaction must not be null");
        try {
            transaction.verify();
        } catch (VerificationException e) {
            throw new IllegalArgumentException("Invalid transaction", e);
        }
        return transaction;
    }

    public static byte[] checkSerializedTransaction(byte[] serializedTransaction,
                                                    BtcWalletService btcWalletService) {
        checkNonEmptyBytes(serializedTransaction, "serializedTransaction");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        toVerifiedTransaction(serializedTransaction, btcWalletService);
        return serializedTransaction;
    }

    public static Transaction toVerifiedTransaction(byte[] serializedTransaction,
                                                    BtcWalletService btcWalletService) {
        checkNonEmptyBytes(serializedTransaction, "serializedTransaction");
        checkNotNull(btcWalletService, "btcWalletService must not be null");

        try {
            Transaction transaction = new Transaction(btcWalletService.getParams(), serializedTransaction);
            transaction.verify();
            return transaction;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid serialized transaction", e);
        }
    }

    public static String checkTransactionId(String txId) {
        checkNonBlankString(txId, "txId");

        try {
            return Sha256Hash.wrap(txId.toLowerCase(Locale.ROOT)).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid transaction ID: " + txId, e);
        }
    }

    public static long checkLockTime(long lockTime, boolean isAltcoin, BtcWalletService btcWalletService) {
        return checkLockTime(lockTime, isAltcoin, btcWalletService, Config.baseCurrencyNetwork().isMainnet());
    }

    @VisibleForTesting
    static long checkLockTime(long lockTime,
                              boolean isAltcoin,
                              BtcWalletService btcWalletService,
                              boolean isMainnet) {
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        checkIsPositive(lockTime, "lockTime");

        // For regtest dev testing we use shorter lock times and skip the test
        if (isMainnet) {
            int expectedUnlockHeight = btcWalletService.getBestChainHeight() + Restrictions.getLockTime(isAltcoin);
            // We allow a tolerance of 3 blocks as BestChainHeight might be a bit different on maker and taker in
            // case a new block was just found
            checkArgument(Math.abs(lockTime - expectedUnlockHeight) <= MAX_LOCKTIME_BLOCK_DEVIATION,
                    "Lock time of maker is more than 3 blocks different to the lockTime I " +
                            "calculated. Makers lockTime= " + lockTime + ", expectedUnlockHeight=" + expectedUnlockHeight);
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

    public static List<RawTransactionInput> checkRawTransactionInputsAreNotMalleable(List<RawTransactionInput> rawTransactionInputs,
                                                                                     TradeWalletService tradeWalletService) {
        checkNotNull(rawTransactionInputs, "rawTransactionInputs must not be null");
        checkNotNull(tradeWalletService, "tradeWalletService must not be null");
        checkArgument(rawTransactionInputs.stream().allMatch(tradeWalletService::isP2WPKH),
                "rawTransactionInputs must be canonical funding inputs (P2WPKH only)");
        return rawTransactionInputs;
    }




    /* --------------------------------------------------------------------- */
    // Crypto
    /* --------------------------------------------------------------------- */

    public static String checkBase64Signature(String signatureBase64) {
        checkNonBlankString(signatureBase64, "signatureBase64");
        toDecodedSignature(signatureBase64);
        return signatureBase64;
    }

    public static byte[] toDecodedSignature(String signatureBase64) {
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

}
