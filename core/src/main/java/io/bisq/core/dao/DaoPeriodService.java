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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.bisq.common.app.DevEnv;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.dao.blockchain.parse.BsqChainState;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.compensation.CompensationRequest;
import io.bisq.core.dao.vote.VotingDefaultValues;
import io.bisq.core.dao.vote.VotingService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DaoPeriodService {
    private static final Logger log = LoggerFactory.getLogger(DaoPeriodService.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    // phase period: 30 days + 30 blocks
    public enum Phase {
        // TODO for testing
        UNDEFINED(0),
        OPEN_FOR_COMPENSATION_REQUESTS(10),
        BREAK1(2),
        OPEN_FOR_VOTING(2),
        BREAK2(2),
        VOTE_CONFIRMATION(2),
        BREAK3(2);

      /* UNDEFINED(0),
        OPEN_FOR_COMPENSATION_REQUESTS(144 * 23),
        BREAK1(10),
        OPEN_FOR_VOTING(144 * 4),
        BREAK2(10),
        VOTE_CONFIRMATION(144 * 3),
        BREAK3(10);*/

        /**
         * 144 blocks is 1 day if a block is found each 10 min.
         */
        @Getter
        private int blocks;

        Phase(int blocks) {
            this.blocks = blocks;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final BtcWalletService btcWalletService;
    private BsqChainState bsqChainState;
    private final VotingDefaultValues votingDefaultValues;
    private final VotingService votingService;
    @Getter
    private ObjectProperty<Phase> phaseProperty = new SimpleObjectProperty<>(Phase.UNDEFINED);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoPeriodService(BtcWalletService btcWalletService, BsqChainState bsqChainState, VotingDefaultValues votingDefaultValues, VotingService votingService) {
        this.btcWalletService = btcWalletService;
        this.bsqChainState = bsqChainState;
        this.votingDefaultValues = votingDefaultValues;
        this.votingService = votingService;
    }

    public void onAllServicesInitialized() {
       /* bestChainHeight = btcWalletService.getBestChainHeight();
        checkArgument(bestChainHeight >= GENESIS_BLOCK_HEIGHT, "GENESIS_BLOCK_HEIGHT must be in the past");

        applyVotingResults(votingDefaultValues, bestChainHeight, GENESIS_BLOCK_HEIGHT);*/

        btcWalletService.addNewBestBlockListener(block -> {
            phaseProperty.set(calculatePhase(getRelativeBlocksInCycle(BsqChainState.getGenesisHeight(), block.getHeight())));
        });

        phaseProperty.set(calculatePhase(getRelativeBlocksInCycle(BsqChainState.getGenesisHeight(), btcWalletService.getBestChainHeight())));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    public boolean isInCompensationRequestPhase(CompensationRequest compensationRequest) {
        Tx tx = bsqChainState.getTxMap().get(compensationRequest.getCompensationRequestPayload().getTxId());
        return tx != null && isInCompensationRequestPhase(tx.getBlockHeight(),
                btcWalletService.getBestChainHeight(),
                BsqChainState.getGenesisHeight(),
                Phase.OPEN_FOR_COMPENSATION_REQUESTS.getBlocks(),
                getTotalPeriodInBlocks());
    }

    public long getVotingResultPeriod() {
        return votingDefaultValues.getCompensationRequestPeriodInBlocks() +
                votingDefaultValues.getBreakBetweenPeriodsInBlocks() +
                votingDefaultValues.getVotingPeriodInBlocks() +
                votingDefaultValues.getBreakBetweenPeriodsInBlocks();
    }
/*
    public int getTotalPeriodInBlocks() {
        return votingDefaultValues.getCompensationRequestPeriodInBlocks() +
                votingDefaultValues.getVotingPeriodInBlocks() +
                votingDefaultValues.getFundingPeriodInBlocks() +
                3 * votingDefaultValues.getBreakBetweenPeriodsInBlocks();
    }*/

    public int getTotalPeriodInBlocks() {
        int blocks = 0;
        for (int i = 0; i < Phase.values().length; i++) {
            blocks += Phase.values()[i].getBlocks();
        }
        return blocks;
    }

    public int getAbsoluteStartBlockOfPhase(int chainHeight, Phase phase) {
        return getAbsoluteStartBlockOfPhase(chainHeight,
                BsqChainState.getGenesisHeight(),
                getTotalPeriodInBlocks(),
                phase);
    }

    public int getNumCycles(int chainHeight) {
        return getNumCycles(chainHeight,
                BsqChainState.getGenesisHeight(),
                getTotalPeriodInBlocks());
    }

    public int getAbsoluteEndBlockOfPhase(int chainHeight, Phase phase) {
        return getAbsoluteEndBlockOfPhase(chainHeight,
                BsqChainState.getGenesisHeight(),
                getTotalPeriodInBlocks(),
                phase);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    @VisibleForTesting
    int getRelativeBlocksInCycle(int genesisHeight, int bestChainHeight) {
        int totalPhaseBlocks = getTotalPeriodInBlocks();
        log.info("bestChainHeight={}", bestChainHeight);
        return (bestChainHeight - genesisHeight) % totalPhaseBlocks;
    }

    @VisibleForTesting
    Phase calculatePhase(int blocksInNewPhase) {
        log.info("blocksInNewPhase={}", blocksInNewPhase);
        if (blocksInNewPhase < Phase.OPEN_FOR_COMPENSATION_REQUESTS.getBlocks())
            return Phase.OPEN_FOR_COMPENSATION_REQUESTS;
        else if (blocksInNewPhase < Phase.OPEN_FOR_COMPENSATION_REQUESTS.getBlocks() +
                Phase.BREAK1.getBlocks())
            return Phase.BREAK1;
        else if (blocksInNewPhase < Phase.OPEN_FOR_COMPENSATION_REQUESTS.getBlocks() +
                Phase.BREAK1.getBlocks() +
                Phase.OPEN_FOR_VOTING.getBlocks())
            return Phase.OPEN_FOR_VOTING;
        else if (blocksInNewPhase < Phase.OPEN_FOR_COMPENSATION_REQUESTS.getBlocks() +
                Phase.BREAK1.getBlocks() +
                Phase.OPEN_FOR_VOTING.getBlocks() +
                Phase.BREAK2.getBlocks())
            return Phase.BREAK2;
        else if (blocksInNewPhase < Phase.OPEN_FOR_COMPENSATION_REQUESTS.getBlocks() +
                Phase.BREAK1.getBlocks() +
                Phase.OPEN_FOR_VOTING.getBlocks() +
                Phase.BREAK2.getBlocks() +
                Phase.VOTE_CONFIRMATION.getBlocks())
            return Phase.VOTE_CONFIRMATION;
        else if (blocksInNewPhase < Phase.OPEN_FOR_COMPENSATION_REQUESTS.getBlocks() +
                Phase.BREAK1.getBlocks() +
                Phase.OPEN_FOR_VOTING.getBlocks() +
                Phase.BREAK2.getBlocks() +
                Phase.VOTE_CONFIRMATION.getBlocks() +
                Phase.BREAK3.getBlocks())
            return Phase.BREAK3;
        else {
            log.error("blocksInNewPhase is not covered by phase checks. blocksInNewPhase={}", blocksInNewPhase);
            if (DevEnv.DEV_MODE)
                throw new RuntimeException("blocksInNewPhase is not covered by phase checks. blocksInNewPhase=" + blocksInNewPhase);
            else
                return Phase.UNDEFINED;
        }
    }

    @VisibleForTesting
    boolean isInCompensationRequestPhase(int height, int chainHeight, int genesisHeight, int requestPhaseInBlocks, int totalPeriodInBlocks) {
        if (height >= genesisHeight && chainHeight >= genesisHeight && chainHeight >= height) {
            height -= genesisHeight;
            chainHeight -= genesisHeight;
            height = height % totalPeriodInBlocks;
            chainHeight = chainHeight % totalPeriodInBlocks;
            return height <= requestPhaseInBlocks && chainHeight <= requestPhaseInBlocks;
        } else {
            return false;
        }
    }

    @VisibleForTesting
    int getAbsoluteStartBlockOfPhase(int chainHeight, int genesisHeight, int totalPeriodInBlocks, Phase phase) {
        if (chainHeight >= genesisHeight)
            return genesisHeight + (getNumCycles(chainHeight, genesisHeight, totalPeriodInBlocks) - 1) * totalPeriodInBlocks + getStartBlockOfPhase(phase);
        else
            return genesisHeight + getStartBlockOfPhase(phase);
    }

    @VisibleForTesting
    int getAbsoluteEndBlockOfPhase(int chainHeight, int genesisHeight, int totalPeriodInBlocks, Phase phase) {
        return getAbsoluteStartBlockOfPhase(chainHeight, genesisHeight, totalPeriodInBlocks, phase) + phase.getBlocks();
    }

    @VisibleForTesting
    int getNumCycles(int chainHeight, int genesisHeight, int totalPeriodInBlocks) {
        if (chainHeight >= genesisHeight)
            return (chainHeight - genesisHeight) / totalPeriodInBlocks + 1; // We start with 1 (first cycle starts with genesis)
        else
            return 0;
    } //TODO test

    @VisibleForTesting
    int getStartBlockOfPhase(Phase phase) {
        int blocks = 0;
        for (int i = 0; i < Phase.values().length; i++) {
            final Phase currentPhase = Phase.values()[i];
            if (currentPhase == phase)
                break;

            blocks += currentPhase.getBlocks();
        }
        return blocks;
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
}
