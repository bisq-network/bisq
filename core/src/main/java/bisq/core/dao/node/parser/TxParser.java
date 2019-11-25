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

import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.node.full.RawTx;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.OpReturnType;
import bisq.core.dao.state.model.blockchain.Tx;
import bisq.core.dao.state.model.blockchain.TxInput;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxOutputType;
import bisq.core.dao.state.model.blockchain.TxType;
import bisq.core.dao.state.model.governance.DaoPhase;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Verifies if a given transaction is a BSQ transaction.
 */
@Slf4j
public class TxParser {
    private final PeriodService periodService;
    private final DaoStateService daoStateService;
    private TxOutputParser txOutputParser;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TxParser(PeriodService periodService,
                    DaoStateService daoStateService) {
        this.periodService = periodService;
        this.daoStateService = daoStateService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<Tx> findTx(RawTx rawTx, String genesisTxId, int genesisBlockHeight, Coin genesisTotalSupply) {
        if (GenesisTxParser.isGenesis(rawTx, genesisTxId, genesisBlockHeight))
            return Optional.of(GenesisTxParser.getGenesisTx(rawTx, genesisTotalSupply, daoStateService));
        else
            return findTx(rawTx);
    }

    // Apply state changes to tx, inputs and outputs
    // return Tx if any input contained BSQ
    // Any tx with BSQ input is a BSQ tx.
    // There might be txs without any valid BSQ txOutput but we still keep track of it,
    // for instance to calculate the total burned BSQ.
    private Optional<Tx> findTx(RawTx rawTx) {
        int blockHeight = rawTx.getBlockHeight();
        TempTx tempTx = TempTx.fromRawTx(rawTx);

        //****************************************************************************************
        // Parse Inputs
        //****************************************************************************************

        TxInputParser txInputParser = new TxInputParser(daoStateService);
        for (int inputIndex = 0; inputIndex < tempTx.getTxInputs().size(); inputIndex++) {
            TxInput input = tempTx.getTxInputs().get(inputIndex);
            TxOutputKey outputKey = input.getConnectedTxOutputKey();
            txInputParser.process(outputKey, blockHeight, rawTx.getId(), inputIndex);
        }

        // Results from txInputParser
        long accumulatedInputValue = txInputParser.getAccumulatedInputValue();
        long burntBondValue = txInputParser.getBurntBondValue();
        boolean unLockInputValid = txInputParser.isUnLockInputValid();
        int unlockBlockHeight = txInputParser.getUnlockBlockHeight();
        Optional<TxOutput> optionalSpentLockupTxOutput = txInputParser.getOptionalSpentLockupTxOutput();

        boolean hasBsqInputs = accumulatedInputValue > 0;
        boolean hasBurntBond = burntBondValue > 0;

        // If we don't have any BSQ in our input and we don't have burnt bonds we do not consider the tx as a BSQ tx.
        if (!hasBsqInputs && !hasBurntBond)
            return Optional.empty();


        //****************************************************************************************
        // Parse Outputs
        //****************************************************************************************

        txOutputParser = new TxOutputParser(daoStateService);
        txOutputParser.setAvailableInputValue(accumulatedInputValue);
        txOutputParser.setUnlockBlockHeight(unlockBlockHeight);
        txOutputParser.setOptionalSpentLockupTxOutput(optionalSpentLockupTxOutput);

        List<TempTxOutput> outputs = tempTx.getTempTxOutputs();
        // We start with last output as that might be an OP_RETURN output and gives us the specific tx type, so it is
        // easier and cleaner at parsing the other outputs to detect which kind of tx we deal with.
        int lastIndex = outputs.size() - 1;
        int lastNonOpReturnIndex = lastIndex;
        if (outputs.get(lastIndex).isOpReturnOutput()) {
            txOutputParser.processOpReturnOutput(outputs.get(lastIndex));
            lastNonOpReturnIndex -= 1;
        }

        // We need to consider the order of the outputs. An output is a BSQ utxo as long there is enough input value
        // We iterate all outputs (excluding an optional opReturn).
        for (int index = 0; index <= lastNonOpReturnIndex; index++) {
            txOutputParser.processTxOutput(outputs.get(index));
        }

        // Results from txOutputParser
        long remainingInputValue = txOutputParser.getAvailableInputValue();
        Optional<OpReturnType> optionalOpReturnType = txOutputParser.getOptionalOpReturnType();
        boolean bsqOutputFound = txOutputParser.isBsqOutputFound();

        long burntBsq = remainingInputValue + burntBondValue;
        boolean hasBurntBsq = burntBsq > 0;
        if (hasBurntBsq)
            tempTx.setBurntBsq(burntBsq);


        //****************************************************************************************
        // Verify and apply txType and txOutputTypes after we have all outputs parsed
        //****************************************************************************************

        applyTxTypeAndTxOutputType(blockHeight, tempTx, remainingInputValue);
        TxType txType;
        if (tempTx.getTxType() != TxType.IRREGULAR && tempTx.getTxType() != TxType.INVALID) {
            txType = evaluateTxType(tempTx, optionalOpReturnType, hasBurntBsq, unLockInputValid);
            tempTx.setTxType(txType);
        } else {
            txType = tempTx.getTxType();
        }

        if (isTxInvalid(tempTx, bsqOutputFound, hasBurntBond)) {
            tempTx.setTxType(TxType.INVALID);
            // We consider all BSQ inputs as burned if the tx is invalid.
            tempTx.setBurntBsq(accumulatedInputValue);
            txOutputParser.invalidateUTXOCandidates();
            log.warn("We have destroyed BSQ because of an invalid tx. Burned BSQ={}. tx={}", accumulatedInputValue / 100D, tempTx);
        } else if (txType == TxType.IRREGULAR) {
            log.warn("We have an irregular tx {}", tempTx);
            txOutputParser.commitUTXOCandidates();
        } else {
            txOutputParser.commitUTXOCandidates();
        }

        return Optional.of(Tx.fromTempTx(tempTx));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * This method verifies after all outputs are parsed if the opReturn type and the optional txOutputs required for
     * certain use cases are valid.
     * It verifies also if the fee is correct (if required) and if the phase is correct (if relevant).
     * We set the txType as well as the txOutputType of the relevant outputs.
     */
    private void applyTxTypeAndTxOutputType(int blockHeight, TempTx tempTx, long bsqFee) {
        OpReturnType opReturnType = null;
        Optional<OpReturnType> optionalOpReturnType = txOutputParser.getOptionalOpReturnType();
        if (optionalOpReturnType.isPresent()) {
            opReturnType = optionalOpReturnType.get();

            switch (opReturnType) {
                case PROPOSAL:
                    processProposal(blockHeight, tempTx, bsqFee);
                    break;
                case COMPENSATION_REQUEST:
                case REIMBURSEMENT_REQUEST:
                    processIssuance(blockHeight, tempTx, bsqFee);
                    break;
                case BLIND_VOTE:
                    processBlindVote(blockHeight, tempTx, bsqFee);
                    break;
                case VOTE_REVEAL:
                    // We do not check phase or cycle as a late voteReveal tx is considered a valid BSQ tx.
                    // The vote result though will ignore such votes.
                    break;
                case LOCKUP:
                case ASSET_LISTING_FEE:
                case PROOF_OF_BURN:
                    // do nothing
                    break;
            }
        }

        // We need to check if any tempTxOutput is available and if so and the OpReturn data is invalid we
        // set the output to a BTC output. We must not use `if else` cases here!
        if (opReturnType != OpReturnType.COMPENSATION_REQUEST && opReturnType != OpReturnType.REIMBURSEMENT_REQUEST) {
            // We applied already the check to not permit further BSQ outputs after the issuanceCandidate in the
            // txOutputParser so we don't need to do any additional check here when we change to BTC_OUTPUT.
            txOutputParser.getOptionalIssuanceCandidate().ifPresent(tempTxOutput -> tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT));
        }

        if (opReturnType != OpReturnType.BLIND_VOTE) {
            txOutputParser.getOptionalBlindVoteLockStakeOutput().ifPresent(tempTxOutput -> {
                // We cannot apply the rule to not allow BSQ outputs after a BTC output as the 2nd output is an
                // optional BSQ change output and we don't want to burn that in case the opReturn is invalid.
                tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
            });
        }

        if (opReturnType != OpReturnType.VOTE_REVEAL) {
            txOutputParser.getOptionalVoteRevealUnlockStakeOutput().ifPresent(tempTxOutput -> {
                // We do not apply the rule to not allow BSQ outputs after a BTC output here because we expect only
                // one BSQ output anyway.
                tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
            });
        }

        if (opReturnType != OpReturnType.LOCKUP) {
            txOutputParser.getOptionalLockupOutput().ifPresent(tempTxOutput -> {
                // We cannot apply the rule to not allow BSQ outputs after a BTC output as the 2nd output is an
                // optional BSQ change output and we don't want to burn that in case the opReturn is invalid.
                tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
            });
        }
    }

    private void processProposal(int blockHeight, TempTx tempTx, long bsqFee) {
        boolean isFeeAndPhaseValid = isFeeAndPhaseValid(tempTx.getId(), blockHeight, bsqFee, DaoPhase.Phase.PROPOSAL, Param.PROPOSAL_FEE);
        if (!isFeeAndPhaseValid) {
            // We tolerate such an incorrect tx and do not burn the BSQ
            tempTx.setTxType(TxType.IRREGULAR);
        }
    }

    private void processIssuance(int blockHeight, TempTx tempTx, long bsqFee) {
        boolean isFeeAndPhaseValid = isFeeAndPhaseValid(tempTx.getId(), blockHeight, bsqFee, DaoPhase.Phase.PROPOSAL, Param.PROPOSAL_FEE);
        Optional<TempTxOutput> optionalIssuanceCandidate = txOutputParser.getOptionalIssuanceCandidate();
        if (isFeeAndPhaseValid) {
            if (optionalIssuanceCandidate.isPresent()) {
                // Now after we have validated the fee and phase we will apply the TxOutputType
                optionalIssuanceCandidate.get().setTxOutputType(TxOutputType.ISSUANCE_CANDIDATE_OUTPUT);
            } else {
                log.warn("It can be that we have a opReturn which is correct from its structure but the whole tx " +
                        "in not valid as the issuanceCandidate in not there. " +
                        "As the BSQ fee is set it must be either a buggy tx or a manually crafted invalid tx.");
                // Even though the request part if invalid the BSQ transfer and change output should still be valid
                // as long as the BSQ change <= BSQ inputs.
                // We tolerate such an incorrect tx and do not burn the BSQ
                tempTx.setTxType(TxType.IRREGULAR);
            }
        } else {
            // This could be a valid compensation request that failed to be included in a block during the
            // correct phase due to no fault of the user. We must not burn the change as long as the BSQ inputs
            // cover the value of the outputs.
            // We tolerate such an incorrect tx and do not burn the BSQ
            tempTx.setTxType(TxType.IRREGULAR);

            // Make sure the optionalIssuanceCandidate is set to BTC
            // We applied already the check to not permit further BSQ outputs after the issuanceCandidate in the
            // txOutputParser so we don't need to do any additional check here when we change to BTC_OUTPUT.
            optionalIssuanceCandidate.ifPresent(tempTxOutput -> tempTxOutput.setTxOutputType(TxOutputType.BTC_OUTPUT));
            // Empty Optional case is a possible valid case where a random tx matches our opReturn rules but it is not a
            // valid BSQ tx.
        }
    }

    private void processBlindVote(int blockHeight, TempTx tempTx, long bsqFee) {
        boolean isFeeAndPhaseValid = isFeeAndPhaseValid(tempTx.getId(), blockHeight, bsqFee, DaoPhase.Phase.BLIND_VOTE, Param.BLIND_VOTE_FEE);
        if (!isFeeAndPhaseValid) {
            // We tolerate such an incorrect tx and do not burn the BSQ
            tempTx.setTxType(TxType.IRREGULAR);

            // Set the stake output from BLIND_VOTE_LOCK_STAKE_OUTPUT to BSQ
            txOutputParser.getOptionalBlindVoteLockStakeOutput().ifPresent(tempTxOutput -> tempTxOutput.setTxOutputType(TxOutputType.BSQ_OUTPUT));
            // Empty Optional case is a possible valid case where a random tx matches our opReturn rules but it is not a
            // valid BSQ tx.
        }
    }

    /**
     * Whether the BSQ fee and phase is valid for a transaction.
     *
     * @param blockHeight The height of the block that the transaction is in.
     * @param bsqFee      The fee in BSQ, in satoshi.
     * @param phase       The current phase of the DAO, e.g {@code DaoPhase.Phase.PROPOSAL}.
     * @param param       The parameter for the fee, e.g {@code Param.PROPOSAL_FEE}.
     * @return True if the fee and phase was valid, false otherwise.
     */
    private boolean isFeeAndPhaseValid(String txId, int blockHeight, long bsqFee, DaoPhase.Phase phase, Param param) {
        // The leftover BSQ balance from the inputs is the BSQ fee in case we are in an OP_RETURN output

        if (!periodService.isInPhase(blockHeight, phase)) {
            log.warn("Tx with ID {} is not in required phase ({}). blockHeight={}", txId, phase, blockHeight);
            return false;
        }
        long paramValue = daoStateService.getParamValueAsCoin(param, blockHeight).value;
        boolean isFeeCorrect = bsqFee == paramValue;
        if (!isFeeCorrect) {
            log.warn("Invalid fee. used fee={}, required fee={}, txId={}", bsqFee, paramValue, txId);
        }
        return isFeeCorrect;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    // Performs various checks for an invalid tx
    static boolean isTxInvalid(TempTx tempTx, boolean bsqOutputFound, boolean burntBondValue) {
        if (tempTx.getTxType() == TxType.INVALID) {
            // We got already set the invalid type in earlier checks and return early.
            return true;
        }

        // We don't allow multiple opReturn outputs (they are non-standard but to be safe lets check it)
        long numOpReturnOutputs = tempTx.getTempTxOutputs().stream()
                .filter(TempTxOutput::isOpReturnOutput)
                .count();
        if (numOpReturnOutputs > 1) {
            log.warn("Invalid tx. We have multiple opReturn outputs. tx=" + tempTx);
            return true;
        }

        if ((tempTx.getTxType() == TxType.COMPENSATION_REQUEST ||
                tempTx.getTxType() == TxType.REIMBURSEMENT_REQUEST)
                && !bsqOutputFound) {
            log.warn("Invalid Tx: A compensation or reimbursement tx requires 1 BSQ output. Tx=" + tempTx);
            return true;
        }

        if (burntBondValue) {
            log.warn("Invalid Tx: Bond value was burnt. tx=" + tempTx);
            return true;
        }

        if (tempTx.getTempTxOutputs().stream()
                .anyMatch(txOutput -> TxOutputType.UNDEFINED_OUTPUT == txOutput.getTxOutputType() ||
                        TxOutputType.INVALID_OUTPUT == txOutput.getTxOutputType())) {
            log.warn("Invalid Tx: We have undefined or invalid txOutput types. tx=" + tempTx);
            return true;
        }

        return false;
    }

    /**
     * Retrieve the type of the transaction, assuming it is relevant to bisq.
     *
     * @param tempTx               The temporary transaction.
     * @param optionalOpReturnType The optional OP_RETURN type of the transaction.
     * @param hasBurntBSQ          If there have been remaining value from the inputs which got not spent in outputs.
     *                             Might be valid BSQ fees or burned BSQ from an invalid tx.
     * @return The type of the transaction, if it is relevant to bisq.
     */
    @VisibleForTesting
    static TxType evaluateTxType(TempTx tempTx, Optional<OpReturnType> optionalOpReturnType,
                                 boolean hasBurntBSQ, boolean isUnLockInputValid) {
        if (optionalOpReturnType.isPresent()) {
            // We use the opReturnType to find the txType
            return evaluateTxTypeFromOpReturnType(tempTx, optionalOpReturnType.get());
        }

        // No opReturnType, so we check for the remaining possible cases
        if (hasBurntBSQ) {
            // PAY_TRADE_FEE tx has a fee and no opReturn
            return TxType.PAY_TRADE_FEE;
        }

        // UNLOCK tx has no fee, no opReturn but an UNLOCK_OUTPUT at first output.
        if (tempTx.getTempTxOutputs().get(0).getTxOutputType() == TxOutputType.UNLOCK_OUTPUT) {
            // We check if there have been invalid inputs
            if (!isUnLockInputValid)
                return TxType.INVALID;

            return TxType.UNLOCK;
        }

        // TRANSFER_BSQ has no fee, no opReturn and no UNLOCK_OUTPUT at first output
        log.trace("No burned fee and no OP_RETURN, so this is a TRANSFER_BSQ tx.");
        return TxType.TRANSFER_BSQ;
    }

    @VisibleForTesting
    static TxType evaluateTxTypeFromOpReturnType(TempTx tempTx, OpReturnType opReturnType) {
        switch (opReturnType) {
            case PROPOSAL:
                return TxType.PROPOSAL;
            case COMPENSATION_REQUEST:
            case REIMBURSEMENT_REQUEST:
                boolean hasCorrectNumOutputs = tempTx.getTempTxOutputs().size() >= 3;
                if (!hasCorrectNumOutputs) {
                    log.warn("Compensation/reimbursement request tx need to have at least 3 outputs");
                    // Such a transaction cannot be created by the Bisq client and is considered invalid.
                    return TxType.INVALID;
                }

                TempTxOutput issuanceTxOutput = tempTx.getTempTxOutputs().get(1);
                boolean hasIssuanceOutput = issuanceTxOutput.getTxOutputType() == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT;
                if (!hasIssuanceOutput) {
                    log.warn("Compensation/reimbursement request txOutput type of output at index 1 need to be ISSUANCE_CANDIDATE_OUTPUT. " +
                            "TxOutputType={}", issuanceTxOutput.getTxOutputType());
                    // Such a transaction cannot be created by the Bisq client and is considered invalid.
                    return TxType.INVALID;
                }

                return opReturnType == OpReturnType.COMPENSATION_REQUEST ?
                        TxType.COMPENSATION_REQUEST :
                        TxType.REIMBURSEMENT_REQUEST;
            case BLIND_VOTE:
                return TxType.BLIND_VOTE;
            case VOTE_REVEAL:
                return TxType.VOTE_REVEAL;
            case LOCKUP:
                return TxType.LOCKUP;
            case ASSET_LISTING_FEE:
                return TxType.ASSET_LISTING_FEE;
            case PROOF_OF_BURN:
                return TxType.PROOF_OF_BURN;
            default:
                log.warn("We got a BSQ tx with an unknown OP_RETURN. tx={}, opReturnType={}", tempTx, opReturnType);
                // We tolerate such an incorrect tx and do not burn the BSQ. We might need that in case we add new
                // opReturn types in future.
                return TxType.IRREGULAR;
        }
    }
}
