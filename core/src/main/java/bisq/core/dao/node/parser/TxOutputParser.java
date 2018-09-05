/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.parser;

import bisq.core.dao.bonding.BondingConsensus;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.TempTx;
import bisq.core.dao.state.blockchain.TempTxOutput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;

import com.google.common.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Checks if an output is a BSQ output and apply state change.
 */
@Slf4j
public class TxOutputParser {
    private final BsqStateService bsqStateService;

    // Setters
    @Getter
    @Setter
    private long availableInputValue = 0;
    private int lockTime;
    private List<TempTxOutput> bsqOutputs = new ArrayList<>();
    @Setter
    private int unlockBlockHeight;
    @Setter
    private TempTx tempTx; //TODO remove
    @Setter
    @Getter
    private Optional<TxOutput> optionalSpentLockupTxOutput = Optional.empty();

    @Getter
    private boolean bsqOutputFound;
    @Getter
    private Optional<OpReturnType> optionalVerifiedOpReturnType = Optional.empty();
    @Getter
    private Optional<TempTxOutput> optionalIssuanceCandidate = Optional.empty();
    @Getter
    private Optional<TempTxOutput> optionalBlindVoteLockStakeOutput = Optional.empty();
    @Getter
    private Optional<TempTxOutput> optionalVoteRevealUnlockStakeOutput = Optional.empty();
    @Getter
    private Optional<TempTxOutput> optionalLockupOutput = Optional.empty();

    TxOutputParser(BsqStateService bsqStateService) {
        this.bsqStateService = bsqStateService;
    }

    public void commitTxOutputs() {
        bsqOutputs.forEach(bsqOutput -> {
            bsqStateService.addUnspentTxOutput(TxOutput.fromTempOutput(bsqOutput));
        });
    }

    /**
     * This sets all outputs to BTC_OUTPUT and doesn't add any txOutputs to the bsqStateService
     */
    public void commitTxOutputsForInvalidTx() {
        bsqOutputs.forEach(bsqOutput -> {
            bsqOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
        });

    }

    public void processGenesisTxOutput(TempTx genesisTx) {
        for (int i = 0; i < genesisTx.getTempTxOutputs().size(); ++i) {
            TempTxOutput tempTxOutput = genesisTx.getTempTxOutputs().get(i);
            bsqOutputs.add(tempTxOutput);
        }
        commitTxOutputs();
    }

    boolean isOpReturnOutput(TempTxOutput txOutput) {
        return txOutput.getOpReturnData() != null;
    }

    void processOpReturnOutput(boolean isLastOutput, TempTxOutput tempTxOutput) {
        byte[] opReturnData = tempTxOutput.getOpReturnData();
        if (opReturnData != null) {
            handleOpReturnOutput(tempTxOutput, isLastOutput);
        } else {
            log.error("This should be an opReturn output");
        }
    }

    /**
     * Process a transaction output.
     *
     * @param isLastOutput  If it is the last output
     * @param tempTxOutput  The TempTxOutput we are parsing
     * @param index         The index in the outputs
     */
    void processTxOutput(boolean isLastOutput, TempTxOutput tempTxOutput, int index) {
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        byte[] opReturnData = tempTxOutput.getOpReturnData();
        if (opReturnData == null) {
            long txOutputValue = tempTxOutput.getValue();
            if (tempTx.getTxType() == TxType.INVALID) {
                // Set all non opReturn outputs to BTC_OUTPUT if the tx is invalid
                tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
            } else if (isUnlockBondTx(tempTxOutput.getValue(), index)) {
                // We need to handle UNLOCK transactions separately as they don't follow the pattern on spending BSQ
                // The LOCKUP BSQ is burnt unless the output exactly matches the input, that would cause the
                // output to not be BSQ output at all
                handleUnlockBondTx(tempTxOutput);
            } else if (availableInputValue > 0 && availableInputValue >= txOutputValue) {
                handleBsqOutput(tempTxOutput, index, txOutputValue);
            } else {
                handleBtcOutput(tempTxOutput, index);
            }
        } else {
            log.error("This should not be an opReturn output");
        }
    }

    /**
     * Whether a transaction is a valid unlock bond transaction or not.
     *
     * @param txOutputValue The value of the current output, in satoshi.
     * @param index         The index of the output.
     * @return True if the transaction is an unlock transaction, false otherwise.
     */
    private boolean isUnlockBondTx(long txOutputValue, int index) {
        // We require that the input value is exact the available value and the output value
        return index == 0 &&
                availableInputValue == txOutputValue &&
                optionalSpentLockupTxOutput.isPresent() &&
                optionalSpentLockupTxOutput.get().getValue() == txOutputValue;
    }

    private void handleUnlockBondTx(TempTxOutput txOutput) {
        checkArgument(optionalSpentLockupTxOutput.isPresent(), "optionalSpentLockupTxOutput must be present");
        availableInputValue -= optionalSpentLockupTxOutput.get().getValue();

        txOutput.setTxOutputType(TxOutputType.UNLOCK);
        bsqOutputs.add(txOutput);

        //TODO move up to TxParser
        // We should add unlockBlockHeight to TempTxOutput and remove unlockBlockHeight from tempTx
        tempTx.setUnlockBlockHeight(unlockBlockHeight);

        bsqOutputFound = true;
    }

    private void handleBsqOutput(TempTxOutput txOutput, int index, long txOutputValue) {
        // Update the input balance.
        availableInputValue -= txOutputValue;

        boolean isFirstOutput = index == 0;

        OpReturnType opReturnTypeCandidate = null;
        if (optionalVerifiedOpReturnType.isPresent())
            opReturnTypeCandidate = optionalVerifiedOpReturnType.get();

        TxOutputType bsqOutput;
        if (isFirstOutput && opReturnTypeCandidate == OpReturnType.BLIND_VOTE) {
            bsqOutput = TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT;
            optionalBlindVoteLockStakeOutput = Optional.of(txOutput);
        } else if (isFirstOutput && opReturnTypeCandidate == OpReturnType.VOTE_REVEAL) {
            bsqOutput = TxOutputType.VOTE_REVEAL_UNLOCK_STAKE_OUTPUT;
            optionalVoteRevealUnlockStakeOutput = Optional.of(txOutput);
        } else if (isFirstOutput && opReturnTypeCandidate == OpReturnType.LOCKUP) {
            bsqOutput = TxOutputType.LOCKUP;
            txOutput.setLockTime(lockTime);
            optionalLockupOutput = Optional.of(txOutput);
        } else {
            bsqOutput = TxOutputType.BSQ_OUTPUT;
        }
        txOutput.setTxOutputType(bsqOutput);
        bsqOutputs.add(txOutput);

        bsqOutputFound = true;
    }

    private void handleBtcOutput(TempTxOutput txOutput, int index) {
        // If we have BSQ left for burning and at the second output a compensation request output we set the
        // candidate to the parsingModel and we don't apply the TxOutputType as we do that later as the OpReturn check.
        if (availableInputValue > 0 &&
                index == 1 &&
                optionalVerifiedOpReturnType.isPresent() &&
                optionalVerifiedOpReturnType.get() == OpReturnType.COMPENSATION_REQUEST) {
            // We don't set the txOutputType yet as we have not fully validated the tx but put the candidate
            // into our local optionalIssuanceCandidate.

            optionalIssuanceCandidate = Optional.of(txOutput);
        } else {
            txOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
        }
    }

    private void handleOpReturnOutput(TempTxOutput tempTxOutput, boolean isLastOutput) {
        TxOutputType txOutputType = OpReturnParser.getTxOutputType(tempTxOutput, isLastOutput);
        tempTxOutput.setTxOutputType(txOutputType);

        optionalVerifiedOpReturnType = getMappedOpReturnType(txOutputType);
        optionalVerifiedOpReturnType.filter(verifiedOpReturnType -> verifiedOpReturnType == OpReturnType.LOCKUP)
                .ifPresent(verifiedOpReturnType -> {
                    byte[] opReturnData = tempTxOutput.getOpReturnData();
                    checkNotNull(opReturnData, "opReturnData must not be null");
                    lockTime = BondingConsensus.getLockTime(opReturnData);
                });
    }

    @SuppressWarnings("WeakerAccess")
    @VisibleForTesting
    static Optional<OpReturnType> getMappedOpReturnType(TxOutputType outputType) {
        switch (outputType) {
            case PROPOSAL_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.PROPOSAL);
            case COMP_REQ_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.COMPENSATION_REQUEST);
            case BLIND_VOTE_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.BLIND_VOTE);
            case VOTE_REVEAL_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.VOTE_REVEAL);
            case LOCKUP_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.LOCKUP);
            default:
                return Optional.empty();
        }
    }
}
