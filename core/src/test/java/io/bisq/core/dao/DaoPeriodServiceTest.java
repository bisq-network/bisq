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

import static org.junit.Assert.assertEquals;

public class DaoPeriodServiceTest {

    @Test
    public void calculatePhaseTest() {
        DaoPeriodService service = new DaoPeriodService(null, null, null);

        /*      UNDEFINED(0),
                OPEN_FOR_COMPENSATION_REQUESTS(144 * 23), // 3312
                BREAK1(10), 3322
                OPEN_FOR_VOTING(144 * 4), // 3322 + 576 = 3898
                BREAK2(10), 3908
                VOTE_CONFIRMATION(144 * 3), // 3908 + 432 = 4340
                BREAK3(10); 4350
        */
        assertEquals(DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS, service.calculatePhase(service.getRelativeBlocksInCycle(0, 0)));
        assertEquals(DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS, service.calculatePhase(service.getRelativeBlocksInCycle(0, 3311)));
        assertEquals(DaoPeriodService.Phase.BREAK1, service.calculatePhase(service.getRelativeBlocksInCycle(0, 3312)));
        assertEquals(DaoPeriodService.Phase.BREAK1, service.calculatePhase(service.getRelativeBlocksInCycle(0, 3321)));
        assertEquals(DaoPeriodService.Phase.OPEN_FOR_VOTING, service.calculatePhase(service.getRelativeBlocksInCycle(0, 3322)));
        assertEquals(DaoPeriodService.Phase.OPEN_FOR_VOTING, service.calculatePhase(service.getRelativeBlocksInCycle(0, 3897)));
        assertEquals(DaoPeriodService.Phase.BREAK2, service.calculatePhase(service.getRelativeBlocksInCycle(0, 3898)));
        assertEquals(DaoPeriodService.Phase.BREAK2, service.calculatePhase(service.getRelativeBlocksInCycle(0, 3907)));
        assertEquals(DaoPeriodService.Phase.VOTE_CONFIRMATION, service.calculatePhase(service.getRelativeBlocksInCycle(0, 3908)));
        assertEquals(DaoPeriodService.Phase.VOTE_CONFIRMATION, service.calculatePhase(service.getRelativeBlocksInCycle(0, 4339)));
        assertEquals(DaoPeriodService.Phase.BREAK3, service.calculatePhase(service.getRelativeBlocksInCycle(0, 4340)));
        assertEquals(DaoPeriodService.Phase.BREAK3, service.calculatePhase(service.getRelativeBlocksInCycle(0, 4349)));
        assertEquals(DaoPeriodService.Phase.OPEN_FOR_COMPENSATION_REQUESTS, service.calculatePhase(service.getRelativeBlocksInCycle(0, 4350)));
    }
}
