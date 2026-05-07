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
import bisq.core.btc.wallet.WalletUtils;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.validation.exceptions.InvalidTxException;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionWitness;
import org.bitcoinj.script.ScriptBuilder;

import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.TransactionValidation.checkTransaction;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
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
        checkArgument(rawTransactionInputs.stream().allMatch(tradeWalletService::isP2WPKH),
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
            checkArgument(walletService.isP2WPKH(input), "%s input must be P2WH", peerRole);
            inputValue = Math.addExact(inputValue, input.value);
        }
        return inputValue;
    }

    public static void validateDepositInputs(Trade trade) throws InvalidTxException {
        // assumption: deposit tx always has 2 inputs, the maker and taker
        if (trade == null || trade.getDepositTx() == null || trade.getDepositTx().getInputs().size() != 2) {
            throw new InvalidTxException("Deposit transaction is null or has unexpected input count");
        }
        Transaction depositTx = trade.getDepositTx();
        String txIdInput0 = depositTx.getInput(0).getOutpoint().getHash().toString();
        String txIdInput1 = depositTx.getInput(1).getOutpoint().getHash().toString();
        String contractMakerTxId = trade.getContract().getOfferPayload().getOfferFeePaymentTxId();
        String contractTakerTxId = trade.getContract().getTakerFeeTxID();
        boolean makerFirstMatch = contractMakerTxId.equalsIgnoreCase(txIdInput0) && contractTakerTxId.equalsIgnoreCase(txIdInput1);
        boolean takerFirstMatch = contractMakerTxId.equalsIgnoreCase(txIdInput1) && contractTakerTxId.equalsIgnoreCase(txIdInput0);
        if (!makerFirstMatch && !takerFirstMatch) {
            String errMsg = "Maker/Taker txId in contract does not match deposit tx input";
            log.error(errMsg +
                    "\nContract Maker tx=" + contractMakerTxId + " Contract Taker tx=" + contractTakerTxId +
                    "\nDeposit Input0=" + txIdInput0 + " Deposit Input1=" + txIdInput1);
            throw new InvalidTxException(errMsg);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Bitcoin tx structural checks
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Bundles the canonical-shape checks the taker runs against the maker's prepared
     * deposit tx: version == 1, lockTime == 0, no input opts in to RBF, and every
     * peer-supplied funding input is P2WPKH. Centralised so both buyer-as-taker and
     * seller-as-taker apply the same policy (single point to extend with future checks).
     */
    public static void assertCanonicalDepositTxShape(Transaction makersDepositTx,
                                                     List<RawTransactionInput> peerInputs,
                                                     NetworkParameters params) throws InvalidTxException {
        assertVersionIsOne(makersDepositTx);
        assertLockTimeIsZero(makersDepositTx);
        assertInputSequencesDisableRbf(makersDepositTx);
        assertAllInputsAreP2WPKH(peerInputs, params);
    }

    /**
     * Throws unless the tx is version 1. Bisq deposit txs are constructed by bitcoinj at the
     * default version (1) and have no need for v2 semantics (no relative-locktime / BIP68
     * use). The version field is part of the serialized tx and therefore part of the txid,
     * so a peer-supplied tx at any other version would still hash differently than what we
     * expect. Rejecting non-v1 keeps the funding path canonical and removes a degree of
     * freedom a peer could otherwise wiggle without us noticing.
     */
    static void assertVersionIsOne(Transaction tx) throws InvalidTxException {
        if (tx.getVersion() != 1) {
            throw new InvalidTxException("Transaction must be version 1, got " + tx.getVersion());
        }
    }

    /**
     * Throws if the tx has a non-zero lockTime. Strictly, lockTime is ignored when every
     * input has a final sequence (0xffffffff), so a non-zero lockTime alone does not always
     * delay mining. We still require lockTime == 0 as a canonical-form / policy invariant:
     * any non-zero value is non-canonical for this path and a peer setting one would be
     * either buggy or trying to deviate from the protocol-specified shape.
     */
    static void assertLockTimeIsZero(Transaction tx) throws InvalidTxException {
        if (tx.getLockTime() != 0) {
            throw new InvalidTxException("Transaction must have lockTime == 0, got " + tx.getLockTime());
        }
    }

    /**
     * Throws unless every input has BIP125 opt-in RBF disabled (sequence == NO_SEQUENCE - 1
     * or NO_SEQUENCE). Any lower value opts in to RBF, which lets a third party (or the peer)
     * replace the broadcast tx with a different one before it confirms — so the txid that
     * lands on chain may not be the txid we signed for in any downstream binding.
     */
    static void assertInputSequencesDisableRbf(Transaction tx) throws InvalidTxException {
        for (TransactionInput txIn : tx.getInputs()) {
            long seq = txIn.getSequenceNumber();
            if (seq != TransactionInput.NO_SEQUENCE - 1 && seq != TransactionInput.NO_SEQUENCE) {
                throw new InvalidTxException("Transaction input has RBF-enabled sequence: " + seq);
            }
        }
    }


    /**
     * Throws unless every supplied funding input refers to a P2WPKH UTXO. Two reasons:
     *   - Legacy P2PKH and P2SH-wrapped scripts carry a malleable scriptSig, which is part
     *     of the txid. A third party can re-sign and rebroadcast with a different txid
     *     before confirmation, breaking any downstream binding to the original txid.
     *   - P2WSH (native segwit) is not malleable in that sense, but it allows arbitrary
     *     peer-controlled script semantics and unpredictable vsize. The Bisq trade-protocol
     *     funding path is canonically P2WPKH; anything else is non-standard for this path.
     */
    static void assertAllInputsAreP2WPKH(List<RawTransactionInput> inputs,
                                                NetworkParameters params) throws InvalidTxException {
        if (inputs == null) {
            throw new InvalidTxException("Funding inputs list must not be null");
        }
        for (int i = 0; i < inputs.size(); i++) {
            RawTransactionInput input = inputs.get(i);
            if (input == null) {
                throw new InvalidTxException("Funding input at position " + i + " must not be null");
            }
            if (!WalletUtils.isP2WPKH(input, params)) {
                // Avoid re-parsing parentTransaction here — it may be malformed (which is
                // exactly why isP2WPKH returned false). Surface only the fields we already
                // hold on the RawTransactionInput.
                throw new InvalidTxException("Funding input at position " + i +
                        " (parent vout=" + input.index + ")" +
                        " is not native segwit P2WPKH (bech32: bc1q on mainnet, tb1q on testnet, bcrt1q on regtest). " +
                        "Bisq v1 trades require P2WPKH UTXOs; legacy P2PKH, P2SH-wrapped, and P2WSH inputs are rejected. " +
                        "Move funds to a native segwit address and retry.");
            }
        }
    }
}
