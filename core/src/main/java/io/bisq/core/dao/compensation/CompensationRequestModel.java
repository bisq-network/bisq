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

package io.bisq.core.dao.compensation;

import io.bisq.core.dao.blockchain.parse.BsqChainState;
import io.bisq.core.dao.blockchain.parse.PeriodVerification;
import io.bisq.core.dao.blockchain.parse.VotingVerification;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class CompensationRequestModel {
    private final BsqChainState bsqChainState;
    private final PeriodVerification periodVerification;
    private final VotingVerification votingVerification;
    @Getter
    private final List<CompensationRequest> list = new ArrayList<>();

    @Inject
    public CompensationRequestModel(BsqChainState bsqChainState,
                                    PeriodVerification periodVerification,
                                    VotingVerification votingVerification) {
        this.bsqChainState = bsqChainState;
        this.periodVerification = periodVerification;
        this.votingVerification = votingVerification;
    }

    public void setPersistedCompensationRequest(List<CompensationRequest> list) {
        this.list.addAll(list);
    }

    public Optional<CompensationRequest> findByAddress(String address) {
        return list.stream()
                .filter(e -> e.getCompensationRequestPayload().getBtcAddress().equals(address))
                .findAny();
    }


    public void addCompensationRequest(CompensationRequest compensationRequest) {
        list.add(compensationRequest);
    }
}
