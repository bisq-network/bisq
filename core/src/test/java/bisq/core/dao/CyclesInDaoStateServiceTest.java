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

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Optional;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CyclesInDaoStateServiceTest {
    private static final int GENESIS_BLOCK_HEIGHT = 100;
    private static final int CYCLE_DURATION = 10;
    // The first cycle starts at the genesis block, as CycleService.addFirstCycle sets it up.
    private static final List<Cycle> CYCLES = List.of(cycle(GENESIS_BLOCK_HEIGHT),
            cycle(GENESIS_BLOCK_HEIGHT + CYCLE_DURATION),
            cycle(GENESIS_BLOCK_HEIGHT + 2 * CYCLE_DURATION));

    @Mock
    private DaoStateService daoStateService;
    @Mock
    private CycleService cycleService;

    private CyclesInDaoStateService cyclesInDaoStateService;

    @BeforeEach
    void setUp() {
        lenient().when(daoStateService.getGenesisBlockHeight()).thenReturn(GENESIS_BLOCK_HEIGHT);
        lenient().when(daoStateService.getCycle(anyInt())).thenAnswer(invocation -> {
            int height = invocation.getArgument(0);
            return CYCLES.stream().filter(cycle -> cycle.isInCycle(height)).findAny();
        });
        lenient().when(daoStateService.getCycleAtIndex(anyInt()))
                .thenAnswer(invocation -> CYCLES.get(invocation.<Integer>getArgument(0)));
        lenient().when(cycleService.getCycleIndex(any()))
                .thenAnswer(invocation -> CYCLES.indexOf(invocation.<Cycle>getArgument(0)));
        cyclesInDaoStateService = new CyclesInDaoStateService(daoStateService, cycleService);
    }

    @Test
    void pastCycleAtIndexZeroIsTheFirstCycle() {
        int heightInThirdCycle = CYCLES.get(2).getHeightOfFirstBlock() + 3;

        int height = cyclesInDaoStateService.getHeightOfFirstBlockOfPastCycle(heightInThirdCycle, 2);

        assertEquals(CYCLES.get(0).getHeightOfFirstBlock(), height);
        // Index 0 is a cycle like any other and is not replaced by the fallback for a missing cycle.
        verify(daoStateService).getCycleAtIndex(0);
    }

    @Test
    void pastCycleAtPositiveIndex() {
        int heightInThirdCycle = CYCLES.get(2).getHeightOfFirstBlock() + 3;

        assertEquals(CYCLES.get(1).getHeightOfFirstBlock(),
                cyclesInDaoStateService.getHeightOfFirstBlockOfPastCycle(heightInThirdCycle, 1));
        assertEquals(CYCLES.get(2).getHeightOfFirstBlock(),
                cyclesInDaoStateService.getHeightOfFirstBlockOfPastCycle(heightInThirdCycle, 0));
    }

    @Test
    void pastCycleBeforeTheFirstCycleFallsBackToGenesisBlockHeight() {
        int heightInSecondCycle = CYCLES.get(1).getHeightOfFirstBlock() + 3;

        int height = cyclesInDaoStateService.getHeightOfFirstBlockOfPastCycle(heightInSecondCycle, 2);

        assertEquals(GENESIS_BLOCK_HEIGHT, height);
        verify(daoStateService, never()).getCycleAtIndex(anyInt());
    }

    @Test
    void heightWithoutCycleFallsBackToGenesisBlockHeight() {
        int heightBeforeGenesis = GENESIS_BLOCK_HEIGHT - 1;

        assertEquals(GENESIS_BLOCK_HEIGHT,
                cyclesInDaoStateService.getHeightOfFirstBlockOfPastCycle(heightBeforeGenesis, 0));
        assertEquals(-1, cyclesInDaoStateService.getCycleIndexAtChainHeight(heightBeforeGenesis));
    }

    @Test
    void chainHeightOfPastCycleKeepsTheOffsetExceptInTheFirstCycle() {
        int offset = 3;
        int heightInThirdCycle = CYCLES.get(2).getHeightOfFirstBlock() + offset;

        assertEquals(CYCLES.get(1).getHeightOfFirstBlock() + offset,
                cyclesInDaoStateService.getChainHeightOfPastCycle(heightInThirdCycle, 1));
        // A height in the first cycle is reported as the genesis block height, without the offset.
        assertEquals(GENESIS_BLOCK_HEIGHT,
                cyclesInDaoStateService.getChainHeightOfPastCycle(heightInThirdCycle, 2));
    }

    private static Cycle cycle(int heightOfFirstBlock) {
        return new Cycle(heightOfFirstBlock,
                ImmutableList.of(new DaoPhase(DaoPhase.Phase.PROPOSAL, CYCLE_DURATION)));
    }
}
