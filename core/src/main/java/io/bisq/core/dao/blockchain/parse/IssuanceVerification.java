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

import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.blockchain.vo.TxOutput;
import io.bisq.core.dao.blockchain.vo.TxOutputType;
import io.bisq.core.dao.blockchain.vo.TxType;
import io.bisq.core.dao.compensation.CompensationRequest;
import io.bisq.core.dao.compensation.CompensationRequestModel;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class IssuanceVerification {
    public static final long MIN_BSQ_ISSUANCE_AMOUNT = 1000;
    public static final long MAX_BSQ_ISSUANCE_AMOUNT = 10_000_000;

    private final BsqChainState bsqChainState;
    private final PeriodVerification periodVerification;
    private final VotingVerification votingVerification;
    private final CompensationRequestModel compensationRequestModel;

    @Inject
    public IssuanceVerification(BsqChainState bsqChainState,
                                PeriodVerification periodVerification,
                                VotingVerification votingVerification,
                                CompensationRequestModel compensationRequestModel) {
        this.bsqChainState = bsqChainState;
        this.periodVerification = periodVerification;
        this.votingVerification = votingVerification;
        this.compensationRequestModel = compensationRequestModel;
    }

    boolean maybeProcessData(Tx tx) {
        List<TxOutput> outputs = tx.getOutputs();
        if (outputs.size() >= 2) {
            TxOutput bsqTxOutput = outputs.get(0);
            TxOutput btcTxOutput = outputs.get(1);
            final String btcAddress = btcTxOutput.getAddress();
            // TODO find address by block range/cycle
            final Optional<CompensationRequest> compensationRequest = compensationRequestModel.findByAddress(btcAddress);
            if (compensationRequest.isPresent()) {
                final CompensationRequest compensationRequest1 = compensationRequest.get();
                final long bsqAmount = bsqTxOutput.getValue();
                final long requestedBtc = compensationRequest1.getCompensationRequestPayload().getRequestedBtc().value;
                long alreadyFundedBtc = 0;
                final int height = btcTxOutput.getBlockHeight();
                Set<TxOutput> issuanceTxs = bsqChainState.findSponsoringBtcOutputsWithSameBtcAddress(btcAddress);
                // Sorting rule: the txs are sorted by inter-block dependency and 
                // at each recursive iteration we add another sorted list which can be parsed, so we have a reproducible
                // sorting.
                for (TxOutput txOutput : issuanceTxs) {
                    if (txOutput.getBlockHeight() < height ||
                            (txOutput.getBlockHeight() == height &&
                                    txOutput.getId().compareTo(btcTxOutput.getId()) == 1)) {
                        alreadyFundedBtc += txOutput.getValue();
                    }
                }
                final long btcAmount = btcTxOutput.getValue();
                if (periodVerification.isInSponsorPeriod(height) &&
                        bsqChainState.existsCompensationRequestBtcAddress(btcAddress) &&
                        votingVerification.isCompensationRequestAccepted(compensationRequest1) &&
                        alreadyFundedBtc + btcAmount <= requestedBtc &&
                        bsqAmount >= MIN_BSQ_ISSUANCE_AMOUNT && bsqAmount <= MAX_BSQ_ISSUANCE_AMOUNT &&
                        votingVerification.isConversionRateValid(height, btcAmount, bsqAmount)) {
                    btcTxOutput.setTxOutputType(TxOutputType.SPONSORING_BTC_OUTPUT);
                    tx.setTxType(TxType.ISSUANCE);
                    return true;
                }
            }
        }
        return false;
    }
}
