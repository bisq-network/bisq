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

import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.SpentInfo;
import bisq.core.dao.state.model.blockchain.TxOutput;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.model.blockchain.TxOutputType;

import javax.inject.Inject;

import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Processes TxInput and add input value to available balance if the input is a valid BSQ input.
 */
@Slf4j
public class TxInputParser {
    private final DaoStateService daoStateService;

    // Getters
    @Getter
    private long accumulatedInputValue = 0;
    @Getter
    private long burntBondValue = 0;
    @Getter
    private int unlockBlockHeight;
    @Getter
    private Optional<TxOutput> optionalSpentLockupTxOutput = Optional.empty();
    @Getter
    private boolean isUnLockInputValid = true;

    // Private
    private int numVoteRevealInputs = 0;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TxInputParser(DaoStateService daoStateService) {
        this.daoStateService = daoStateService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    void process(TxOutputKey txOutputKey, int blockHeight, String txId, int inputIndex) {
        if (!daoStateService.isConfiscatedOutput(txOutputKey)) {
            daoStateService.getUnspentTxOutput(txOutputKey)
                    .ifPresent(connectedTxOutput -> {
                        long inputValue = connectedTxOutput.getValue();
                        accumulatedInputValue += inputValue;

                        // If we are spending an output from a blind vote tx marked as VOTE_STAKE_OUTPUT we save it in our parsingModel
                        // for later verification at the outputs of a reveal tx.
                        TxOutputType connectedTxOutputType = connectedTxOutput.getTxOutputType();
                        switch (connectedTxOutputType) {
                            case UNDEFINED_OUTPUT:
                            case GENESIS_OUTPUT:
                            case BSQ_OUTPUT:
                            case BTC_OUTPUT:
                            case PROPOSAL_OP_RETURN_OUTPUT:
                            case COMP_REQ_OP_RETURN_OUTPUT:
                            case REIMBURSEMENT_OP_RETURN_OUTPUT:
                            case CONFISCATE_BOND_OP_RETURN_OUTPUT:
                            case ISSUANCE_CANDIDATE_OUTPUT:
                                break;
                            case BLIND_VOTE_LOCK_STAKE_OUTPUT:
                                numVoteRevealInputs++;
                                // The connected tx output of the blind vote tx is our input for the reveal tx.
                                // We allow only one input from any blind vote tx otherwise the vote reveal tx is invalid.
                                if (!isVoteRevealInputValid()) {
                                    log.warn("We have a tx which has >1 connected txOutputs marked as BLIND_VOTE_LOCK_STAKE_OUTPUT. " +
                                            "This is not a valid BSQ tx.");
                                }
                                break;
                            case BLIND_VOTE_OP_RETURN_OUTPUT:
                            case VOTE_REVEAL_UNLOCK_STAKE_OUTPUT:
                            case VOTE_REVEAL_OP_RETURN_OUTPUT:
                            case ASSET_LISTING_FEE_OP_RETURN_OUTPUT:
                            case PROOF_OF_BURN_OP_RETURN_OUTPUT:
                                break;
                            case LOCKUP_OUTPUT:
                                // A LOCKUP BSQ txOutput is spent to a corresponding UNLOCK
                                // txInput. The UNLOCK can only be spent after lockTime blocks has passed.
                                isUnLockInputValid = !optionalSpentLockupTxOutput.isPresent();
                                if (isUnLockInputValid) {
                                    optionalSpentLockupTxOutput = Optional.of(connectedTxOutput);
                                    unlockBlockHeight = blockHeight + connectedTxOutput.getLockTime();
                                } else {
                                    log.warn("We have a tx which has >1 connected txOutputs marked as LOCKUP_OUTPUT. " +
                                            "This is not a valid BSQ tx.");
                                }
                                break;
                            case LOCKUP_OP_RETURN_OUTPUT:
                                // Cannot happen
                                break;
                            case UNLOCK_OUTPUT:
                                // This txInput is Spending an UNLOCK txOutput
                                int unlockBlockHeight = connectedTxOutput.getUnlockBlockHeight();
                                if (blockHeight < unlockBlockHeight) {
                                    accumulatedInputValue -= inputValue;
                                    burntBondValue += inputValue;

                                    log.warn("We got a tx which spends the output from an unlock tx but before the " +
                                            "unlockTime has passed. That leads to burned BSQ! " +
                                            "blockHeight={}, unLockHeight={}", blockHeight, unlockBlockHeight);
                                }
                                break;
                            case INVALID_OUTPUT:
                            default:
                                break;
                        }

                        daoStateService.setSpentInfo(connectedTxOutput.getKey(), new SpentInfo(blockHeight, txId, inputIndex));
                        daoStateService.removeUnspentTxOutput(connectedTxOutput);
                    });
        } else {
            log.warn("Connected txOutput {} at input {} of txId {} is confiscated ", txOutputKey, inputIndex, txId);
        }
    }

    private boolean isVoteRevealInputValid() {
        return numVoteRevealInputs == 1;
    }
}
