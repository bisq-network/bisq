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

package io.bisq.core.dao.blockchain.parse;

import io.bisq.core.dao.DaoConstants;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import io.bisq.core.dao.blockchain.vo.TxOutputType;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Utils;

import javax.inject.Inject;
import java.util.List;

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
    boolean processDaoOpReturnData(Tx tx, int index, long bsqFee,
                                   int blockHeight, TxOutput btcOutput, TxOutput bsqOutput) {
        List<TxOutput> txOutputs = tx.getOutputs();
        TxOutput txOutput = txOutputs.get(index);
        final long txOutputValue = txOutput.getValue();
        // If we get an OP_RETURN it has to be the last output and the txOutputValue has to be 0 as well there have be at least one bsqOutput
        if (txOutputValue == 0 && index == txOutputs.size() - 1 && bsqFee > 0) {
            byte[] opReturnData = txOutput.getOpReturnData();
            // We expect at least the type byte
            if (opReturnData != null && opReturnData.length > 1) {
                txOutput.setTxOutputType(TxOutputType.OP_RETURN_OUTPUT);
                switch (opReturnData[0]) {
                    case DaoConstants.OP_RETURN_TYPE_COMPENSATION_REQUEST:
                        return compensationRequestVerification.processOpReturnData(tx, opReturnData, txOutput,
                                bsqFee, blockHeight, btcOutput);
                    case DaoConstants.OP_RETURN_TYPE_VOTE:
                        // TODO: Handle missing bsqOutput, is it considered an invalid vote?
                        if (bsqOutput != null) {
                            return votingVerification.processOpReturnData(tx, opReturnData, txOutput, bsqFee, blockHeight, bsqOutput);
                        } else {
                            log.warn("Voting tx is missing bsqOutput for vote base txid={}", tx.getId());
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
        return false;
    }
}
