/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class DaoPeriodServiceTest {

    private DaoPeriodService service;

    @Before
    public void startup() {
        service = new DaoPeriodService(null, null, null, null);
    }

    @Test
    public void calculatePhaseTest() {
        /*      UNDEFINED(0),
              phase1  COMPENSATION_REQUESTS(144 * 23), // 3312
              phase2  BREAK1(10), 3322
              phase3  OPEN_FOR_VOTING(144 * 4), // 3322 + 576 = 3898
              phase4  BREAK2(10), 3908
              phase5  VOTE_CONFIRMATION(144 * 3), // 3908 + 432 = 4340
              phase6  BREAK3(10); 4350
        */
        int totalPhaseBlocks = 20;

        int phase1 = DaoPeriodService.Phase.COMPENSATION_REQUESTS.getDurationInBlocks();
        int phase2 = phase1 + DaoPeriodService.Phase.BREAK1.getDurationInBlocks();
        int phase3 = phase2 + DaoPeriodService.Phase.OPEN_FOR_VOTING.getDurationInBlocks();
        int phase4 = phase3 + DaoPeriodService.Phase.BREAK2.getDurationInBlocks();
        int phase5 = phase4 + DaoPeriodService.Phase.VOTE_CONFIRMATION.getDurationInBlocks();
        int phase6 = phase5 + DaoPeriodService.Phase.BREAK3.getDurationInBlocks();

        assertEquals(DaoPeriodService.Phase.COMPENSATION_REQUESTS, service.calculatePhase(service.getRelativeBlocksInCycle(0, 0, totalPhaseBlocks)));
        assertEquals(DaoPeriodService.Phase.COMPENSATION_REQUESTS, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase1 - 1, totalPhaseBlocks)));
        assertEquals(DaoPeriodService.Phase.BREAK1, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase1, totalPhaseBlocks)));
        assertEquals(DaoPeriodService.Phase.BREAK1, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase2 - 1, totalPhaseBlocks)));
        assertEquals(DaoPeriodService.Phase.OPEN_FOR_VOTING, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase2, totalPhaseBlocks)));
        assertEquals(DaoPeriodService.Phase.OPEN_FOR_VOTING, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase3 - 1, totalPhaseBlocks)));
        assertEquals(DaoPeriodService.Phase.BREAK2, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase3, totalPhaseBlocks)));
        assertEquals(DaoPeriodService.Phase.BREAK2, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase4 - 1, totalPhaseBlocks)));
        assertEquals(DaoPeriodService.Phase.VOTE_CONFIRMATION, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase4, totalPhaseBlocks)));
        assertEquals(DaoPeriodService.Phase.VOTE_CONFIRMATION, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase5 - 1, totalPhaseBlocks)));
        assertEquals(DaoPeriodService.Phase.BREAK3, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase5, totalPhaseBlocks)));
        assertEquals(DaoPeriodService.Phase.BREAK3, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase6 - 1, totalPhaseBlocks)));
        assertEquals(DaoPeriodService.Phase.COMPENSATION_REQUESTS, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase6, totalPhaseBlocks)));
    }

    @Test
    public void isTxHeightInPhaseTest() {
        // int height, int chainHeight, int genesisHeight, int numBlocksOfCycle, int totalPeriodInBlocks
        assertFalse(service.isTxHeightInPhase(1, 0, 0, 100, 300));
        assertTrue(service.isTxHeightInPhase(0, 0, 0, 100, 300));
        assertTrue(service.isTxHeightInPhase(0, 1, 0, 100, 300));
        assertTrue(service.isTxHeightInPhase(1, 1, 0, 100, 300));
        assertTrue(service.isTxHeightInPhase(1, 99, 0, 100, 300));
        assertTrue(service.isTxHeightInPhase(1, 100, 0, 100, 300));
        assertTrue(service.isTxHeightInPhase(99, 100, 0, 100, 300));
        assertTrue(service.isTxHeightInPhase(100, 100, 0, 100, 300));
        assertFalse(service.isTxHeightInPhase(1, 101, 0, 100, 300));
        assertFalse(service.isTxHeightInPhase(101, 100, 0, 100, 300));
        assertFalse(service.isTxHeightInPhase(101, 101, 0, 100, 300));

        assertFalse(service.isTxHeightInPhase(0, 0, 10, 100, 300));
        assertFalse(service.isTxHeightInPhase(1, 0, 10, 100, 300));
        assertFalse(service.isTxHeightInPhase(0, 1, 10, 100, 300));
        assertFalse(service.isTxHeightInPhase(9, 10, 10, 100, 300));
        assertFalse(service.isTxHeightInPhase(10, 9, 10, 100, 300));
        assertTrue(service.isTxHeightInPhase(10, 10, 10, 100, 300));
        assertTrue(service.isTxHeightInPhase(10, 109, 10, 100, 300));
        assertTrue(service.isTxHeightInPhase(10, 110, 10, 100, 300));
        assertTrue(service.isTxHeightInPhase(109, 110, 10, 100, 300));
        assertTrue(service.isTxHeightInPhase(110, 110, 10, 100, 300));
        assertFalse(service.isTxHeightInPhase(10, 111, 10, 100, 300));
        assertFalse(service.isTxHeightInPhase(111, 110, 10, 100, 300));
        assertFalse(service.isTxHeightInPhase(111, 111, 10, 100, 300));

        assertFalse(service.isTxHeightInPhase(0, 0, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1000, 0, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(0, 1000, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(999, 10, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1000, 999, 1000, 100, 300));
        assertTrue(service.isTxHeightInPhase(1000, 1000, 1000, 100, 300));
        assertTrue(service.isTxHeightInPhase(1000, 1099, 1000, 100, 300));
        assertTrue(service.isTxHeightInPhase(1000, 1100, 1000, 100, 300));
        assertTrue(service.isTxHeightInPhase(1099, 1100, 1000, 100, 300));
        assertTrue(service.isTxHeightInPhase(1100, 1100, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1000, 1101, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1101, 1100, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1100, 1101, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1101, 1101, 1000, 100, 300));

        assertFalse(service.isTxHeightInPhase(0, 0, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1300, 0, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(0, 1300, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1299, 10, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1300, 1299, 1000, 100, 300));
        assertTrue(service.isTxHeightInPhase(1300, 1300, 1000, 100, 300));
        assertTrue(service.isTxHeightInPhase(1300, 1399, 1000, 100, 300));
        assertTrue(service.isTxHeightInPhase(1300, 1400, 1000, 100, 300));
        assertTrue(service.isTxHeightInPhase(1399, 1400, 1000, 100, 300));
        assertTrue(service.isTxHeightInPhase(1400, 1400, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1300, 1401, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1401, 1400, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1400, 1401, 1000, 100, 300));
        assertFalse(service.isTxHeightInPhase(1401, 1401, 1000, 100, 300));
    }

    @Test
    public void getNumOfStartedCyclesTest() {
        // int chainHeight, int genesisHeight, int numBlocksOfCycle
        int numBlocksOfCycle = 20;
        int genesisHeight = 1;
        assertEquals(0, service.getNumOfStartedCycles(genesisHeight - 1, genesisHeight, numBlocksOfCycle));
        assertEquals(1, service.getNumOfStartedCycles(genesisHeight, genesisHeight, numBlocksOfCycle));
        assertEquals(1, service.getNumOfStartedCycles(genesisHeight + 1, genesisHeight, numBlocksOfCycle));
        assertEquals(1, service.getNumOfStartedCycles(genesisHeight + numBlocksOfCycle - 1, genesisHeight, numBlocksOfCycle));
        assertEquals(2, service.getNumOfStartedCycles(genesisHeight + numBlocksOfCycle, genesisHeight, numBlocksOfCycle));
        assertEquals(2, service.getNumOfStartedCycles(genesisHeight + numBlocksOfCycle + 1, genesisHeight, numBlocksOfCycle));
        assertEquals(2, service.getNumOfStartedCycles(genesisHeight + numBlocksOfCycle + numBlocksOfCycle - 1, genesisHeight, numBlocksOfCycle));
        assertEquals(3, service.getNumOfStartedCycles(genesisHeight + numBlocksOfCycle + numBlocksOfCycle, genesisHeight, numBlocksOfCycle));
    }

    @Test
    public void getNumOfCompletedCyclesTest() {
        // int chainHeight, int genesisHeight, int totalPeriodInBlocks
        int numBlocksOfCycle = 20;
        int genesisHeight = 1;
        assertEquals(0, service.getNumOfCompletedCycles(genesisHeight - 1, genesisHeight, numBlocksOfCycle));
        assertEquals(0, service.getNumOfCompletedCycles(genesisHeight, genesisHeight, numBlocksOfCycle));
        assertEquals(0, service.getNumOfCompletedCycles(genesisHeight + 1, genesisHeight, numBlocksOfCycle));
        assertEquals(0, service.getNumOfCompletedCycles(genesisHeight + numBlocksOfCycle - 1, genesisHeight, numBlocksOfCycle));
        assertEquals(1, service.getNumOfCompletedCycles(genesisHeight + numBlocksOfCycle, genesisHeight, numBlocksOfCycle));
        assertEquals(1, service.getNumOfCompletedCycles(genesisHeight + numBlocksOfCycle + 1, genesisHeight, numBlocksOfCycle));
        assertEquals(1, service.getNumOfCompletedCycles(genesisHeight + numBlocksOfCycle + numBlocksOfCycle - 1, genesisHeight, numBlocksOfCycle));
        assertEquals(2, service.getNumOfCompletedCycles(genesisHeight + numBlocksOfCycle + numBlocksOfCycle, genesisHeight, numBlocksOfCycle));
    }

    @Test
    public void getCompensationRequestStartBlockTest() {
        // int chainHeight, int genesisHeight, int totalPeriodInBlocks
        int numBlocksOfCycle = 20;
        int gen = 1;
        final int first = gen; // 1
        final int second = first + numBlocksOfCycle; //
        final int third = first + numBlocksOfCycle + numBlocksOfCycle; //
        assertEquals(gen, service.getAbsoluteStartBlockOfPhase(0, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(gen, service.getAbsoluteStartBlockOfPhase(gen, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(first, service.getAbsoluteStartBlockOfPhase(gen + 1, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(first, service.getAbsoluteStartBlockOfPhase(gen + numBlocksOfCycle - 1, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(second, service.getAbsoluteStartBlockOfPhase(gen + numBlocksOfCycle, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(second, service.getAbsoluteStartBlockOfPhase(gen + numBlocksOfCycle + 1, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(second, service.getAbsoluteStartBlockOfPhase(gen + numBlocksOfCycle + numBlocksOfCycle - 1, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(third, service.getAbsoluteStartBlockOfPhase(gen + numBlocksOfCycle + numBlocksOfCycle, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
    }

    @Test
    public void getCompensationRequestEndBlockTest() {
        // int chainHeight, int genesisHeight, int numBlocksOfCycle, int totalPeriodInBlocks
        int blocks = DaoPeriodService.Phase.COMPENSATION_REQUESTS.getDurationInBlocks(); //10
        int numBlocksOfCycle = 20;
        int gen = 1;
        final int first = gen + blocks - 1; //10
        final int second = first + numBlocksOfCycle; // 30
        final int third = first + numBlocksOfCycle + numBlocksOfCycle; //40
        assertEquals(first, service.getAbsoluteEndBlockOfPhase(0, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS, numBlocksOfCycle));
        assertEquals(first, service.getAbsoluteEndBlockOfPhase(gen, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(first, service.getAbsoluteEndBlockOfPhase(gen + 1, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(first, service.getAbsoluteEndBlockOfPhase(gen + numBlocksOfCycle - 1, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(second, service.getAbsoluteEndBlockOfPhase(gen + numBlocksOfCycle, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(second, service.getAbsoluteEndBlockOfPhase(gen + numBlocksOfCycle + 1, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(second, service.getAbsoluteEndBlockOfPhase(gen + numBlocksOfCycle + numBlocksOfCycle - 1, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
        assertEquals(third, service.getAbsoluteEndBlockOfPhase(gen + numBlocksOfCycle + numBlocksOfCycle, gen, DaoPeriodService.Phase.COMPENSATION_REQUESTS,numBlocksOfCycle));
    }

    @Test
    public void getStartBlockOfPhaseTest() {
        assertEquals(0, service.getNumBlocksOfPhaseStart(DaoPeriodService.Phase.COMPENSATION_REQUESTS));
        assertEquals(DaoPeriodService.Phase.COMPENSATION_REQUESTS.getDurationInBlocks(),
                service.getNumBlocksOfPhaseStart(DaoPeriodService.Phase.BREAK1));
    }

    @Test
    public void isInCurrentCycleTest() {
        //int txHeight, int chainHeight, int genesisHeight, int numBlocksOfCycle
        int gen = 1;
        int numBlocksOfCycle = 20;
        assertFalse(service.isInCurrentCycle(gen, gen + numBlocksOfCycle, gen, numBlocksOfCycle));

        assertFalse(service.isInCurrentCycle(gen - 1, gen - 1, gen, numBlocksOfCycle));
        assertFalse(service.isInCurrentCycle(gen, gen - 1, gen, numBlocksOfCycle));
        assertFalse(service.isInCurrentCycle(gen - 1, gen, gen, numBlocksOfCycle));
        assertTrue(service.isInCurrentCycle(gen, gen, gen, numBlocksOfCycle));
        assertTrue(service.isInCurrentCycle(gen, gen + 1, gen, numBlocksOfCycle));
        assertTrue(service.isInCurrentCycle(gen, gen + numBlocksOfCycle - 1, gen, numBlocksOfCycle));

        assertTrue(service.isInCurrentCycle(gen, gen + numBlocksOfCycle - 1, gen, numBlocksOfCycle));
        assertTrue(service.isInCurrentCycle(gen + 1, gen + numBlocksOfCycle - 1, gen, numBlocksOfCycle));
        assertTrue(service.isInCurrentCycle(gen + numBlocksOfCycle - 1, gen + numBlocksOfCycle - 1, gen, numBlocksOfCycle));
        assertFalse(service.isInCurrentCycle(gen + numBlocksOfCycle, gen + numBlocksOfCycle - 1, gen, numBlocksOfCycle));
    }
}
