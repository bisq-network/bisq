/*
 * This file is part of bisq.
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

package io.bisq.core.dao.blockchain.parse;

import io.bisq.core.dao.DaoConstants;
import io.bisq.core.dao.blockchain.vo.TxOutput;
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

    boolean maybeProcessOpReturnData(List<TxOutput> txOutputs, int index, long availableValue, int blockHeight, TxOutput btcOutput) {
        TxOutput txOutput = txOutputs.get(index);
        final long txOutputValue = txOutput.getValue();
        if (txOutputValue == 0 && index == txOutputs.size() - 1 && availableValue > 0) {
            // If we get an OP_RETURN it has to be the last output and 
            // the txOutputValue is 0 as well we expect that availableValue>0
            byte[] opReturnData = txOutput.getOpReturnData();
            if (opReturnData != null && opReturnData.length > 1) {
                switch (opReturnData[0]) {
                    case DaoConstants.OP_RETURN_TYPE_COMPENSATION_REQUEST:
                        return compensationRequestVerification.maybeProcessData(opReturnData, txOutput, availableValue, blockHeight, btcOutput);
                    case DaoConstants.OP_RETURN_TYPE_VOTE:
                        return votingVerification.maybeProcessData(opReturnData, txOutput, availableValue, blockHeight);
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
