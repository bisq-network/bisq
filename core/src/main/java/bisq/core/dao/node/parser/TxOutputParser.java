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
    @Setter
    private int unlockBlockHeight;
    @Setter
    private TempTx tempTx; //TODO remove
    @Setter
    @Getter
    private Optional<TxOutput> optionalSpentLockupTxOutput = Optional.empty();

    // Getters
    @Getter
    private boolean bsqOutputFound;
    @Getter
    private Optional<OpReturnType> optionalOpReturnType = Optional.empty();
    @Getter
    private Optional<TempTxOutput> optionalIssuanceCandidate = Optional.empty();
    @Getter
    private Optional<TempTxOutput> optionalBlindVoteLockStakeOutput = Optional.empty();
    @Getter
    private Optional<TempTxOutput> optionalVoteRevealUnlockStakeOutput = Optional.empty();
    @Getter
    private Optional<TempTxOutput> optionalLockupOutput = Optional.empty();

    // Private
    private int lockTime;
    private List<TempTxOutput> tempTxOutputs = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    TxOutputParser(BsqStateService bsqStateService) {
        this.bsqStateService = bsqStateService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void processGenesisTxOutput(TempTx genesisTx) {
        for (int i = 0; i < genesisTx.getTempTxOutputs().size(); ++i) {
            TempTxOutput tempTxOutput = genesisTx.getTempTxOutputs().get(i);
            tempTxOutputs.add(tempTxOutput);
        }
        commitTxOutputsForValidTx();
    }

    void commitTxOutputsForValidTx() {
        tempTxOutputs.forEach(output -> bsqStateService.addUnspentTxOutput(TxOutput.fromTempOutput(output)));
    }

    /**
     * This sets all outputs to BTC_OUTPUT and doesn't add any txOutputs to the unspentTxOutput map in bsqStateService
     */
    void commitTxOutputsForInvalidTx() {
        tempTxOutputs.forEach(output -> output.setTxOutputType(TxOutputType.BTC_OUTPUT));
    }

    // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
    boolean isOpReturnOutput(TempTxOutput txOutput) {
        return txOutput.getOpReturnData() != null;
    }

    void processOpReturnOutput(TempTxOutput tempTxOutput) {
        byte[] opReturnData = tempTxOutput.getOpReturnData();
        checkNotNull(opReturnData, "opReturnData must not be null");
        TxOutputType txOutputType = OpReturnParser.getTxOutputType(tempTxOutput);
        tempTxOutput.setTxOutputType(txOutputType);

        optionalOpReturnType = getMappedOpReturnType(txOutputType);
        // TODO: Do we really want to store the lockTime in another output as where we get it delivered?
        optionalOpReturnType.filter(opReturnType -> opReturnType == OpReturnType.LOCKUP)
                .ifPresent(verifiedOpReturnType -> lockTime = BondingConsensus.getLockTime(opReturnData));
    }

    /**
     * Process a transaction output.
     *
     * @param tempTxOutput The TempTxOutput we are parsing
     */
    void processTxOutput(TempTxOutput tempTxOutput) {
        long txOutputValue = tempTxOutput.getValue();
        int index = tempTxOutput.getIndex();
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
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

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

        txOutput.setTxOutputType(TxOutputType.UNLOCK_OUTPUT);
        tempTxOutputs.add(txOutput);

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
        if (optionalOpReturnType.isPresent())
            opReturnTypeCandidate = optionalOpReturnType.get();

        TxOutputType bsqOutput;
        if (isFirstOutput && opReturnTypeCandidate == OpReturnType.BLIND_VOTE) {
            bsqOutput = TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT;
            optionalBlindVoteLockStakeOutput = Optional.of(txOutput);
        } else if (isFirstOutput && opReturnTypeCandidate == OpReturnType.VOTE_REVEAL) {
            bsqOutput = TxOutputType.VOTE_REVEAL_UNLOCK_STAKE_OUTPUT;
            optionalVoteRevealUnlockStakeOutput = Optional.of(txOutput);
        } else if (isFirstOutput && opReturnTypeCandidate == OpReturnType.LOCKUP) {
            bsqOutput = TxOutputType.LOCKUP_OUTPUT;

            // TODO: Do we really want to store the lockTime in another output as where we get it delivered (opReturn)?
            txOutput.setLockTime(lockTime);
            optionalLockupOutput = Optional.of(txOutput);
        } else {
            bsqOutput = TxOutputType.BSQ_OUTPUT;
        }
        txOutput.setTxOutputType(bsqOutput);
        tempTxOutputs.add(txOutput);

        bsqOutputFound = true;
    }

    private void handleBtcOutput(TempTxOutput txOutput, int index) {
        // If we have BSQ left as fee and we are at the second output it might be a compensation request output.
        // We store the candidate but we don't apply the TxOutputType yet as we need to verify the fee  after all
        // outputs are parsed and check the phase. The TxParser will do that....
        if (availableInputValue > 0 &&
                index == 1 &&
                optionalOpReturnType.isPresent() &&
                optionalOpReturnType.get() == OpReturnType.COMPENSATION_REQUEST) {
            optionalIssuanceCandidate = Optional.of(txOutput);
        } else {
            txOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

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
