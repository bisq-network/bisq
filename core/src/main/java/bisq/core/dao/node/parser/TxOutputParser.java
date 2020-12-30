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
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.OpReturnType;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputType;

import bisq.common.config.Config;

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
 *
 * With block 602500 (about 4 weeks after v1.2.0 release) we enforce a new rule which represents a
 * hard fork. Not updated nodes would see an out of sync dao state hash if a relevant transaction would
 * happen again.
 * Further (highly unlikely) consequences could be:
 * If the BSQ output would be sent to a BSQ address the old client would accept that even it is
 * invalid according to the new rules. But sending such an output would require a manually crafted tx
 * (not possible in the UI). Worst case a not updated user would buy invalid BSQ but that is not possible as we
 * enforce update to 1.2.0 for trading a few days after release as that release introduced the new trade protocol
 * and protection tool. Only if both traders would have deactivated filter messages they could trade.
 *
 * Problem description:
 * We did not apply the check to not allow BSQ outputs after we had detected a BTC output.
 * The supported BSQ transactions did not support such cases anyway but we missed an edge case:
 * A trade fee tx in case when the BTC input matches exactly the BTC output
 * (or BTC change was <= the miner fee) and the BSQ fee was > the miner fee. Then we
 * create a change output after the BTC output (using an address from the BTC wallet) and as
 * available BSQ was >= as spent BSQ it was considered a valid BSQ output.
 * There have been observed 5 such transactions where 4 got spent later to a BTC address and by that burned
 * the pending BSQ (spending amount was higher than sending amount). One was still unspent.
 * The BSQ was sitting in the BTC wallet so not even visible as BSQ to the user.
 * If the user would have crafted a custom BSQ tx he could have avoided that the full trade fee was burned.
 *
 * Not a universal rule:
 * We cannot enforce the rule that no BSQ output is permitted to all possible transactions because there can be cases
 * where we need to permit this case.
 * For instance in case we confiscate a lockupTx we have usually 2 BSQ outputs: The first one is the bond which
 * should be confiscated and the second one is the BSQ change output.
 * At confiscating we set the first to TxOutputType.BTC_OUTPUT but we do not want to confiscate
 * the second BSQ change output as well. So we do not apply the rule that no BSQ is allowed once a BTC output is
 * found. Theoretically other transactions could be confiscated as well and all BSQ tx which allow > 1 BSQ outputs
 * would have the same issue as well if the first output gets confiscated.
 * We also don't enforce the rule for irregular or invalid txs which are usually set and detected at the end of
 * the tx parsing which is done in the TxParser. Blind vote and LockupTx with invalid OpReturn would be such cases
 * where we don't want to invalidate the change output (See comments in TxParser).
 *
 * Most transactions created in Bisq (Proposal, blind vote and lockup,...) have only 1 or 2 BSQ
 * outputs but we do not enforce a limit of max. 2 transactions in the parser.
 * We leave for now that flexibility but it should not be considered as a rule. We might strengthen
 * it any time if we find a reason for that (e.g. attack risk) and add checks that no more
 * BSQ outputs are permitted for those txs.
 * Some transactions like issuance, vote reveal and unlock have exactly 1 BSQ output and that rule
 * is enforced.
 */
@Slf4j
class TxOutputParser {
    private static final int ACTIVATE_HARD_FORK_1_HEIGHT_MAINNET = 605000;
    private static final int ACTIVATE_HARD_FORK_1_HEIGHT_TESTNET = 1583054;
    private static final int ACTIVATE_HARD_FORK_1_HEIGHT_REGTEST = 1;

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
    private Optional<Integer> optionalOpReturnIndex = Optional.empty();

    // Private
    private int lockTime;
    private final List<TempTxOutput> utxoCandidates = new ArrayList<>();
    private boolean prohibitMoreBsqOutputs = false;


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

        optionalOpReturnType.ifPresent(e -> optionalOpReturnIndex = Optional.of(tempTxOutput.getIndex()));

        // If we have a LOCKUP opReturn output we save the lockTime to apply it later to the LOCKUP output.
        // We keep that data in that other output as it makes parsing of the UNLOCK tx easier.
        optionalOpReturnType.filter(opReturnType -> opReturnType == OpReturnType.LOCKUP)
                .ifPresent(opReturnType -> lockTime = BondConsensus.getLockTime(opReturnData));
    }

    void processTxOutput(TempTxOutput tempTxOutput) {
        // We don not expect here an opReturn output as we do not get called on the last output. Any opReturn at
        // another output index is invalid.
        if (tempTxOutput.isOpReturnOutput()) {
            tempTxOutput.setTxOutputType(TxOutputType.INVALID_OUTPUT);
            return;
        }

        if (!daoStateService.isConfiscatedOutput(tempTxOutput.getKey())) {
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
            } else if (isHardForkActivated(tempTxOutput) && isIssuanceCandidateTxOutput(tempTxOutput)) {
                // After the hard fork activation we fix a bug with a transaction which would have interpreted the
                // issuance output as BSQ if the availableInputValue was >= issuance amount.
                // Such a tx was never created but as we don't know if it will happen before activation date we cannot
                // enforce the bug fix which represents a rule change before the activation date.
                handleIssuanceCandidateOutput(tempTxOutput);
            } else if (availableInputValue > 0 && availableInputValue >= txOutputValue) {
                if (isHardForkActivated(tempTxOutput) && prohibitMoreBsqOutputs) {
                    handleBtcOutput(tempTxOutput, index);
                } else {
                    handleBsqOutput(tempTxOutput, index, txOutputValue);
                }
            } else {
                handleBtcOutput(tempTxOutput, index);
            }
        } else {
            log.warn("TxOutput {} is confiscated ", tempTxOutput.getKey());
            // We only burn that output
            availableInputValue -= tempTxOutput.getValue();

            // We must not set prohibitMoreBsqOutputs at confiscation transactions as optional
            // BSQ change output (output 2) must not be confiscated.
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
        // We do not need to apply prohibitMoreBsqOutputs as all spendable outputs are set to BTC_OUTPUT anyway.
        utxoCandidates.forEach(output -> output.setTxOutputType(TxOutputType.BTC_OUTPUT));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Whether a transaction is a valid unlock bond transaction or not.
     *
     * @param txOutputValue The value of the current output, in satoshis.
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

        // We do not permit more BSQ outputs after the unlock txo as we don't expect additional BSQ outputs.
        prohibitMoreBsqOutputs = true;
    }

    private boolean isBtcOutputOfBurnFeeTx(TempTxOutput tempTxOutput) {
        if (optionalOpReturnType.isPresent()) {
            int index = tempTxOutput.getIndex();
            switch (optionalOpReturnType.get()) {
                case UNDEFINED:
                    break;
                case PROPOSAL:
                    if (isHardForkActivated(tempTxOutput)) {
                        // We enforce a mandatory BSQ change output.
                        // We need that as similar to ASSET_LISTING_FEE and PROOF_OF_BURN
                        // we could not distinguish between 2 structurally same transactions otherwise (only way here
                        // would be to check the proposal fee as that is known from the params).
                        return index >= 1;
                    }
                    break;
                case COMPENSATION_REQUEST:
                    break;
                case REIMBURSEMENT_REQUEST:
                    break;
                case BLIND_VOTE:
                    if (isHardForkActivated(tempTxOutput)) {
                        // After the hard fork activation we fix a bug with a transaction which would have interpreted the
                        // burned vote fee output as BSQ if the vote fee was >= miner fee.
                        // Such a tx was never created but as we don't know if it will happen before activation date we cannot
                        // enforce the bug fix which represents a rule change before the activation date.

                        // If it is the vote stake output we return false.
                        if (index == 0) {
                            return false;
                        }

                        // There must be a vote fee left
                        if (availableInputValue <= 0) {
                            return false;
                        }

                        // Burned BSQ output is last output before opReturn.
                        // We could have also a BSQ change output as last output before opReturn but that will
                        // be detected at blindVoteFee check.
                        // We always have the BSQ change before the burned BSQ output if both are present.
                        checkArgument(optionalOpReturnIndex.isPresent());
                        if (index != optionalOpReturnIndex.get() - 1) {
                            return false;
                        }

                        // Without checking the fee we would not be able to distinguish between 2 structurally same transactions, one
                        // where the output is burned BSQ and one where it is a BSQ change output.
                        long blindVoteFee = daoStateService.getParamValueAsCoin(Param.BLIND_VOTE_FEE, tempTxOutput.getBlockHeight()).value;
                        return availableInputValue == blindVoteFee;
                    }
                    break;
                case VOTE_REVEAL:
                    break;
                case LOCKUP:
                    break;
                case ASSET_LISTING_FEE:
                case PROOF_OF_BURN:
                    // Asset listing fee and proof of burn tx are structurally the same.

                    // We need to require one BSQ change output as we could otherwise not be able to distinguish between 2
                    // structurally same transactions where only the BSQ fee is different. In case of asset listing fee and proof of
                    // burn it is a user input, so it is not known to the parser, instead we derive the burned fee from the parser.
                    // In case of proposal fee we could derive it from the params.

                    // Case 1: 10 BSQ fee to burn
                    // In: 17 BSQ
                    // Out: BSQ change 7 BSQ -> valid BSQ
                    // Out: OpReturn
                    // Miner fee: 1000 sat  (10 BSQ burned)


                    // Case 2: 17 BSQ fee to burn
                    // In: 17 BSQ
                    // Out: burned BSQ change 7 BSQ -> BTC (7 BSQ burned)
                    // Out: OpReturn
                    // Miner fee: 1000 sat  (10 BSQ burned)
                    return index >= 1;
            }
        }
        return false;
    }

    private boolean isIssuanceCandidateTxOutput(TempTxOutput tempTxOutput) {
        // If we have BSQ left as fee and we are at the second output we interpret it as a compensation request output.
        return availableInputValue > 0 &&
                tempTxOutput.getIndex() == 1 &&
                optionalOpReturnType.isPresent() &&
                (optionalOpReturnType.get() == OpReturnType.COMPENSATION_REQUEST ||
                        optionalOpReturnType.get() == OpReturnType.REIMBURSEMENT_REQUEST);
    }

    private void handleIssuanceCandidateOutput(TempTxOutput tempTxOutput) {
        // We do not permit more BSQ outputs after the issuance candidate.
        prohibitMoreBsqOutputs = true;

        // We store the candidate but we don't apply the TxOutputType yet as we need to verify the fee after all
        // outputs are parsed and check the phase. The TxParser will do that....
        optionalIssuanceCandidate = Optional.of(tempTxOutput);
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

            // We do not permit more BSQ outputs after the VOTE_REVEAL_UNLOCK_STAKE_OUTPUT.
            prohibitMoreBsqOutputs = true;
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
        if (isHardForkActivated(txOutput)) {
            txOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);

            // For regular transactions we don't permit BSQ outputs after a BTC output was detected.
            prohibitMoreBsqOutputs = true;
        } else {
            // If we have BSQ left as fee and we are at the second output it might be a compensation request output.
            // We store the candidate but we don't apply the TxOutputType yet as we need to verify the fee after all
            // outputs are parsed and check the phase. The TxParser will do that....
            if (availableInputValue > 0 &&
                    index == 1 &&
                    optionalOpReturnType.isPresent() &&
                    (optionalOpReturnType.get() == OpReturnType.COMPENSATION_REQUEST ||
                            optionalOpReturnType.get() == OpReturnType.REIMBURSEMENT_REQUEST)) {
                optionalIssuanceCandidate = Optional.of(txOutput);

                // We do not permit more BSQ outputs after the issuance candidate.
                prohibitMoreBsqOutputs = true;
            } else {
                txOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);

                // For regular transactions we don't permit BSQ outputs after a BTC output was detected.
                prohibitMoreBsqOutputs = true;
            }
        }
    }

    private boolean isHardForkActivated(TempTxOutput tempTxOutput) {
        return tempTxOutput.getBlockHeight() >= getActivateHardFork1Height();
    }

    private int getActivateHardFork1Height() {
        return Config.baseCurrencyNetwork().isMainnet() ? ACTIVATE_HARD_FORK_1_HEIGHT_MAINNET :
                Config.baseCurrencyNetwork().isTestnet() ? ACTIVATE_HARD_FORK_1_HEIGHT_TESTNET :
                        ACTIVATE_HARD_FORK_1_HEIGHT_REGTEST;
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
