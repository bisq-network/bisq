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

import io.bisq.core.dao.DaoPeriodService;
import io.bisq.core.dao.blockchain.parse.PeriodVerification;
import io.bisq.core.dao.blockchain.parse.VotingVerification;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

@Slf4j
public class CompensationRequestModel {

    private final DaoPeriodService daoPeriodService;
    private final PeriodVerification periodVerification;
    private final VotingVerification votingVerification;
    @Getter
    private final ObservableList<CompensationRequest> allRequests = FXCollections.observableArrayList();
    @Getter
    private final FilteredList<CompensationRequest> activeRequests = new FilteredList<>(allRequests);
    @Getter
    private final FilteredList<CompensationRequest> pastRequests = new FilteredList<>(allRequests);

    @Inject
    public CompensationRequestModel(DaoPeriodService daoPeriodService,
                                    PeriodVerification periodVerification,
                                    VotingVerification votingVerification) {
        this.daoPeriodService = daoPeriodService;
        this.periodVerification = periodVerification;
        this.votingVerification = votingVerification;
    }

    public void updateFilteredLists() {
        activeRequests.setPredicate(daoPeriodService::isInCurrentCycle);
        pastRequests.setPredicate(compensationRequest -> {
            return !daoPeriodService.isInCurrentCycle(compensationRequest);
        });
    }

    public void setPersistedCompensationRequest(List<CompensationRequest> list) {
        this.allRequests.clear();
        this.allRequests.addAll(list);
        updateFilteredLists();
    }

    public Optional<CompensationRequest> findByAddress(String address) {
        return allRequests.stream()
                .filter(e -> e.getCompensationRequestPayload().getBsqAddress().equals(address))
                .findAny();
    }

    public void addCompensationRequest(CompensationRequest compensationRequest) {
        allRequests.add(compensationRequest);
        updateFilteredLists();
    }

    public void removeCompensationRequest(CompensationRequest compensationRequest) {
        allRequests.remove(compensationRequest);
        updateFilteredLists();
    }

    public Optional<CompensationRequest> findCompensationRequest(CompensationRequestPayload compensationRequestPayload) {
        return allRequests.stream().filter(e -> e.getCompensationRequestPayload().equals(compensationRequestPayload)).findAny();
    }
}
