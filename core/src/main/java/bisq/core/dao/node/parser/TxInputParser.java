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

import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputKey;
import bisq.core.dao.state.blockchain.TxOutputType;

import javax.inject.Inject;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Processes TxInput and add input value to available balance if the input is a valid BSQ input.
 */
@Slf4j
public class TxInputParser {
    enum VoteRevealInputState {
        UNKNOWN, VALID, INVALID
    }

    @Getter
    private long accumulatedInputValue = 0;
    @Getter
    private long burntBondValue = 0;
    @Getter
    private int unlockBlockHeight;
    @Getter
    private Optional<TxOutput> optionalSpentLockupTxOutput = Optional.empty();

    private final BsqStateService bsqStateService;
    @Getter
    private TxInputParser.VoteRevealInputState voteRevealInputState = TxInputParser.VoteRevealInputState.UNKNOWN;

    //TODO never read from... remove?
    // We use here TxOutput as we do not alter it but take it from the BsqState
    private Set<TxOutput> spentUnlockConnectedTxOutputs = new HashSet<>();

    @Inject
    public TxInputParser(BsqStateService bsqStateService) {
        this.bsqStateService = bsqStateService;
    }

    @SuppressWarnings("IfCanBeSwitch")
    void process(TxOutputKey txOutputKey, int blockHeight, String txId, int inputIndex) {
        bsqStateService.getUnspentTxOutput(txOutputKey)
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
                        case CONFISCATE_BOND_OP_RETURN_OUTPUT:
                        case ISSUANCE_CANDIDATE_OUTPUT:
                            break;
                        case BLIND_VOTE_LOCK_STAKE_OUTPUT:
                            if (voteRevealInputState == TxInputParser.VoteRevealInputState.UNKNOWN) {
                                // The connected tx output of the blind vote tx is our input for the reveal tx.
                                // We allow only one input from any blind vote tx otherwise the vote reveal tx is invalid.
                                voteRevealInputState = TxInputParser.VoteRevealInputState.VALID;
                            } else {
                                log.warn("We have a tx which has >1 connected txOutputs marked as BLIND_VOTE_LOCK_STAKE_OUTPUT. " +
                                        "This is not a valid BSQ tx.");
                                voteRevealInputState = TxInputParser.VoteRevealInputState.INVALID;
                            }
                            break;
                        case BLIND_VOTE_OP_RETURN_OUTPUT:
                        case VOTE_REVEAL_UNLOCK_STAKE_OUTPUT:
                        case VOTE_REVEAL_OP_RETURN_OUTPUT:
                            break;
                        case LOCKUP_OUTPUT:
                            // A LOCKUP BSQ txOutput is spent to a corresponding UNLOCK
                            // txOutput. The UNLOCK can only be spent after lockTime blocks has passed.
                            if (!optionalSpentLockupTxOutput.isPresent()) {
                                optionalSpentLockupTxOutput = Optional.of(connectedTxOutput);
                                unlockBlockHeight = blockHeight + connectedTxOutput.getLockTime();
                            }
                            break;
                        case LOCKUP_OP_RETURN_OUTPUT:
                            break;
                        case UNLOCK_OUTPUT:
                            // This txInput is Spending an UNLOCK txOutput
                            spentUnlockConnectedTxOutputs.add(connectedTxOutput);

                            //TODO  We should add unlockBlockHeight to TempTxOutput and remove unlockBlockHeight from tempTx
                            // then we can use connectedTxOutput to access the unlockBlockHeight instead of the tx
                            bsqStateService.getTx(connectedTxOutput.getTxId()).ifPresent(unlockTx -> {
                                // Only count the input as BSQ input if spent after unlock time
                                if (blockHeight < unlockTx.getUnlockBlockHeight()) {
                                    accumulatedInputValue -= inputValue;
                                    burntBondValue += inputValue;
                                }
                            });
                            break;
                        case INVALID_OUTPUT:
                        default:
                            break;
                    }

                    bsqStateService.setSpentInfo(connectedTxOutput.getKey(), new SpentInfo(blockHeight, txId, inputIndex));
                    bsqStateService.removeUnspentTxOutput(connectedTxOutput);
                });
    }
}
