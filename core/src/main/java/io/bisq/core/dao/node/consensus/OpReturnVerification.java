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
import io.bisq.core.dao.blockchain.vo.TxOutputType;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;
import java.util.List;

/**
 * Verifies if a given transaction is a BSQ OP_RETURN transaction.
 */
//TODO refactor
@Slf4j
public class OpReturnVerification {
    private final CompensationRequestVerification compensationRequestVerification;
    private final VotingVerification votingVerification;

    @Inject
    public OpReturnVerification(CompensationRequestVerification compensationRequestVerification,
                                VotingVerification votingVerification) {
        this.compensationRequestVerification = compensationRequestVerification;
        this.votingVerification = votingVerification;
    }

    // FIXME bsqOutput can be null in case there is no BSQ change output at comp requests tx
    public void process(Tx tx, int index, long bsqFee, int blockHeight, TxOutput btcOutput, TxOutput bsqOutput) {
        List<TxOutput> txOutputs = tx.getOutputs();
        TxOutput opReturnTxOutput = txOutputs.get(index);
        final long txOutputValue = opReturnTxOutput.getValue();
        // If we get an OP_RETURN it has to be the last output and the txOutputValue has to be 0 as well there have be at least one bsqOutput
        if (txOutputValue == 0 && index == txOutputs.size() - 1 && bsqFee > 0) {
            byte[] opReturnData = opReturnTxOutput.getOpReturnData();
            // We expect at least the type byte
            if (opReturnData != null && opReturnData.length > 1) {
                opReturnTxOutput.setTxOutputType(TxOutputType.OP_RETURN_OUTPUT);
                switch (opReturnData[0]) {
                    case OpReturnTypes.COMPENSATION_REQUEST:
                        if (compensationRequestVerification.verify(opReturnData, bsqFee, blockHeight, btcOutput)) {
                            compensationRequestVerification.applyStateChange(tx, opReturnTxOutput, btcOutput);
                        }
                    case OpReturnTypes.VOTE:
                        // TODO: Handle missing bsqOutput, is it considered an invalid vote?
                        if (bsqOutput != null) {
                            votingVerification.isOpReturn(tx, opReturnData, opReturnTxOutput, bsqFee, blockHeight, bsqOutput);
                        } else {
                            log.warn("Voting tx is missing bsqOutput for vote base txId={}", tx.getId());
                        }
                    default:
                        log.warn("OP_RETURN version of the BSQ tx ={} does not match expected version bytes. opReturnData={}",
                                tx.getId(), Utils.HEX.encode(opReturnData));
                        break;
                }
            } else {
                log.warn("opReturnData is null or has no content. opReturnData={}", opReturnData != null ? Utils.HEX.encode(opReturnData) : "null");
            }
        } else {
            log.warn("opReturnData is not matching DAO rules txId={} outValue={} index={} #outputs={} hasBsqOut={} bsqFee={}",
                    tx.getId(), txOutputValue, index, txOutputs.size(), bsqOutput != null, bsqFee);
        }
    }
}
