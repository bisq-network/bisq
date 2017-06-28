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

package io.bisq.core.dao.vote;

import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VotingService {
    private static final Logger log = LoggerFactory.getLogger(VotingService.class);
    private final VotingDefaultValues votingDefaultValues;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VotingService(VotingDefaultValues votingDefaultValues) {
        this.votingDefaultValues = votingDefaultValues;
    }

    public VotingDefaultValues applyVotingResultsForRound(VotingDefaultValues votingDefaultValues, int startOfRound) {
        long from = startOfRound +
                votingDefaultValues.getCompensationRequestPeriodInBlocks() +
                votingDefaultValues.getBreakBetweenPeriodsInBlocks();
        long to = from +
                votingDefaultValues.getVotingPeriodInBlocks() +
                votingDefaultValues.getBreakBetweenPeriodsInBlocks();

        getBlocks(from, to);
        getVotedFromBlocks();
        calculateVotingResult();
        return applyVotingResult(votingDefaultValues);

    }

    private void getBlocks(long from, long to) {
        //TODO
    }

    private void getVotedFromBlocks() {
        //TODO


    }

    private void calculateVotingResult() {
        //TODO
    }

    private VotingDefaultValues applyVotingResult(VotingDefaultValues votingDefaultValues) {
        //TODO
        return votingDefaultValues;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}
