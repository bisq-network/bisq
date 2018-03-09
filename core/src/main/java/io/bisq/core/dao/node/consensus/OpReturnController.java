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

package io.bisq.core.dao.node.consensus;

import io.bisq.core.dao.OpReturnTypes;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Verifies if a given transaction is a BSQ OP_RETURN transaction.
 */
@Slf4j
public class OpReturnController {
    private final CompensationRequestController compensationRequestController;
    private final VotingController votingController;

    @Inject
    public OpReturnController(CompensationRequestController compensationRequestController,
                              VotingController votingController) {
        this.compensationRequestController = compensationRequestController;
        this.votingController = votingController;
    }

    public void process(TxOutput txOutput, Tx tx, int index, long bsqFee, int blockHeight, TxOutputsController.MutableState mutableState) {
        final long txOutputValue = txOutput.getValue();
        // A BSQ OP_RETURN has to be the last output, the txOutputValue has to be 0 as well as there have to be a BSQ fee.
        if (txOutputValue == 0 && index == tx.getOutputs().size() - 1 && bsqFee > 0) {
            byte[] opReturnData = txOutput.getOpReturnData();
            checkArgument(opReturnData != null, "opReturnData must not be null");
            // All BSQ OP_RETURN txs have at least a type byte
            if (opReturnData.length >= 1) {
                // Check with the type byte which kind of OP_RETURN we have.
                switch (opReturnData[0]) {
                    case OpReturnTypes.COMPENSATION_REQUEST:
                        if (compensationRequestController.verify(opReturnData, bsqFee, blockHeight, mutableState)) {
                            compensationRequestController.applyStateChange(tx, txOutput, mutableState);
                        }
                    case OpReturnTypes.VOTE:
                        // TODO
                    case OpReturnTypes.VOTE_RELEASE:
                        // TODO
                    case OpReturnTypes.LOCK_UP:
                        // TODO
                    case OpReturnTypes.UNLOCK:
                        // TODO
                    default:
                        log.warn("OP_RETURN version of the BSQ tx ={} does not match expected version bytes. opReturnData={}",
                                tx.getId(), Utils.HEX.encode(opReturnData));
                        break;
                }
            } else {
                log.warn("opReturnData is null or has no content. opReturnData={}", Utils.HEX.encode(opReturnData));
            }
        } else {
            log.warn("opReturnData is not matching DAO rules txId={} outValue={} index={} #outputs={} bsqFee={}",
                    tx.getId(), txOutputValue, index, tx.getOutputs().size(), bsqFee);
        }
    }
}
