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

    boolean maybeProcessOpReturnData(Tx tx, int index, long availableValue,
                                     int blockHeight, TxOutput btcOutput, TxOutput bsqOutput) {
        List<TxOutput> txOutputs = tx.getOutputs();
        TxOutput txOutput = txOutputs.get(index);
        final long txOutputValue = txOutput.getValue();
        if (txOutputValue == 0 && index == txOutputs.size() - 1 && availableValue > 0) {
            // If we get an OP_RETURN it has to be the last output and 
            // the txOutputValue is 0 as well we expect that availableValue>0
            byte[] opReturnData = txOutput.getOpReturnData();
            if (opReturnData != null && opReturnData.length > 1) {
                txOutput.setTxOutputType(TxOutputType.OP_RETURN_OUTPUT);
                switch (opReturnData[0]) {
                    case DaoConstants.OP_RETURN_TYPE_COMPENSATION_REQUEST:
                        return compensationRequestVerification.maybeProcessData(tx, opReturnData, txOutput,
                                availableValue, blockHeight, btcOutput);
                    case DaoConstants.OP_RETURN_TYPE_VOTE:
                        return votingVerification.maybeProcessData(tx, opReturnData, txOutput, availableValue, blockHeight, bsqOutput);
                    default:
                        log.warn("OP_RETURN data version does not match expected version bytes. opReturnData={}",
                                Utils.HEX.encode(opReturnData));
                        break;
                }
            }
        }
        return false;
    }
}
