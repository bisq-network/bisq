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

package bisq.core.dao.state;

import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.state.storage.DaoStateStorageService;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class DaoStateSnapshotServiceTest {

    private DaoStateSnapshotService daoStateSnapshotService;

    @Before
    public void setup() {
        daoStateSnapshotService = new DaoStateSnapshotService(mock(DaoStateService.class),
                mock(GenesisTxInfo.class),
                mock(DaoStateStorageService.class),
                mock(DaoStateMonitoringService.class),
                null,
                null,
                null,
                null,
                null);
    }

    @Test
    public void testGetSnapshotHeight() {
        assertEquals(120, daoStateSnapshotService.getSnapshotHeight(102, 0, 10));
        assertEquals(120, daoStateSnapshotService.getSnapshotHeight(102, 100, 10));
        assertEquals(120, daoStateSnapshotService.getSnapshotHeight(102, 102, 10));
        assertEquals(120, daoStateSnapshotService.getSnapshotHeight(102, 119, 10));
        assertEquals(120, daoStateSnapshotService.getSnapshotHeight(102, 120, 10));
        assertEquals(120, daoStateSnapshotService.getSnapshotHeight(102, 121, 10));
        assertEquals(120, daoStateSnapshotService.getSnapshotHeight(102, 130, 10));
        assertEquals(120, daoStateSnapshotService.getSnapshotHeight(102, 139, 10));
        assertEquals(130, daoStateSnapshotService.getSnapshotHeight(102, 140, 10));
        assertEquals(130, daoStateSnapshotService.getSnapshotHeight(102, 141, 10));
        assertEquals(990, daoStateSnapshotService.getSnapshotHeight(102, 1000, 10));
    }

    @Test
    public void testSnapshotHeight() {
        assertFalse(daoStateSnapshotService.isSnapshotHeight(102, 0, 10));
        assertFalse(daoStateSnapshotService.isSnapshotHeight(102, 80, 10));
        assertFalse(daoStateSnapshotService.isSnapshotHeight(102, 90, 10));
        assertFalse(daoStateSnapshotService.isSnapshotHeight(102, 100, 10));
        assertFalse(daoStateSnapshotService.isSnapshotHeight(102, 119, 10));
        assertTrue(daoStateSnapshotService.isSnapshotHeight(102, 120, 10));
        assertTrue(daoStateSnapshotService.isSnapshotHeight(102, 130, 10));
        assertTrue(daoStateSnapshotService.isSnapshotHeight(102, 140, 10));
        assertTrue(daoStateSnapshotService.isSnapshotHeight(102, 200, 10));
        assertFalse(daoStateSnapshotService.isSnapshotHeight(102, 201, 10));
        assertFalse(daoStateSnapshotService.isSnapshotHeight(102, 199, 10));
    }
}
