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

package bisq.core.dao;

import bisq.core.dao.governance.period.CycleService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.governance.Cycle;
import bisq.core.dao.state.model.governance.DaoPhase;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility methods for Cycle related methods.
 * As they might be called often we use caching.
 */
@Slf4j
@Singleton
public class CyclesInDaoStateService {
    private final DaoStateService daoStateService;
    private final CycleService cycleService;

    // Cached results
    private final Map<Integer, Cycle> cyclesByHeight = new HashMap<>();
    private final Map<Cycle, Integer> indexByCycle = new HashMap<>();
    private final Map<Integer, Cycle> cyclesByIndex = new HashMap<>();

    @Inject
    public CyclesInDaoStateService(DaoStateService daoStateService, CycleService cycleService) {
        this.daoStateService = daoStateService;
        this.cycleService = cycleService;
    }

    public int getCycleIndexAtChainHeight(int chainHeight) {
        return findCycleAtHeight(chainHeight)
                .map(cycleService::getCycleIndex)
                .orElse(-1);
    }

    public int getHeightOfFirstBlockOfResultPhaseOfPastCycle(int chainHeight, int numPastCycles) {
        return findCycleAtHeight(chainHeight)
                .map(cycle -> {
                    int cycleIndex = getIndexForCycle(cycle);
                    int targetIndex = Math.max(0, (cycleIndex - numPastCycles));
                    return getCycleAtIndex(targetIndex);
                })
                .map(cycle -> cycle.getFirstBlockOfPhase(DaoPhase.Phase.RESULT))
                .orElse(daoStateService.getGenesisBlockHeight());
    }

    /**
     *
     * @param chainHeight       Chain height from where we start
     * @param numPastCycles     Number of past cycles
     * @return The height at the same offset from the first block of the cycle as in the current cycle minus the past cycles.
     */
    public int getChainHeightOfPastCycle(int chainHeight, int numPastCycles) {
        int firstBlockOfPastCycle = getHeightOfFirstBlockOfPastCycle(chainHeight, numPastCycles);
        if (firstBlockOfPastCycle == daoStateService.getGenesisBlockHeight()) {
            return firstBlockOfPastCycle;
        }
        return firstBlockOfPastCycle + getOffsetFromFirstBlockInCycle(chainHeight);
    }

    public Integer getOffsetFromFirstBlockInCycle(int chainHeight) {
        return daoStateService.getCycle(chainHeight)
                .map(c -> chainHeight - c.getHeightOfFirstBlock())
                .orElse(0);
    }

    public int getHeightOfFirstBlockOfPastCycle(int chainHeight, int numPastCycles) {
        return findCycleAtHeight(chainHeight)
                .map(cycle -> getIndexForCycle(cycle) - numPastCycles)
                .filter(targetIndex -> targetIndex > 0)
                .map(targetIndex -> getCycleAtIndex(targetIndex).getHeightOfFirstBlock())
                .orElse(daoStateService.getGenesisBlockHeight());
    }

    public Cycle getCycleAtIndex(int index) {
        int cycleIndex = Math.max(0, index);
        return Optional.ofNullable(cyclesByIndex.get(cycleIndex))
                .orElseGet(() -> {
                    Cycle cycle = daoStateService.getCycleAtIndex(cycleIndex);
                    cyclesByIndex.put(cycleIndex, cycle);
                    return cycle;
                });
    }

    public int getIndexForCycle(Cycle cycle) {
        return Optional.ofNullable(indexByCycle.get(cycle))
                .orElseGet(() -> {
                    int index = cycleService.getCycleIndex(cycle);
                    indexByCycle.put(cycle, index);
                    return index;
                });
    }

    public Optional<Cycle> findCycleAtHeight(int chainHeight) {
        return Optional.ofNullable(cyclesByHeight.get(chainHeight))
                .or(() -> {
                    Optional<Cycle> optionalCycle = daoStateService.getCycle(chainHeight);
                    optionalCycle.ifPresent(cycle -> cyclesByHeight.put(chainHeight, cycle));
                    return optionalCycle;
                });
    }
}
