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
import bisq.core.btc.wallet.utils.DepositTransactionUtils;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.validation.exceptions.InvalidTxException;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;

import com.google.common.annotations.VisibleForTesting;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static bisq.core.trade.validation.TransactionValidation.checkTransaction;
import static bisq.core.util.Validator.checkNonEmptyBytes;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public final class DepositTxValidation {
    // Practical upper bound for a single parent transaction. Real wallet UTXOs come from txs
    // well under this size; an attacker passing a much larger blob is grief / memory pressure.
    static final int MAX_PARENT_TX_BYTES = 100 * 1024;

    // Cap on the number of inputs we accept from a peer for a single deposit-tx contribution.
    static final int MAX_INPUTS = 100;

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

    public static Transaction checkMakersPreparedDepositTx(Transaction preparedDepositTx,
                                                           Offer offer,
                                                           Coin tradeAmount,
                                                           Coin tradeTxFee,
                                                           List<RawTransactionInput> makerInputs,
                                                           List<RawTransactionInput> takerInputs,
                                                           byte[] makerMultiSigPubKey,
                                                           byte[] takerMultiSigPubKey,
                                                           NetworkParameters params) {
        Transaction checkedPreparedDepositTx = checkTransaction(checkNotNull(preparedDepositTx,
                "preparedDepositTx must not be null"));
        Offer checkedOffer = checkNotNull(offer, "offer must not be null");
        Coin checkedTradeAmount = checkNotNull(tradeAmount, "tradeAmount must not be null");
        Coin checkedTradeTxFee = checkNotNull(tradeTxFee, "tradeTxFee must not be null");
        List<RawTransactionInput> checkedMakerInputs = checkNotNull(makerInputs, "makerInputs must not be null");
        List<RawTransactionInput> checkedTakerInputs = checkNotNull(takerInputs, "takerInputs must not be null");
        byte[] checkedMakerMultiSigPubKey = TransactionValidation.checkMultiSigPubKey(makerMultiSigPubKey);
        byte[] checkedTakerMultiSigPubKey = TransactionValidation.checkMultiSigPubKey(takerMultiSigPubKey);
        NetworkParameters checkedParams = checkNotNull(params, "params must not be null");
        Coin offerMinAmount = checkNotNull(checkedOffer.getMinAmount(), "offer.getMinAmount() must not be null");
        Coin offerAmount = checkNotNull(checkedOffer.getAmount(), "offer.getAmount() must not be null");
        Coin buyerSecurityDeposit = checkNotNull(checkedOffer.getBuyerSecurityDeposit(),
                "offer.getBuyerSecurityDeposit() must not be null");
        Coin sellerSecurityDeposit = checkNotNull(checkedOffer.getSellerSecurityDeposit(),
                "offer.getSellerSecurityDeposit() must not be null");

        checkArgument(!checkedTradeAmount.isLessThan(offerMinAmount),
                "tradeAmount must not be less than offerMinAmount");
        checkArgument(!checkedTradeAmount.isGreaterThan(offerAmount),
                "tradeAmount must not be greater than offerAmount");
        checkArgument(checkedTradeTxFee.isPositive(), "tradeTxFee must be positive");

        checkCanonicalDepositTxShape(checkedPreparedDepositTx,
                DepositTransactionUtils.combinedInputs(checkedMakerInputs, checkedTakerInputs),
                checkedParams);
        checkPreparedDepositTxInputOrder(checkedPreparedDepositTx,
                checkedOffer.isBuyOffer(),
                checkedMakerInputs,
                checkedTakerInputs,
                checkedParams);
        checkPreparedDepositTxInputAmounts(checkedOffer.isBuyOffer(),
                offerAmount,
                buyerSecurityDeposit,
                sellerSecurityDeposit,
                checkedTradeAmount,
                checkedTradeTxFee,
                checkedMakerInputs,
                checkedTakerInputs);

        byte[] buyerPubKey = checkedOffer.isBuyOffer() ? checkedMakerMultiSigPubKey : checkedTakerMultiSigPubKey;
        byte[] sellerPubKey = checkedOffer.isBuyOffer() ? checkedTakerMultiSigPubKey : checkedMakerMultiSigPubKey;
        Coin expectedMsOutputAmount = buyerSecurityDeposit
                .add(sellerSecurityDeposit)
                .add(checkedTradeTxFee)
                .add(checkedTradeAmount);
        checkPreparedDepositTxOutputs(checkedPreparedDepositTx,
                checkedOffer,
                offerAmount,
                sellerSecurityDeposit,
                checkedTradeAmount,
                checkedTradeTxFee,
                checkedMakerInputs,
                checkedTakerInputs,
                expectedMsOutputAmount,
                buyerPubKey,
                sellerPubKey);
        return checkedPreparedDepositTx;
    }


    /**
     * Bundles the canonical-shape checks the taker runs against the maker's prepared
     * deposit tx: version == 1, lockTime == 0, no input opts in to RBF, and every
     * peer-supplied funding input is P2WPKH. Centralised so both buyer-as-taker and
     * seller-as-taker apply the same policy (single point to extend with future checks).
     */
    public static Transaction checkCanonicalDepositTxShape(Transaction transaction,
                                                           List<RawTransactionInput> peerInputs,
                                                           NetworkParameters params) {
        Transaction checkedTransaction = checkCanonicalDepositTxFields(transaction);
        checkAllInputsAreP2WPKH(peerInputs, params);
        return checkedTransaction;
    }

    /**
     * Checks canonical transaction-level fields that must remain true for the
     * final signed deposit tx as well as the prepared tx.
     */
    public static Transaction checkCanonicalDepositTxFields(Transaction transaction) {
        Transaction checkedTransaction = checkVersionIsOne(transaction);
        checkedTransaction = checkLockTimeIsZero(checkedTransaction);
        checkedTransaction = checkInputSequencesDisableRbf(checkedTransaction);
        return checkedTransaction;
    }

    /**
     * Throws unless the tx is version 1. Bisq deposit txs are constructed by bitcoinj at the
     * default version (1) and have no need for v2 semantics (no relative-locktime / BIP68
     * use). The version field is part of the serialized tx and therefore part of the txid,
     * so a peer-supplied tx at any other version would still hash differently than what we
     * expect. Rejecting non-v1 keeps the funding path canonical and removes a degree of
     * freedom a peer could otherwise wiggle without us noticing.
     */
    @VisibleForTesting
    static Transaction checkVersionIsOne(Transaction tx) {
        Transaction checkedTx = checkNotNull(tx, "tx must not be null");
        checkArgument(checkedTx.getVersion() == 1,
                "Transaction must be version 1, got %s",
                checkedTx.getVersion());
        return checkedTx;
    }

    /**
     * Throws if the tx has a non-zero lockTime. Strictly, lockTime is ignored when every
     * input has a final sequence (0xffffffff), so a non-zero lockTime alone does not always
     * delay mining. We still require lockTime == 0 as a canonical-form / policy invariant:
     * any non-zero value is non-canonical for this path and a peer setting one would be
     * either buggy or trying to deviate from the protocol-specified shape.
     */
    @VisibleForTesting
    static Transaction checkLockTimeIsZero(Transaction tx) {
        Transaction checkedTx = checkNotNull(tx, "tx must not be null");
        checkArgument(checkedTx.getLockTime() == 0,
                "Transaction must have lockTime == 0, got %s",
                checkedTx.getLockTime());
        return checkedTx;
    }

    /**
     * Throws unless every input has BIP125 opt-in RBF disabled (sequence == NO_SEQUENCE - 1
     * or NO_SEQUENCE). Any lower value opts in to RBF, which lets a third party (or the peer)
     * replace the broadcast tx with a different one before it confirms — so the txid that
     * lands on chain may not be the txid we signed for in any downstream binding.
     */
    @VisibleForTesting
    static Transaction checkInputSequencesDisableRbf(Transaction tx) {
        Transaction checkedTx = checkNotNull(tx, "tx must not be null");
        for (TransactionInput txIn : checkedTx.getInputs()) {
            long seq = txIn.getSequenceNumber();
            checkArgument(seq == TransactionInput.NO_SEQUENCE - 1 || seq == TransactionInput.NO_SEQUENCE,
                    "Transaction input has RBF-enabled sequence: %s",
                    seq);
        }
        return checkedTx;
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
    @VisibleForTesting
    static List<RawTransactionInput> checkAllInputsAreP2WPKH(List<RawTransactionInput> inputs,
                                                             NetworkParameters params) {
        checkNotNull(inputs, "inputs must not be null");
        checkNotNull(params, "params must not be null");
        for (int listPos = 0; listPos < inputs.size(); listPos++) {
            RawTransactionInput input = inputs.get(listPos);
            checkNotNull(input, "Funding input at position %s must not be null", listPos);
            checkArgument(WalletUtils.isP2WPKH(input, params),
                    "Funding input at position %s (parent vout=%s) is not native segwit P2WPKH " +
                            "(bech32: bc1q on mainnet, tb1q on testnet, bcrt1q on regtest). " +
                            "Bisq v1 trades require P2WPKH UTXOs; legacy P2PKH, P2SH-wrapped, " +
                            "and P2WSH inputs are rejected. Move funds to a native segwit address and retry.",
                    listPos,
                    input.index);
        }
        return inputs;
    }

    public static Trade checkDepositInputs(Trade trade) {
        Trade checkedTrade = checkNotNull(trade, "trade must not be null");
        Transaction depositTx = checkTransaction(checkNotNull(checkedTrade.getDepositTx(),
                "trade.getDepositTx() must not be null"));
        checkArgument(depositTx.getInputs().size() == 2,
                "Deposit transaction has unexpected input count");

        Contract contract = checkNotNull(checkedTrade.getContract(),
                "trade.getContract() must not be null");
        String txIdInput0 = checkNotNull(depositTx.getInput(0).getOutpoint(),
                "depositTx input 0 outpoint must not be null").getHash().toString();
        String txIdInput1 = checkNotNull(depositTx.getInput(1).getOutpoint(),
                "depositTx input 1 outpoint must not be null").getHash().toString();
        String contractMakerTxId = checkNotNull(checkNotNull(contract.getOfferPayload(),
                        "contract.getOfferPayload() must not be null").getOfferFeePaymentTxId(),
                "contract.getOfferPayload().getOfferFeePaymentTxId() must not be null");
        String contractTakerTxId = checkNotNull(contract.getTakerFeeTxID(),
                "contract.getTakerFeeTxID() must not be null");
        boolean makerFirstMatch = contractMakerTxId.equalsIgnoreCase(txIdInput0) &&
                contractTakerTxId.equalsIgnoreCase(txIdInput1);
        boolean takerFirstMatch = contractMakerTxId.equalsIgnoreCase(txIdInput1) &&
                contractTakerTxId.equalsIgnoreCase(txIdInput0);
        checkArgument(makerFirstMatch || takerFirstMatch,
                "Maker/Taker txId in contract does not match deposit tx input. " +
                        "Contract Maker tx=%s Contract Taker tx=%s Deposit Input0=%s Deposit Input1=%s",
                contractMakerTxId,
                contractTakerTxId,
                txIdInput0,
                txIdInput1);
        return checkedTrade;
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

        byte[] strippedDepositTx = DepositTransactionUtils.toSerializedTransactionWithoutWitnessAndScriptSig(depositTx, params);
        byte[] strippedExpectedDepositTx = DepositTransactionUtils.toSerializedTransactionWithoutWitnessAndScriptSig(expectedDepositTx, params);
        checkArgument(Arrays.equals(strippedDepositTx, strippedExpectedDepositTx),
                "Deposit tx does not match expected deposit tx when witness and scriptSig data is stripped. " +
                        "depositTxId=%s, expectedDepositTxId=%s",
                depositTx.getTxId(),
                expectedDepositTx.getTxId());
        return depositTx;
    }

    private static void checkPreparedDepositTxInputOrder(Transaction preparedDepositTx,
                                                         boolean makerIsBuyer,
                                                         List<RawTransactionInput> makerInputs,
                                                         List<RawTransactionInput> takerInputs,
                                                         NetworkParameters params) {
        int expectedInputCount = makerInputs.size() + takerInputs.size();
        checkArgument(preparedDepositTx.getInputs().size() == expectedInputCount,
                "Prepared deposit tx input count mismatch. txInputs=%s, makerInputs=%s, takerInputs=%s",
                preparedDepositTx.getInputs().size(),
                makerInputs.size(),
                takerInputs.size());

        if (makerIsBuyer) {
            checkInputOutpoints(preparedDepositTx, 0, makerInputs, params, "maker");
            checkInputOutpoints(preparedDepositTx, makerInputs.size(), takerInputs, params, "taker");
        } else {
            checkInputOutpoints(preparedDepositTx, 0, takerInputs, params, "taker");
            checkInputOutpoints(preparedDepositTx, takerInputs.size(), makerInputs, params, "maker");
        }
    }

    private static void checkInputOutpoints(Transaction preparedDepositTx,
                                            int startIndex,
                                            List<RawTransactionInput> expectedInputs,
                                            NetworkParameters params,
                                            String inputOwner) {
        for (int i = 0; i < expectedInputs.size(); i++) {
            RawTransactionInput expectedInput = checkNotNull(expectedInputs.get(i),
                    "%s input at position %s must not be null",
                    inputOwner,
                    i);
            TransactionOutPoint expectedOutpoint = WalletUtils.getConnectedOutPoint(expectedInput, params);
            TransactionOutPoint actualOutpoint = preparedDepositTx.getInput(startIndex + i).getOutpoint();
            checkArgument(actualOutpoint.getIndex() == expectedOutpoint.getIndex() &&
                            actualOutpoint.getHash().equals(expectedOutpoint.getHash()),
                    "Prepared deposit tx input %s does not match expected %s input %s",
                    startIndex + i,
                    inputOwner,
                    i);
        }
    }

    private static void checkPreparedDepositTxInputAmounts(boolean makerIsBuyer,
                                                           Coin offerAmount,
                                                           Coin buyerSecurityDeposit,
                                                           Coin sellerSecurityDeposit,
                                                           Coin tradeAmount,
                                                           Coin tradeTxFee,
                                                           List<RawTransactionInput> makerInputs,
                                                           List<RawTransactionInput> takerInputs) {
        Coin expectedMakerInputAmount = makerIsBuyer
                ? buyerSecurityDeposit
                : sellerSecurityDeposit.add(offerAmount);
        Coin makerInputAmount = DepositTransactionUtils.sumInputValues(makerInputs);
        checkArgument(makerInputAmount.equals(expectedMakerInputAmount),
                "Maker input amount mismatch. actual=%s, expected=%s",
                makerInputAmount,
                expectedMakerInputAmount);

        Coin expectedTakerInputAmount = makerIsBuyer
                ? sellerSecurityDeposit.add(tradeAmount).add(tradeTxFee.multiply(2))
                : buyerSecurityDeposit.add(tradeTxFee.multiply(2));
        Coin takerInputAmount = DepositTransactionUtils.sumInputValues(takerInputs);
        checkArgument(takerInputAmount.equals(expectedTakerInputAmount),
                "Taker input amount mismatch. actual=%s, expected=%s",
                takerInputAmount,
                expectedTakerInputAmount);
    }

    private static void checkPreparedDepositTxOutputs(Transaction preparedDepositTx,
                                                      Offer offer,
                                                      Coin offerAmount,
                                                      Coin sellerSecurityDeposit,
                                                      Coin tradeAmount,
                                                      Coin tradeTxFee,
                                                      List<RawTransactionInput> makerInputs,
                                                      List<RawTransactionInput> takerInputs,
                                                      Coin expectedMsOutputAmount,
                                                      byte[] buyerPubKey,
                                                      byte[] sellerPubKey) {
        checkArgument(!preparedDepositTx.getOutputs().isEmpty(),
                "Prepared deposit tx must have at least the multisig output");

        TransactionOutput multisigOutput = preparedDepositTx.getOutput(0);
        checkArgument(multisigOutput.getValue().equals(expectedMsOutputAmount),
                "Prepared deposit tx multisig output amount mismatch. actual=%s, expected=%s",
                multisigOutput.getValue(),
                expectedMsOutputAmount);
        Script expectedMultiSigOutputScript = DepositTransactionUtils.get2of2MultiSigOutputScript(buyerPubKey, sellerPubKey);
        checkArgument(multisigOutput.getScriptPubKey().equals(expectedMultiSigOutputScript),
                "Prepared deposit tx multisig output script does not match expected trade multisig script");

        Coin expectedMakerChange = offer.isBuyOffer()
                ? Coin.ZERO
                : DepositTransactionUtils.sumInputValues(makerInputs)
                .subtract(sellerSecurityDeposit)
                .subtract(tradeAmount);
        checkArgument(!expectedMakerChange.isNegative(), "expectedMakerChange must not be negative");
        checkArgument(offer.isBuyOffer() ||
                        !expectedMakerChange.isGreaterThan(offerAmount.subtract(tradeAmount)),
                "expectedMakerChange must not be greater than remaining offer amount");
        int expectedOutputCount = expectedMakerChange.isZero() ? 1 : 2;
        checkArgument(preparedDepositTx.getOutputs().size() == expectedOutputCount,
                expectedMakerChange.isZero()
                        ? "Maker's preparedDepositTx must not have a change output"
                        : "Maker's preparedDepositTx must have exactly one change output");
        if (!expectedMakerChange.isZero()) {
            Coin makerChangeOutput = preparedDepositTx.getOutput(1).getValue();
            checkArgument(makerChangeOutput.equals(expectedMakerChange),
                    "Maker's preparedDepositTx change output value does not match the expected maker change");
        }

        Coin inputTotal = DepositTransactionUtils.sumInputValues(makerInputs).add(DepositTransactionUtils.sumInputValues(takerInputs));
        Coin outputTotal = preparedDepositTx.getOutputs().stream()
                .map(TransactionOutput::getValue)
                .reduce(Coin.ZERO, Coin::add);
        Coin actualTxFee = inputTotal.subtract(outputTotal);
        checkArgument(actualTxFee.equals(tradeTxFee),
                "Prepared deposit tx fee mismatch. actual=%s, expected=%s",
                actualTxFee,
                tradeTxFee);
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
                "rawTransactionInputs must all be native segwit P2WPKH inputs; " +
                        "legacy P2PKH, P2SH-wrapped, and P2WSH inputs are not supported");
        return rawTransactionInputs;
    }

    public static void validatePeersInputs(List<RawTransactionInput> rawTransactionInputs,
                                           Coin expectedInputAmount,
                                           BtcWalletService walletService,
                                           String peerRole) {
        checkNotNull(rawTransactionInputs, "%s raw transaction inputs must not be null", peerRole);
        checkArgument(!rawTransactionInputs.isEmpty(), "%s raw transaction inputs must not be empty", peerRole);
        checkArgument(rawTransactionInputs.size() <= MAX_INPUTS,
                "%s raw transaction inputs count exceeds %s", peerRole, MAX_INPUTS);
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
        // Dedup outpoints (txid:index) so a peer cannot double-count by listing the same UTXO
        // more than once. Without this, the equality check on the sum is satisfied while the
        // actual on-chain inputs would be duplicates rejected by Bitcoin nodes.
        Set<String> seenOutpoints = new HashSet<>();
        Coin inputValue = Coin.ZERO;
        for (int listPos = 0; listPos < rawTransactionInputs.size(); listPos++) {
            RawTransactionInput input = rawTransactionInputs.get(listPos);
            checkNotNull(input, "%s raw transaction input at position %s must not be null", peerRole, listPos);
            checkArgument(input.value > 0,
                    "%s raw transaction input at position %s must have positive value",
                    peerRole,
                    listPos);
            checkNotNull(input.parentTransaction,
                    "%s raw transaction input at position %s parent tx must not be null", peerRole, listPos);
            // Bound parentTransaction size so a peer cannot ship a multi-MB blob to grief us.
            checkArgument(input.parentTransaction.length <= MAX_PARENT_TX_BYTES,
                    "%s parentTransaction size %s at position %s exceeds limit %s",
                    peerRole, input.parentTransaction.length, listPos, MAX_PARENT_TX_BYTES);
            input.validate(walletService);
            String outpointKey = input.getParentTxId(walletService) + ":" + input.index;
            checkArgument(seenOutpoints.add(outpointKey),
                    "%s duplicate outpoint detected at position %s: %s", peerRole, listPos, outpointKey);
            checkArgument(walletService.isP2WPKH(input),
                    "%s funding input at position %s (parent vout=%s) is not native segwit P2WPKH " +
                            "(bech32: bc1q on mainnet, tb1q on testnet, bcrt1q on regtest). " +
                            "Bisq v1 trades require P2WPKH UTXOs; legacy P2PKH, P2SH-wrapped, and P2WSH inputs are rejected. " +
                            "Move funds to a native segwit address and retry.",
                    peerRole,
                    listPos,
                    input.index);
            inputValue = inputValue.add(Coin.valueOf(input.value));
        }
        return inputValue.value;
    }

    public static void validateDepositInputs(Trade trade) throws InvalidTxException {
        try {
            checkDepositInputs(trade);
        } catch (RuntimeException e) {
            throw new InvalidTxException(e.getMessage(), e);
        }
    }
}
