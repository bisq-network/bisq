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
import io.bisq.core.dao.blockchain.parse.BsqBlockChain;
import io.bisq.core.dao.blockchain.vo.Tx;
import io.bisq.core.dao.vote.VotingDefaultValues;
import io.bisq.core.dao.vote.VotingService;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Named;

/**
 * Provide information about the phase and cycle of the request/voting cycle.
 * A cycle is the sequence of distinct phases. The first cycle and phase starts with the genesis block height.
 * All time events are measured in blocks.
 * The index of first cycle is 1 not 0! The index of first block in first phase is 0 (genesis height).
 */
@Slf4j
public class DaoPeriodService {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Enum
    ///////////////////////////////////////////////////////////////////////////////////////////

    // phase period: 30 days + 30 blocks
    public enum Phase {
        // TODO for testing
        UNDEFINED(0),
        COMPENSATION_REQUESTS(10),
        BREAK1(2),
        OPEN_FOR_VOTING(2),
        BREAK2(2),
        VOTE_CONFIRMATION(2),
        BREAK3(2);

      /* UNDEFINED(0),
        COMPENSATION_REQUESTS(144 * 23),
        BREAK1(10),
        OPEN_FOR_VOTING(144 * 4),
        BREAK2(10),
        VOTE_CONFIRMATION(144 * 3),
        BREAK3(10);*/

        /**
         * 144 blocks is 1 day if a block is found each 10 min.
         */
        @Getter
        private int durationInBlocks;

        Phase(int durationInBlocks) {
            this.durationInBlocks = durationInBlocks;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final BtcWalletService btcWalletService;
    private BsqBlockChain bsqBlockChain;
    private final VotingDefaultValues votingDefaultValues;
    private final VotingService votingService;
    private final int genesisBlockHeight;
    @Getter
    private ObjectProperty<Phase> phaseProperty = new SimpleObjectProperty<>(Phase.UNDEFINED);
    private int chainHeight;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoPeriodService(BtcWalletService btcWalletService,
                            BsqBlockChain bsqBlockChain,
                            VotingDefaultValues votingDefaultValues,
                            VotingService votingService,
                            @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        this.btcWalletService = btcWalletService;
        this.bsqBlockChain = bsqBlockChain;
        this.votingDefaultValues = votingDefaultValues;
        this.votingService = votingService;
        this.genesisBlockHeight = genesisBlockHeight;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown() {

    }

    public void onAllServicesInitialized() {
        btcWalletService.getChainHeightProperty().addListener((observable, oldValue, newValue) -> {
            onChainHeightChanged((int) newValue);
        });
        onChainHeightChanged(btcWalletService.getChainHeightProperty().get());
    }

    public boolean isTxInPhase(String txId, Phase phase) {
        Tx tx = bsqBlockChain.getTxMap().get(txId);
        return tx != null && isTxInPhase(tx.getBlockHeight(),
                chainHeight,
                genesisBlockHeight,
                phase.getDurationInBlocks(),
                getNumBlocksOfCycle());
    }

    public boolean isTxInCurrentCycle(String txId) {
        Tx tx = bsqBlockChain.getTxMap().get(txId);
        return tx != null && isTxInCurrentCycle(tx.getBlockHeight(),
                chainHeight,
                genesisBlockHeight,
                getNumBlocksOfCycle());
    }

    public boolean isTxInPastCycle(String txId) {
        Tx tx = bsqBlockChain.getTxMap().get(txId);
        return tx != null && isTxInPastCycle(tx.getBlockHeight(),
                chainHeight,
                genesisBlockHeight,
                getNumBlocksOfCycle());
    }

    public int getNumOfStartedCycles(int chainHeight) {
        return getNumOfStartedCycles(chainHeight,
                genesisBlockHeight,
                getNumBlocksOfCycle());
    }

    // Not used yet be leave it
    public int getNumOfCompletedCycles(int chainHeight) {
        return getNumOfCompletedCycles(chainHeight,
                genesisBlockHeight,
                getNumBlocksOfCycle());
    }

    public int getAbsoluteStartBlockOfPhase(int chainHeight, Phase phase) {
        return getAbsoluteStartBlockOfPhase(chainHeight,
                genesisBlockHeight,
                phase,
                getNumBlocksOfCycle());
    }

    public int getAbsoluteEndBlockOfPhase(int chainHeight, Phase phase) {
        return getAbsoluteEndBlockOfPhase(chainHeight,
                genesisBlockHeight,
                phase,
                getNumBlocksOfCycle());
    }

    //TODO
    public long getVotingResultPeriod() {
        return votingDefaultValues.getCompensationRequestPeriodInBlocks() +
                votingDefaultValues.getBreakBetweenPeriodsInBlocks() +
                votingDefaultValues.getVotingPeriodInBlocks() +
                votingDefaultValues.getBreakBetweenPeriodsInBlocks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onChainHeightChanged(int chainHeight) {
        this.chainHeight = chainHeight;
        final int relativeBlocksInCycle = getRelativeBlocksInCycle(genesisBlockHeight, this.chainHeight, getNumBlocksOfCycle());
        phaseProperty.set(calculatePhase(relativeBlocksInCycle));
    }

    @VisibleForTesting
    int getRelativeBlocksInCycle(int genesisHeight, int bestChainHeight, int numBlocksOfCycle) {
        return (bestChainHeight - genesisHeight) % numBlocksOfCycle;
    }

    @VisibleForTesting
    Phase calculatePhase(int blocksInNewPhase) {
        log.info("blocksInNewPhase={}", blocksInNewPhase);
        if (blocksInNewPhase < Phase.COMPENSATION_REQUESTS.getDurationInBlocks())
            return Phase.COMPENSATION_REQUESTS;
        else if (blocksInNewPhase < Phase.COMPENSATION_REQUESTS.getDurationInBlocks() +
                Phase.BREAK1.getDurationInBlocks())
            return Phase.BREAK1;
        else if (blocksInNewPhase < Phase.COMPENSATION_REQUESTS.getDurationInBlocks() +
                Phase.BREAK1.getDurationInBlocks() +
                Phase.OPEN_FOR_VOTING.getDurationInBlocks())
            return Phase.OPEN_FOR_VOTING;
        else if (blocksInNewPhase < Phase.COMPENSATION_REQUESTS.getDurationInBlocks() +
                Phase.BREAK1.getDurationInBlocks() +
                Phase.OPEN_FOR_VOTING.getDurationInBlocks() +
                Phase.BREAK2.getDurationInBlocks())
            return Phase.BREAK2;
        else if (blocksInNewPhase < Phase.COMPENSATION_REQUESTS.getDurationInBlocks() +
                Phase.BREAK1.getDurationInBlocks() +
                Phase.OPEN_FOR_VOTING.getDurationInBlocks() +
                Phase.BREAK2.getDurationInBlocks() +
                Phase.VOTE_CONFIRMATION.getDurationInBlocks())
            return Phase.VOTE_CONFIRMATION;
        else if (blocksInNewPhase < Phase.COMPENSATION_REQUESTS.getDurationInBlocks() +
                Phase.BREAK1.getDurationInBlocks() +
                Phase.OPEN_FOR_VOTING.getDurationInBlocks() +
                Phase.BREAK2.getDurationInBlocks() +
                Phase.VOTE_CONFIRMATION.getDurationInBlocks() +
                Phase.BREAK3.getDurationInBlocks())
            return Phase.BREAK3;
        else {
            log.error("blocksInNewPhase is not covered by phase checks. blocksInNewPhase={}", blocksInNewPhase);
            if (DevEnv.isDevMode())
                throw new RuntimeException("blocksInNewPhase is not covered by phase checks. blocksInNewPhase=" + blocksInNewPhase);
            else
                return Phase.UNDEFINED;
        }
    }

    @VisibleForTesting
    boolean isTxInPhase(int txHeight, int chainHeight, int genesisHeight, int requestPhaseInBlocks, int numBlocksOfCycle) {
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
    boolean isTxInCurrentCycle(int txHeight, int chainHeight, int genesisHeight, int numBlocksOfCycle) {
        final int numOfCompletedCycles = getNumOfCompletedCycles(chainHeight, genesisHeight, numBlocksOfCycle);
        final int blockAtCycleStart = genesisHeight + numOfCompletedCycles * numBlocksOfCycle;
        final int blockAtCycleEnd = blockAtCycleStart + numBlocksOfCycle - 1;
        return txHeight <= chainHeight &&
                chainHeight >= genesisHeight &&
                txHeight >= blockAtCycleStart &&
                txHeight <= blockAtCycleEnd;
    }

    @VisibleForTesting
    boolean isTxInPastCycle(int txHeight, int chainHeight, int genesisHeight, int numBlocksOfCycle) {
        final int numOfCompletedCycles = getNumOfCompletedCycles(chainHeight, genesisHeight, numBlocksOfCycle);
        final int blockAtCycleStart = genesisHeight + numOfCompletedCycles * numBlocksOfCycle;
        return txHeight <= chainHeight &&
                chainHeight >= genesisHeight &&
                txHeight <= blockAtCycleStart;
    }

    @VisibleForTesting
    int getAbsoluteStartBlockOfPhase(int chainHeight, int genesisHeight, Phase phase, int numBlocksOfCycle) {
        return genesisHeight + getNumOfCompletedCycles(chainHeight, genesisHeight, numBlocksOfCycle) * getNumBlocksOfCycle() + getNumBlocksOfPhaseStart(phase);
    }

    @VisibleForTesting
    int getAbsoluteEndBlockOfPhase(int chainHeight, int genesisHeight, Phase phase, int numBlocksOfCycle) {
        return getAbsoluteStartBlockOfPhase(chainHeight, genesisHeight, phase, numBlocksOfCycle) + phase.getDurationInBlocks() - 1;
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

            blocks += currentPhase.getDurationInBlocks();
        }
        return blocks;
    }

    @VisibleForTesting
    int getNumBlocksOfCycle() {
        int blocks = 0;
        for (int i = 0; i < Phase.values().length; i++) {
            blocks += Phase.values()[i].getDurationInBlocks();
        }
        return blocks;
    }

    //TODO
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
