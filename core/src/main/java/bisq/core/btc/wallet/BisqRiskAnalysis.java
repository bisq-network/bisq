/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

package bisq.core.btc.wallet;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.ECKey.ECDSASignature;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SignatureDecodeException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.TransactionSignature;
import org.bitcoinj.script.ScriptChunk;
import org.bitcoinj.script.ScriptPattern;
import org.bitcoinj.wallet.RiskAnalysis;
import org.bitcoinj.wallet.Wallet;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkState;

// Copied from DefaultRiskAnalysis as DefaultRiskAnalysis has mostly private methods and constructor so we cannot
// override it.
// The changes to DefaultRiskAnalysis are: removal of the RBF check and accept as standard an OP_RETURN outputs
// with 0 value.
// For Bisq's use cases RBF is not considered risky. Requiring a confirmation for RBF payments from a user's
// external wallet to Bisq would hurt usability. The trade transaction requires anyway a confirmation and we don't see
// a use case where a Bisq user accepts unconfirmed payment from untrusted peers and would not wait anyway for at least
// one confirmation.

/**
 * <p>The default risk analysis. Currently, it only is concerned with whether a tx/dependency is non-final or not, and
 * whether a tx/dependency violates the dust rules. Outside of specialised protocols you should not encounter non-final
 * transactions.</p>
 */
public class BisqRiskAnalysis implements RiskAnalysis {
    private static final Logger log = LoggerFactory.getLogger(BisqRiskAnalysis.class);

    /**
     * Any standard output smaller than this value (in satoshis) will be considered risky, as it's most likely be
     * rejected by the network. This is usually the same as {@link Transaction#MIN_NONDUST_OUTPUT} but can be
     * different when the fee is about to change in Bitcoin Core.
     */
    public static final Coin MIN_ANALYSIS_NONDUST_OUTPUT = Transaction.MIN_NONDUST_OUTPUT;

    protected final Transaction tx;
    protected final List<Transaction> dependencies;
    @Nullable
    protected final Wallet wallet;

    private Transaction nonStandard;
    protected Transaction nonFinal;
    protected boolean analyzed;

    private BisqRiskAnalysis(Wallet wallet, Transaction tx, List<Transaction> dependencies) {
        this.tx = tx;
        this.dependencies = dependencies;
        this.wallet = wallet;
    }

    @Override
    public Result analyze() {
        checkState(!analyzed);
        analyzed = true;

        Result result = analyzeIsFinal();
        if (result != null && result != Result.OK)
            return result;

        return analyzeIsStandard();
    }

    @Nullable
    private Result analyzeIsFinal() {
        // Transactions we create ourselves are, by definition, not at risk of double spending against us.
        if (tx.getConfidence().getSource() == TransactionConfidence.Source.SELF)
            return Result.OK;

        // Commented out to accept replace-by-fee txs.
        // // We consider transactions that opt into replace-by-fee at risk of double spending.
        // if (tx.isOptInFullRBF()) {
        //     nonFinal = tx;
        //     return Result.NON_FINAL;
        // }

        // Relative time-locked transactions are risky too. We can't check the locks because usually we don't know the
        // spent outputs (to know when they were created).
        if (tx.hasRelativeLockTime()) {
            nonFinal = tx;
            return Result.NON_FINAL;
        }

        if (wallet == null)
            return null;

        final int height = wallet.getLastBlockSeenHeight();
        final long time = wallet.getLastBlockSeenTimeSecs();
        // If the transaction has a lock time specified in blocks, we consider that if the tx would become final in the
        // next block it is not risky (as it would confirm normally).
        final int adjustedHeight = height + 1;

        if (!tx.isFinal(adjustedHeight, time)) {
            nonFinal = tx;
            return Result.NON_FINAL;
        }
        for (Transaction dep : dependencies) {
            if (!dep.isFinal(adjustedHeight, time)) {
                nonFinal = dep;
                return Result.NON_FINAL;
            }
        }

        return Result.OK;
    }

    /**
     * The reason a transaction is considered non-standard, returned by
     * {@link #isStandard(org.bitcoinj.core.Transaction)}.
     */
    public enum RuleViolation {
        NONE,
        VERSION,
        DUST,
        SHORTEST_POSSIBLE_PUSHDATA,
        NONEMPTY_STACK, // Not yet implemented (for post 0.12)
        SIGNATURE_CANONICAL_ENCODING
    }

    /**
     * <p>Checks if a transaction is considered "standard" by Bitcoin Core's IsStandardTx and AreInputsStandard
     * functions.</p>
     *
     * <p>Note that this method currently only implements a minimum of checks. More to be added later.</p>
     */
    public static RuleViolation isStandard(Transaction tx) {
        // TODO: Finish this function off.
        if (tx.getVersion() > 2 || tx.getVersion() < 1) {
            log.warn("TX considered non-standard due to unknown version number {}", tx.getVersion());
            return RuleViolation.VERSION;
        }

        final List<TransactionOutput> outputs = tx.getOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            TransactionOutput output = outputs.get(i);
            RuleViolation violation = isOutputStandard(output);
            if (violation != RuleViolation.NONE) {
                log.warn("TX considered non-standard due to output {} violating rule {}", i, violation);
                return violation;
            }
        }

        final List<TransactionInput> inputs = tx.getInputs();
        for (int i = 0; i < inputs.size(); i++) {
            TransactionInput input = inputs.get(i);
            RuleViolation violation = isInputStandard(input);
            if (violation != RuleViolation.NONE) {
                log.warn("TX considered non-standard due to input {} violating rule {}", i, violation);
                return violation;
            }
        }

        return RuleViolation.NONE;
    }

    /**
     * Checks the output to see if the script violates a standardness rule. Not complete.
     */
    public static RuleViolation isOutputStandard(TransactionOutput output) {
        // OP_RETURN has usually output value zero, so we exclude that from the MIN_ANALYSIS_NONDUST_OUTPUT check
        if (!ScriptPattern.isOpReturn(output.getScriptPubKey())
                && output.getValue().compareTo(MIN_ANALYSIS_NONDUST_OUTPUT) < 0)
            return RuleViolation.DUST;
        for (ScriptChunk chunk : output.getScriptPubKey().getChunks()) {
            if (chunk.isPushData() && !chunk.isShortestPossiblePushData())
                return RuleViolation.SHORTEST_POSSIBLE_PUSHDATA;
        }
        return RuleViolation.NONE;
    }

    /** Checks if the given input passes some of the AreInputsStandard checks. Not complete. */
    public static RuleViolation isInputStandard(TransactionInput input) {
        for (ScriptChunk chunk : input.getScriptSig().getChunks()) {
            if (chunk.data != null && !chunk.isShortestPossiblePushData())
                return RuleViolation.SHORTEST_POSSIBLE_PUSHDATA;
            if (chunk.isPushData()) {
                ECDSASignature signature;
                try {
                    signature = ECKey.ECDSASignature.decodeFromDER(chunk.data);
                } catch (SignatureDecodeException x) {
                    // Doesn't look like a signature.
                    signature = null;
                }
                if (signature != null) {
                    if (!TransactionSignature.isEncodingCanonical(chunk.data))
                        return RuleViolation.SIGNATURE_CANONICAL_ENCODING;
                    if (!signature.isCanonical())
                        return RuleViolation.SIGNATURE_CANONICAL_ENCODING;
                }
            }
        }
        return RuleViolation.NONE;
    }

    private Result analyzeIsStandard() {
        // The IsStandard rules don't apply on testnet, because they're just a safety mechanism and we don't want to
        // crush innovation with valueless test coins.
        if (wallet != null && !wallet.getNetworkParameters().getId().equals(NetworkParameters.ID_MAINNET))
            return Result.OK;

        RuleViolation ruleViolation = isStandard(tx);
        if (ruleViolation != RuleViolation.NONE) {
            nonStandard = tx;
            return Result.NON_STANDARD;
        }

        for (Transaction dep : dependencies) {
            ruleViolation = isStandard(dep);
            if (ruleViolation != RuleViolation.NONE) {
                nonStandard = dep;
                return Result.NON_STANDARD;
            }
        }

        return Result.OK;
    }

    /** Returns the transaction that was found to be non-standard, or null. */
    @Nullable
    public Transaction getNonStandard() {
        return nonStandard;
    }

    /** Returns the transaction that was found to be non-final, or null. */
    @Nullable
    public Transaction getNonFinal() {
        return nonFinal;
    }

    @Override
    public String toString() {
        if (!analyzed)
            return "Pending risk analysis for " + tx.getTxId().toString();
        else if (nonFinal != null)
            return "Risky due to non-finality of " + nonFinal.getTxId().toString();
        else if (nonStandard != null)
            return "Risky due to non-standard tx " + nonStandard.getTxId().toString();
        else
            return "Non-risky";
    }

    public static class Analyzer implements RiskAnalysis.Analyzer {
        @Override
        public BisqRiskAnalysis create(Wallet wallet, Transaction tx, List<Transaction> dependencies) {
            return new BisqRiskAnalysis(wallet, tx, dependencies);
        }
    }

    public static Analyzer FACTORY = new Analyzer();
}
