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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.trade.validation;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.offer.Offer;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.ScriptBuilder;

import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.List;

import static bisq.core.trade.validation.TransactionValidation.checkTransaction;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class DepositTxValidation {
    public static final double MAX_TRADE_PRICE_DEVIATION = 1.5;

    private DepositTxValidation() {
    }


    /* --------------------------------------------------------------------- */
    // Deposit transaction
    /* --------------------------------------------------------------------- */

    public static Transaction checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(Transaction depositTx,
                                                                                  Transaction expectedDepositTx,
                                                                                  BtcWalletService btcWalletService) {
        checkNotNull(depositTx, "depositTx must not be null");
        checkNotNull(expectedDepositTx, "expectedDepositTx must not be null");
        checkNotNull(btcWalletService, "btcWalletService must not be null");

        NetworkParameters params = checkNotNull(btcWalletService.getParams(), "params must not be null");
        return checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(depositTx, expectedDepositTx, params);
    }

    @VisibleForTesting
    static Transaction checkDepositTxMatchesIgnoringWitnessesAndScriptSigs(Transaction depositTx,
                                                                           Transaction expectedDepositTx,
                                                                           NetworkParameters params) {
        checkNotNull(depositTx, "depositTx must not be null");
        checkNotNull(expectedDepositTx, "expectedDepositTx must not be null");
        checkNotNull(params, "params must not be null");

        checkTransaction(depositTx);
        checkTransaction(expectedDepositTx);

        byte[] strippedDepositTx = strippedSerializedTransaction(depositTx, params);
        byte[] strippedExpectedDepositTx = strippedSerializedTransaction(expectedDepositTx, params);
        checkArgument(Arrays.equals(strippedDepositTx, strippedExpectedDepositTx),
                "Deposit tx does not match expected deposit tx when witness and scriptSig data is stripped. " +
                        "depositTxId=%s, expectedDepositTxId=%s",
                depositTx.getTxId(),
                expectedDepositTx.getTxId());
        return depositTx;
    }

    private static byte[] strippedSerializedTransaction(Transaction transaction, NetworkParameters params) {
        Transaction strippedTransaction = new Transaction(params, transaction.bitcoinSerialize());
        strippedTransaction.getInputs().forEach(DepositTxValidation::stripWitnessAndScriptSig);
        return strippedTransaction.bitcoinSerialize(false);
    }

    private static void stripWitnessAndScriptSig(TransactionInput input) {
        input.setScriptSig(ScriptBuilder.createEmpty());
        input.setWitness(TransactionWitness.EMPTY);
    }


    /* --------------------------------------------------------------------- */
    // Unsigned transaction
    /* --------------------------------------------------------------------- */

    public static byte[] checkTransactionIsUnsigned(byte[] unsignedSerializedTransaction,
                                                    BtcWalletService btcWalletService) {
        checkNonEmptyBytes(unsignedSerializedTransaction, "unsignedSerializedTransaction");
        checkNotNull(btcWalletService, "btcWalletService must not be null");
        Transaction unsignedTransaction = TransactionValidation.toVerifiedTransaction(unsignedSerializedTransaction, btcWalletService);
        checkArgument(unsignedTransaction.getInputs().stream().noneMatch(TransactionValidation::hasSignatureData),
                "unsignedSerializedTransaction must not be signed");
        return unsignedSerializedTransaction;
    }


    /* --------------------------------------------------------------------- */
    // Raw transaction inputs
    /* --------------------------------------------------------------------- */

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

        validatePeersInputs(takerRawTransactionInputs,
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

        validatePeersInputs(makerRawTransactionInputs,
                expectedMakersInputAmount,
                btcWalletService,
                "Maker");
        return makerRawTransactionInputs;
    }

    public static List<RawTransactionInput> checkRawTransactionInputsAreNotMalleable(List<RawTransactionInput> rawTransactionInputs,
                                                                                     TradeWalletService tradeWalletService) {
        checkNotNull(rawTransactionInputs, "rawTransactionInputs must not be null");
        checkNotNull(tradeWalletService, "tradeWalletService must not be null");
        checkArgument(rawTransactionInputs.stream().allMatch(tradeWalletService::isP2WH),
                "rawTransactionInputs must not be malleable");
        return rawTransactionInputs;
    }

    public static void validatePeersInputs(List<RawTransactionInput> rawTransactionInputs,
                                           Coin expectedInputAmount,
                                           BtcWalletService walletService,
                                           String peerRole) {
        checkNotNull(rawTransactionInputs, "%s raw transaction inputs must not be null", peerRole);
        checkArgument(!rawTransactionInputs.isEmpty(), "%s raw transaction inputs must not be empty", peerRole);
        checkNotNull(walletService, "%s wallet service must not be null", peerRole);
        checkNotNull(expectedInputAmount, "%s expected input value must not be null", peerRole);
        checkArgument(expectedInputAmount.isPositive(), "%s expected input value must be positive", peerRole);

        long inputValueFromTxInputs = getValidatedInputValue(rawTransactionInputs, walletService, peerRole);
        checkArgument(inputValueFromTxInputs == expectedInputAmount.value,
                "%s input value mismatch. inputValueFromTxInputs=%s, expectedInputAmount=%s",
                peerRole, inputValueFromTxInputs, expectedInputAmount.value);
    }

    private static long getValidatedInputValue(List<RawTransactionInput> rawTransactionInputs,
                                               BtcWalletService walletService,
                                               String peerRole) {
        long inputValue = 0;
        for (RawTransactionInput input : rawTransactionInputs) {
            checkNotNull(input, "%s raw transaction input must not be null", peerRole);
            checkArgument(input.value > 0, "%s raw transaction input value must be positive", peerRole);
            input.validate(walletService);
            checkArgument(walletService.isP2WH(input), "%s input must be P2WH", peerRole);
            inputValue = Math.addExact(inputValue, input.value);
        }
        return inputValue;
    }
}
