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

import io.bisq.common.app.Version;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import io.bisq.core.dao.blockchain.vo.TxOutputType;
import io.bisq.core.dao.blockchain.vo.TxType;
import io.bisq.core.dao.compensation.CompensationRequest;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;

@Slf4j
public class VotingVerification {
    private final BsqChainState bsqChainState;
    private final PeriodVerification periodVerification;

    @Inject
    public VotingVerification(BsqChainState bsqChainState,
                              PeriodVerification periodVerification) {
        this.bsqChainState = bsqChainState;
        this.periodVerification = periodVerification;
    }

    boolean isCompensationRequestAccepted(CompensationRequest compensationRequest) {
        return true;
    }

    boolean isConversionRateValid(int blockHeight, long btcAmount, long bsqAmount) {
        return false;
    }

    boolean maybeProcessData(Tx tx, byte[] opReturnData, TxOutput txOutput, long fee, int blockHeight, TxOutput bsqOutput) {
        if (Version.VOTING_VERSION == opReturnData[1] && opReturnData.length > 22) {
            final int sizeOfCompRequestsVotes = (int) opReturnData[22];
            if (bsqOutput != null &&
                    sizeOfCompRequestsVotes % 2 == 0 &&
                    opReturnData.length % 2 == 1 &&
                    opReturnData.length >= 23 + sizeOfCompRequestsVotes * 2 &&
                    fee == bsqChainState.getVotingFee(blockHeight) &&
                    bsqChainState.isVotingPeriodValid(blockHeight)) {
                txOutput.setTxOutputType(TxOutputType.VOTING_OP_RETURN_OUTPUT);
                tx.setTxType(TxType.VOTE);
                // TODO use bsqOutput as weight
                return true;
            }
        }
        return false;
    }
}
