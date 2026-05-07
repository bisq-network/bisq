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

package bisq.core.trade.bisq_v1;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletUtils;
import bisq.core.offer.Offer;
import bisq.core.support.dispute.DisputeValidation;
import bisq.core.trade.model.bisq_v1.Trade;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class TradeDataValidation {
    public static void validateDelayedPayoutTx(Trade trade,
                                               Transaction delayedPayoutTx,
                                               BtcWalletService btcWalletService)
            throws DisputeValidation.AddressException, MissingTxException,
            InvalidTxException, InvalidLockTimeException, InvalidAmountException {
        validateDelayedPayoutTx(trade,
                delayedPayoutTx,
                btcWalletService,
                null);
    }

    public static void validateDelayedPayoutTx(Trade trade,
                                               Transaction delayedPayoutTx,
                                               BtcWalletService btcWalletService,
                                               @Nullable Consumer<String> addressConsumer)
            throws DisputeValidation.AddressException, MissingTxException,
            InvalidTxException, InvalidLockTimeException, InvalidAmountException {
        String errorMsg;
        if (delayedPayoutTx == null) {
            errorMsg = "DelayedPayoutTx must not be null";
            log.error(errorMsg);
            throw new MissingTxException("DelayedPayoutTx must not be null");
        }

        // Validate tx structure
        if (delayedPayoutTx.getInputs().size() != 1) {
            errorMsg = "Number of delayedPayoutTx inputs must be 1";
            log.error(errorMsg);
            log.error(delayedPayoutTx.toString());
            throw new InvalidTxException(errorMsg);
        }


        // connectedOutput is null and input.getValue() is null at that point as the tx is not committed to the wallet
        // yet. So we cannot check that the input matches but we did the amount check earlier in the trade protocol.

        // Validate lock time
        if (delayedPayoutTx.getLockTime() != trade.getLockTime()) {
            errorMsg = "delayedPayoutTx.getLockTime() must match trade.getLockTime()";
            log.error(errorMsg);
            log.error(delayedPayoutTx.toString());
            throw new InvalidLockTimeException(errorMsg);
        }

        // Validate seq num
        if (delayedPayoutTx.getInput(0).getSequenceNumber() != TransactionInput.NO_SEQUENCE - 1) {
            errorMsg = "Sequence number must be 0xFFFFFFFE";
            log.error(errorMsg);
            log.error(delayedPayoutTx.toString());
            throw new InvalidLockTimeException(errorMsg);
        }

        if (trade.isUsingLegacyBurningMan()) {
            if (delayedPayoutTx.getOutputs().size() != 1) {
                errorMsg = "Number of delayedPayoutTx outputs must be 1";
                log.error(errorMsg);
                log.error(delayedPayoutTx.toString());
                throw new InvalidTxException(errorMsg);
            }

            // Check amount
            TransactionOutput output = delayedPayoutTx.getOutput(0);
            Offer offer = checkNotNull(trade.getOffer());
            Coin msOutputAmount = offer.getBuyerSecurityDeposit()
                    .add(offer.getSellerSecurityDeposit())
                    .add(checkNotNull(trade.getAmount()));

            if (!output.getValue().equals(msOutputAmount)) {
                errorMsg = "Output value of deposit tx and delayed payout tx is not matching. Output: " + output + " / msOutputAmount: " + msOutputAmount;
                log.error(errorMsg);
                log.error(delayedPayoutTx.toString());
                throw new InvalidAmountException(errorMsg);
            }

            NetworkParameters params = btcWalletService.getParams();
            if (addressConsumer != null) {
                String delayedPayoutTxOutputAddress = output.getScriptPubKey().getToAddress(params).toString();
                addressConsumer.accept(delayedPayoutTxOutputAddress);
            }
        }
    }

    public static void validatePayoutTxInput(Transaction depositTx,
                                             Transaction delayedPayoutTx)
            throws InvalidInputException {
        TransactionInput input = delayedPayoutTx.getInput(0);
        checkNotNull(input, "delayedPayoutTx.getInput(0) must not be null");
        // input.getConnectedOutput() is null as the tx is not committed at that point

        TransactionOutPoint outpoint = input.getOutpoint();
        if (!outpoint.getHash().toString().equals(depositTx.getTxId().toString()) || outpoint.getIndex() != 0) {
            throw new InvalidInputException("Input of delayed payout transaction does not point to output of deposit tx.\n" +
                    "Delayed payout tx=" + delayedPayoutTx + "\n" +
                    "Deposit tx=" + depositTx);
        }
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
     * Throws if the tx has a non-zero lockTime. Strictly, lockTime is ignored when every
     * input has a final sequence (0xffffffff), so a non-zero lockTime alone does not always
     * delay mining. We still require lockTime == 0 as a canonical-form / policy invariant:
     * any non-zero value is non-canonical for this path and a peer setting one would be
     * either buggy or trying to deviate from the protocol-specified shape.
     */
    public static void assertLockTimeIsZero(Transaction tx) throws InvalidTxException {
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
    public static void assertInputSequencesDisableRbf(Transaction tx) throws InvalidTxException {
        for (TransactionInput txIn : tx.getInputs()) {
            long seq = txIn.getSequenceNumber();
            if (seq != TransactionInput.NO_SEQUENCE - 1 && seq != TransactionInput.NO_SEQUENCE) {
                throw new InvalidTxException("Transaction input has RBF-enabled sequence: " + seq);
            }
        }
    }

    /**
     * Throws unless the tx is version 1. Bisq deposit txs are constructed by bitcoinj at the
     * default version (1) and have no need for v2 semantics (no relative-locktime / BIP68
     * use). The version field is part of the serialized tx and therefore part of the txid,
     * so a peer-supplied tx at any other version would still hash differently than what we
     * expect. Rejecting non-v1 keeps the funding path canonical and removes a degree of
     * freedom a peer could otherwise wiggle without us noticing.
     */
    public static void assertVersionIsOne(Transaction tx) throws InvalidTxException {
        if (tx.getVersion() != 1) {
            throw new InvalidTxException("Transaction must be version 1, got " + tx.getVersion());
        }
    }

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
     * Throws unless every supplied funding input refers to a P2WPKH UTXO. Two reasons:
     *   - Legacy P2PKH and P2SH-wrapped scripts carry a malleable scriptSig, which is part
     *     of the txid. A third party can re-sign and rebroadcast with a different txid
     *     before confirmation, breaking any downstream binding to the original txid.
     *   - P2WSH (native segwit) is not malleable in that sense, but it allows arbitrary
     *     peer-controlled script semantics and unpredictable vsize. The Bisq trade-protocol
     *     funding path is canonically P2WPKH; anything else is non-standard for this path.
     */
    public static void assertAllInputsAreP2WPKH(List<RawTransactionInput> inputs,
                                                NetworkParameters params) throws InvalidTxException {
        if (inputs == null) {
            throw new InvalidTxException("Funding inputs list must not be null");
        }
        for (int listPos = 0; listPos < inputs.size(); listPos++) {
            RawTransactionInput input = inputs.get(listPos);
            if (input == null) {
                throw new InvalidTxException("Funding input at position " + listPos + " must not be null");
            }
            if (!WalletUtils.isP2WPKH(input, params)) {
                // Avoid re-parsing parentTransaction here — it may be malformed (which is
                // exactly why isP2WPKH returned false). Surface only the fields we already
                // hold on the RawTransactionInput.
                throw new InvalidTxException("Funding input at position " + listPos +
                        " (parent vout=" + input.index + ")" +
                        " is not native segwit P2WPKH (bech32: bc1q on mainnet, tb1q on testnet, bcrt1q on regtest). " +
                        "Bisq v1 trades require P2WPKH UTXOs; legacy P2PKH, P2SH-wrapped, and P2WSH inputs are rejected. " +
                        "Move funds to a native segwit address and retry.");
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Exceptions
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static class ValidationException extends Exception {
        ValidationException(String msg) {
            super(msg);
        }

        ValidationException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    public static class MissingTxException extends ValidationException {
        MissingTxException(String msg) {
            super(msg);
        }
    }

    public static class InvalidTxException extends ValidationException {
        InvalidTxException(String msg) {
            super(msg);
        }

        InvalidTxException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    public static class InvalidAmountException extends ValidationException {
        InvalidAmountException(String msg) {
            super(msg);
        }
    }

    public static class InvalidLockTimeException extends ValidationException {
        InvalidLockTimeException(String msg) {
            super(msg);
        }
    }

    public static class InvalidInputException extends ValidationException {
        InvalidInputException(String msg) {
            super(msg);
        }
    }
}
