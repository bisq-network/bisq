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

import org.junit.Test;

import static org.junit.Assert.*;

public class DaoPeriodServiceTest {

    @Test
    public void calculatePhaseTest() {
        DaoPeriodService service = new DaoPeriodService(null, null, null, null);

        /*      UNDEFINED(0),
              phase1  OPEN_FOR_COMPENSATION_REQUESTS(144 * 23), // 3312
              phase2  BREAK1(10), 3322
              phase3  OPEN_FOR_VOTING(144 * 4), // 3322 + 576 = 3898
              phase4  BREAK2(10), 3908
              phase5  VOTE_CONFIRMATION(144 * 3), // 3908 + 432 = 4340
              phase6  BREAK3(10); 4350
        */

        int phase1 = DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS.getBlocks();
        int phase2 = phase1 + DaoPeriodService.Phase.BREAK1.getBlocks();
        int phase3 = phase2 + DaoPeriodService.Phase.OPEN_FOR_VOTING.getBlocks();
        int phase4 = phase3 + DaoPeriodService.Phase.BREAK2.getBlocks();
        int phase5 = phase4 + DaoPeriodService.Phase.VOTE_CONFIRMATION.getBlocks();
        int phase6 = phase5 + DaoPeriodService.Phase.BREAK3.getBlocks();

        assertEquals(DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS, service.calculatePhase(service.getRelativeBlocksInCycle(0, 0)));
        assertEquals(DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase1 - 1)));
        assertEquals(DaoPeriodService.Phase.BREAK1, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase1)));
        assertEquals(DaoPeriodService.Phase.BREAK1, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase2 - 1)));
        assertEquals(DaoPeriodService.Phase.OPEN_FOR_VOTING, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase2)));
        assertEquals(DaoPeriodService.Phase.OPEN_FOR_VOTING, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase3 - 1)));
        assertEquals(DaoPeriodService.Phase.BREAK2, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase3)));
        assertEquals(DaoPeriodService.Phase.BREAK2, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase4 - 1)));
        assertEquals(DaoPeriodService.Phase.VOTE_CONFIRMATION, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase4)));
        assertEquals(DaoPeriodService.Phase.VOTE_CONFIRMATION, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase5 - 1)));
        assertEquals(DaoPeriodService.Phase.BREAK3, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase5)));
        assertEquals(DaoPeriodService.Phase.BREAK3, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase6 - 1)));
        assertEquals(DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS, service.calculatePhase(service.getRelativeBlocksInCycle(0, phase6)));
    }

    @Test
    public void isInCompensationRequestPhaseTest() {
        DaoPeriodService service = new DaoPeriodService(null, null, null, null);
        // int height, int chainHeight, int genesisHeight, int requestPhaseInBlocks, int totalPeriodInBlocks
        assertFalse(service.isInCompensationRequestPhase(1, 0, 0, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(0, 0, 0, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(0, 1, 0, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1, 1, 0, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1, 99, 0, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1, 100, 0, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(99, 100, 0, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(100, 100, 0, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1, 101, 0, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(101, 100, 0, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(101, 101, 0, 100, 300));

        assertFalse(service.isInCompensationRequestPhase(0, 0, 10, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1, 0, 10, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(0, 1, 10, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(9, 10, 10, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(10, 9, 10, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(10, 10, 10, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(10, 109, 10, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(10, 110, 10, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(109, 110, 10, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(110, 110, 10, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(10, 111, 10, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(111, 110, 10, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(111, 111, 10, 100, 300));

        assertFalse(service.isInCompensationRequestPhase(0, 0, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1000, 0, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(0, 1000, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(999, 10, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1000, 999, 1000, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1000, 1000, 1000, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1000, 1099, 1000, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1000, 1100, 1000, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1099, 1100, 1000, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1100, 1100, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1000, 1101, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1101, 1100, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1100, 1101, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1101, 1101, 1000, 100, 300));

        assertFalse(service.isInCompensationRequestPhase(0, 0, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1300, 0, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(0, 1300, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1299, 10, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1300, 1299, 1000, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1300, 1300, 1000, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1300, 1399, 1000, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1300, 1400, 1000, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1399, 1400, 1000, 100, 300));
        assertTrue(service.isInCompensationRequestPhase(1400, 1400, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1300, 1401, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1401, 1400, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1400, 1401, 1000, 100, 300));
        assertFalse(service.isInCompensationRequestPhase(1401, 1401, 1000, 100, 300));
    }

    @Test
    public void getNumCyclesTest() {
        DaoPeriodService service = new DaoPeriodService(null, null, null, null);
        // int chainHeight, int genesisHeight, int totalPeriodInBlocks
        assertEquals(0, service.getNumCycles(9, 10, 5));
        assertEquals(1, service.getNumCycles(10, 10, 5));
        assertEquals(1, service.getNumCycles(11, 10, 5));
        assertEquals(1, service.getNumCycles(14, 10, 5));
        assertEquals(2, service.getNumCycles(15, 10, 5));
        assertEquals(2, service.getNumCycles(16, 10, 5));
        assertEquals(2, service.getNumCycles(19, 10, 5));
        assertEquals(3, service.getNumCycles(20, 10, 5));
    }

    @Test
    public void getCompensationRequestStartBlockTest() {
        DaoPeriodService service = new DaoPeriodService(null, null, null, null);
        // int chainHeight, int genesisHeight, int totalPeriodInBlocks
        assertEquals(10, service.getAbsoluteStartBlockOfPhase(9, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(10, service.getAbsoluteStartBlockOfPhase(10, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(10, service.getAbsoluteStartBlockOfPhase(11, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(10, service.getAbsoluteStartBlockOfPhase(14, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(15, service.getAbsoluteStartBlockOfPhase(15, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(15, service.getAbsoluteStartBlockOfPhase(16, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(15, service.getAbsoluteStartBlockOfPhase(19, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(20, service.getAbsoluteStartBlockOfPhase(20, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
    }

    @Test
    public void getCompensationRequestEndBlockTest() {
        DaoPeriodService service = new DaoPeriodService(null, null, null, null);
        // int chainHeight, int genesisHeight, int requestPhaseInBlocks, int totalPeriodInBlocks
        assertEquals(12, service.getAbsoluteEndBlockOfPhase(9, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(12, service.getAbsoluteEndBlockOfPhase(10, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(12, service.getAbsoluteEndBlockOfPhase(11, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(12, service.getAbsoluteEndBlockOfPhase(14, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(17, service.getAbsoluteEndBlockOfPhase(15, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(17, service.getAbsoluteEndBlockOfPhase(16, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(17, service.getAbsoluteEndBlockOfPhase(19, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(22, service.getAbsoluteEndBlockOfPhase(20, 10, 5, DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
    }

    @Test
    public void getStartBlockOfPhaseTest() {
        DaoPeriodService service = new DaoPeriodService(null, null, null, null);
        assertEquals(0, service.getStartBlockOfPhase(DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS));
        assertEquals(DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS.getBlocks(),
                service.getStartBlockOfPhase(DaoPeriodService.Phase.BREAK1));
    }
}
