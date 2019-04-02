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

import bisq.core.dao.governance.bond.BondConsensus;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.OpReturnType;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputType;

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
    private final DaoStateService daoStateService;

    // Setters
    @Getter
    @Setter
    private long availableInputValue = 0;
    @Setter
    private int unlockBlockHeight;
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
    private final List<TempTxOutput> utxoCandidates = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    TxOutputParser(DaoStateService daoStateService) {
        this.daoStateService = daoStateService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void processOpReturnOutput(TempTxOutput tempTxOutput) {
        byte[] opReturnData = tempTxOutput.getOpReturnData();
        checkNotNull(opReturnData, "opReturnData must not be null");
        TxOutputType txOutputType = OpReturnParser.getTxOutputType(tempTxOutput);
        tempTxOutput.setTxOutputType(txOutputType);

        optionalOpReturnType = getMappedOpReturnType(txOutputType);

        // If we have a LOCKUP opReturn output we save the lockTime to apply it later to the LOCKUP output.
        // We keep that data in that other output as it makes parsing of the UNLOCK tx easier.
        optionalOpReturnType.filter(opReturnType -> opReturnType == OpReturnType.LOCKUP)
                .ifPresent(opReturnType -> lockTime = BondConsensus.getLockTime(opReturnData));
    }

    void processTxOutput(TempTxOutput tempTxOutput) {
        if (!daoStateService.isConfiscatedOutput(tempTxOutput.getKey())) {
            // We don not expect here an opReturn output as we do not get called on the last output. Any opReturn at
            // another output index is invalid.
            if (tempTxOutput.isOpReturnOutput()) {
                tempTxOutput.setTxOutputType(TxOutputType.INVALID_OUTPUT);
                return;
            }

            long txOutputValue = tempTxOutput.getValue();
            int index = tempTxOutput.getIndex();
            if (isUnlockBondTx(tempTxOutput.getValue(), index)) {
                // We need to handle UNLOCK transactions separately as they don't follow the pattern on spending BSQ
                // The LOCKUP BSQ is burnt unless the output exactly matches the input, that would cause the
                // output to not be BSQ output at all
                handleUnlockBondTx(tempTxOutput);
            } else if (isBtcOutputOfBurnFeeTx(tempTxOutput)) {
                // In case we have the opReturn for a burn fee tx all outputs after 1st output are considered BTC
                handleBtcOutput(tempTxOutput, index);
            } else if (availableInputValue > 0 && availableInputValue >= txOutputValue) {
                handleBsqOutput(tempTxOutput, index, txOutputValue);
            } else {
                handleBtcOutput(tempTxOutput, index);
            }
        } else {
            log.warn("TxOutput {} is confiscated ", tempTxOutput.getKey());
            // We only burn that output
            availableInputValue -= tempTxOutput.getValue();
            tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
        }
    }

    void commitUTXOCandidates() {
        utxoCandidates.forEach(output -> daoStateService.addUnspentTxOutput(TxOutput.fromTempOutput(output)));
    }

    /**
     * This sets all outputs to BTC_OUTPUT and doesn't add any txOutputs to the unspentTxOutput map in daoStateService
     */
    void invalidateUTXOCandidates() {
        utxoCandidates.forEach(output -> output.setTxOutputType(TxOutputType.BTC_OUTPUT));
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
        txOutput.setUnlockBlockHeight(unlockBlockHeight);
        utxoCandidates.add(txOutput);

        bsqOutputFound = true;
    }

    private boolean isBtcOutputOfBurnFeeTx(TempTxOutput tempTxOutput) {
        // If we get a asset listing or proof of burn tx we have only 1 BSQ output and if the
        // burned amount is larger than the miner fee we might have a BTC output for receiving the burned funds.
        // If the burned funds are less than the miner fee a BTC input is used for miner fee and a BTC change output for
        // the remaining funds. In any case only the first output is BSQ all the others are BTC.
        return optionalOpReturnType.isPresent() &&
                (optionalOpReturnType.get() == OpReturnType.ASSET_LISTING_FEE ||
                        optionalOpReturnType.get() == OpReturnType.PROOF_OF_BURN) &&
                tempTxOutput.getIndex() >= 1;
    }

    private void handleBsqOutput(TempTxOutput txOutput, int index, long txOutputValue) {
        // Update the input balance.
        availableInputValue -= txOutputValue;

        boolean isFirstOutput = index == 0;

        OpReturnType opReturnTypeCandidate = null;
        if (optionalOpReturnType.isPresent())
            opReturnTypeCandidate = optionalOpReturnType.get();

        TxOutputType txOutputType;
        if (isFirstOutput && opReturnTypeCandidate == OpReturnType.BLIND_VOTE) {
            txOutputType = TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT;
            optionalBlindVoteLockStakeOutput = Optional.of(txOutput);
        } else if (isFirstOutput && opReturnTypeCandidate == OpReturnType.VOTE_REVEAL) {
            txOutputType = TxOutputType.VOTE_REVEAL_UNLOCK_STAKE_OUTPUT;
            optionalVoteRevealUnlockStakeOutput = Optional.of(txOutput);
        } else if (isFirstOutput && opReturnTypeCandidate == OpReturnType.LOCKUP) {
            txOutputType = TxOutputType.LOCKUP_OUTPUT;

            // We store the lockTime in the output which will be used as input for a unlock tx.
            // That makes parsing of that data easier as if we would need to access it from the opReturn output of
            // that tx.
            txOutput.setLockTime(lockTime);
            optionalLockupOutput = Optional.of(txOutput);
        } else {
            txOutputType = TxOutputType.BSQ_OUTPUT;
        }
        txOutput.setTxOutputType(txOutputType);
        utxoCandidates.add(txOutput);

        bsqOutputFound = true;
    }

    private void handleBtcOutput(TempTxOutput txOutput, int index) {
        // If we have BSQ left as fee and we are at the second output it might be a compensation request output.
        // We store the candidate but we don't apply the TxOutputType yet as we need to verify the fee  after all
        // outputs are parsed and check the phase. The TxParser will do that....
        if (availableInputValue > 0 &&
                index == 1 &&
                optionalOpReturnType.isPresent() &&
                (optionalOpReturnType.get() == OpReturnType.COMPENSATION_REQUEST ||
                        optionalOpReturnType.get() == OpReturnType.REIMBURSEMENT_REQUEST)) {
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
            case REIMBURSEMENT_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.REIMBURSEMENT_REQUEST);
            case BLIND_VOTE_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.BLIND_VOTE);
            case VOTE_REVEAL_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.VOTE_REVEAL);
            case LOCKUP_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.LOCKUP);
            case ASSET_LISTING_FEE_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.ASSET_LISTING_FEE);
            case PROOF_OF_BURN_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.PROOF_OF_BURN);
            default:
                return Optional.empty();
        }
    }
}
