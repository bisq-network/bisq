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

/**
 * Provide utilities about the phase and cycle of the request/voting cycle.
 * A cycle is the sequence of distinct phases. The first cycle and phase starts with the genesis block height.
 * All time events are measured in blocks.
 * The index of first cycle is 1 not 0! The index of first block in first phase is 0 (genesis height).
 * The length of blocks of each phase is number of blocks starting with index 0.
 */
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
            phaseProperty.set(calculatePhase(getRelativeBlocksInCycle(BsqChainState.getGenesisHeight(), block.getHeight(), getNumBlocksOfCycle())));
        });

        phaseProperty.set(calculatePhase(getRelativeBlocksInCycle(BsqChainState.getGenesisHeight(), btcWalletService.getBestChainHeight(), getNumBlocksOfCycle())));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    public boolean isInCompensationRequestPhase(CompensationRequest compensationRequest) {
        Tx tx = bsqChainState.getTxMap().get(compensationRequest.getCompensationRequestPayload().getTxId());
        return tx != null && isTxHeightInPhase(tx.getBlockHeight(),
                btcWalletService.getBestChainHeight(),
                BsqChainState.getGenesisHeight(),
                Phase.OPEN_FOR_COMPENSATION_REQUESTS.getBlocks(),
                getNumBlocksOfCycle());
    }

    public boolean isInCurrentCycle(CompensationRequest compensationRequest) {
        Tx tx = bsqChainState.getTxMap().get(compensationRequest.getCompensationRequestPayload().getTxId());
        return tx != null && isInCurrentCycle(tx.getBlockHeight(),
                btcWalletService.getBestChainHeight(),
                BsqChainState.getGenesisHeight(),
                getNumBlocksOfCycle());
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


    public int getNumOfStartedCycles(int chainHeight) {
        return getNumOfStartedCycles(chainHeight,
                BsqChainState.getGenesisHeight(),
                getNumBlocksOfCycle());
    }

    public int getNumOfCompletedCycles(int chainHeight) {
        return getNumOfCompletedCycles(chainHeight,
                BsqChainState.getGenesisHeight(),
                getNumBlocksOfCycle());
    }

    public int getAbsoluteStartBlockOfPhase(int chainHeight, Phase phase) {
        return getAbsoluteStartBlockOfPhase(chainHeight,
                BsqChainState.getGenesisHeight(),
                phase,
                getNumBlocksOfCycle());
    }


    public int getAbsoluteEndBlockOfPhase(int chainHeight, Phase phase) {
        return getAbsoluteEndBlockOfPhase(chainHeight,
                BsqChainState.getGenesisHeight(),
                phase,
                getNumBlocksOfCycle());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    @VisibleForTesting
    int getRelativeBlocksInCycle(int genesisHeight, int bestChainHeight, int numBlocksOfCycle) {
        return (bestChainHeight - genesisHeight) % numBlocksOfCycle;
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
    boolean isTxHeightInPhase(int txHeight, int chainHeight, int genesisHeight, int requestPhaseInBlocks, int numBlocksOfCycle) {
        if (txHeight >= genesisHeight && chainHeight >= genesisHeight && chainHeight >= txHeight) {
            int numBlocksOfTxHeightSinceGenesis = txHeight - genesisHeight;
            int numBlocksOfChainHeightSinceGenesis = chainHeight - genesisHeight;
            int numBlocksOfTxHeightInCycle = numBlocksOfTxHeightSinceGenesis % numBlocksOfCycle;
            int numBlocksOfChainHeightInCycle = numBlocksOfChainHeightSinceGenesis % numBlocksOfCycle;
            return numBlocksOfTxHeightInCycle <= requestPhaseInBlocks && numBlocksOfChainHeightInCycle <= requestPhaseInBlocks;
        } else {
            return false;
        }
    }

    @VisibleForTesting
    boolean isInCurrentCycle(int txHeight, int chainHeight, int genesisHeight, int numBlocksOfCycle) {
        final int numOfCompletedCycles = getNumOfCompletedCycles(chainHeight, genesisHeight, numBlocksOfCycle);
        final int blockAtCycleStart = genesisHeight + numOfCompletedCycles * numBlocksOfCycle;
        final int blockAtCycleEnd = blockAtCycleStart + numBlocksOfCycle - 1;
        return txHeight <= chainHeight &&
                chainHeight >= genesisHeight &&
                txHeight >= blockAtCycleStart &&
                txHeight <= blockAtCycleEnd;
    }

    @VisibleForTesting
    int getAbsoluteStartBlockOfPhase(int chainHeight, int genesisHeight, Phase phase, int numBlocksOfCycle) {
        return genesisHeight + getNumOfCompletedCycles(chainHeight, genesisHeight, numBlocksOfCycle) * getNumBlocksOfCycle() + getNumBlocksOfPhaseStart(phase);
    }

    @VisibleForTesting
    int getAbsoluteEndBlockOfPhase(int chainHeight, int genesisHeight, Phase phase, int numBlocksOfCycle) {
        return getAbsoluteStartBlockOfPhase(chainHeight, genesisHeight, phase, numBlocksOfCycle) + phase.getBlocks() - 1;
    }

    @VisibleForTesting
    int getNumOfStartedCycles(int chainHeight, int genesisHeight, int numBlocksOfCycle) {
        if (chainHeight >= genesisHeight)
            return getNumOfCompletedCycles(chainHeight, genesisHeight, numBlocksOfCycle) + 1;
        else
            return 0;
    }

    int getNumOfCompletedCycles(int chainHeight, int genesisHeight, int numBlocksOfCycle) {
        if (chainHeight >= genesisHeight && numBlocksOfCycle > 0)
            return (chainHeight - genesisHeight) / numBlocksOfCycle;
        else
            return 0;
    }

    @VisibleForTesting
    int getNumBlocksOfPhaseStart(Phase phase) {
        int blocks = 0;
        for (int i = 0; i < Phase.values().length; i++) {
            final Phase currentPhase = Phase.values()[i];
            if (currentPhase == phase)
                break;

            blocks += currentPhase.getBlocks();
        }
        return blocks;
    }

    @VisibleForTesting
    int getNumBlocksOfCycle() {
        int blocks = 0;
        for (int i = 0; i < Phase.values().length; i++) {
            blocks += Phase.values()[i].getBlocks();
        }
        return blocks;
    }

    private void applyVotingResults(VotingDefaultValues votingDefaultValues, int bestChainHeight, int genesisBlockHeight) {
        int pastBlocks = bestChainHeight - genesisBlockHeight;
        int from = genesisBlockHeight;
        boolean hasVotingPeriods = pastBlocks > from + getVotingResultPeriod();
        while (hasVotingPeriods) {
            long currentRoundPeriod = getNumBlocksOfCycle();
            votingService.applyVotingResultsForRound(votingDefaultValues, from);
            // Don't take getTotalPeriodInBlocks() as voting might have changed periods
            from += currentRoundPeriod;
            hasVotingPeriods = pastBlocks > from + getVotingResultPeriod();
        }
    }
}
