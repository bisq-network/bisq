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

package io.bisq.core.dao;

import com.google.inject.Inject;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.vote.VotingDefaultValues;
import io.bisq.core.dao.vote.VotingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DaoPeriodService {
    private static final Logger log = LoggerFactory.getLogger(DaoPeriodService.class);

    private static final int GENESIS_BLOCK_HEIGHT = 300;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    public enum Phase {
        UNDEFINED(),
        OPEN_FOR_COMPENSATION_REQUESTS(),
        BREAK1(),
        OPEN_FOR_VOTING(),
        BREAK2(),
        OPEN_FOR_FUNDING(),
        BREAK3()
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final BtcWalletService btcWalletService;
    private final VotingDefaultValues votingDefaultValues;
    private final VotingService votingService;

    private final Phase phase = Phase.UNDEFINED;
    private int bestChainHeight;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoPeriodService(BtcWalletService btcWalletService, VotingDefaultValues votingDefaultValues, VotingService votingService) {
        this.btcWalletService = btcWalletService;
        this.votingDefaultValues = votingDefaultValues;
        this.votingService = votingService;
    }

    public void onAllServicesInitialized() {
       /* bestChainHeight = btcWalletService.getBestChainHeight();
        checkArgument(bestChainHeight >= GENESIS_BLOCK_HEIGHT, "GENESIS_BLOCK_HEIGHT must be in the past");

        applyVotingResults(votingDefaultValues, bestChainHeight, GENESIS_BLOCK_HEIGHT);*/
    }

    private void applyVotingResults(VotingDefaultValues votingDefaultValues, int bestChainHeight, int genesisBlockHeight) {
        int pastBlocks = bestChainHeight - genesisBlockHeight;
        int from = genesisBlockHeight;
        boolean hasVotingPeriods = pastBlocks > from + getVotingResultPeriod();
        while (hasVotingPeriods) {
            long currentRoundPeriod = getTotalPeriodInBlocks();
            votingService.applyVotingResultsForRound(votingDefaultValues, from);
            // Don't take getTotalPeriodInBlocks() as voting might have changed periods
            from += currentRoundPeriod;
            hasVotingPeriods = pastBlocks > from + getVotingResultPeriod();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public long getVotingResultPeriod() {
        return votingDefaultValues.getCompensationRequestPeriodInBlocks() +
                votingDefaultValues.getBreakBetweenPeriodsInBlocks() +
                votingDefaultValues.getVotingPeriodInBlocks() +
                votingDefaultValues.getBreakBetweenPeriodsInBlocks();
    }

    public long getTotalPeriodInBlocks() {
        return votingDefaultValues.getCompensationRequestPeriodInBlocks() +
                votingDefaultValues.getVotingPeriodInBlocks() +
                votingDefaultValues.getFundingPeriodInBlocks() +
                3 * votingDefaultValues.getBreakBetweenPeriodsInBlocks();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Phase getPhase() {
        return phase;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}
