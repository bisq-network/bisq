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

package bisq.core.dao.monitoring;

import bisq.core.dao.DaoCheckpointTestFixture;
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.state.model.DaoState;

import bisq.common.util.Utilities;

import java.io.IOException;

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DaoCheckpointReadinessTest {
    @Test
    void realCheckpointFailureRevokesReadinessBeforeCleanupAndNotification() throws IOException {
        var fixture = new DaoCheckpointTestFixture();
        assertTrue(fixture.facade.isDaoStateReadyAndInSync());
        doAnswer(invocation -> {
            assertBlocked(fixture);
            return null;
        }).when(fixture.storage).removeAndBackupAllDaoData();
        var listener = mock(DaoStateMonitoringService.Listener.class);
        doAnswer(invocation -> {
            assertBlocked(fixture);
            return null;
        }).when(listener).onCheckpointFailed();
        fixture.monitor.addListener(listener);

        fixture.failCheckpoint();

        assertBlocked(fixture);
        verify(listener).onCheckpointFailed();
        verify(fixture.storage).removeAndBackupAllDaoData();
    }

    @Test
    void cleanupFailureCannotRestoreReadinessOrRepeatRemediation() throws IOException {
        var fixture = new DaoCheckpointTestFixture();
        doAnswer(invocation -> {
            assertBlocked(fixture);
            throw new IOException("Simulated cleanup failure");
        }).when(fixture.storage).removeAndBackupAllDaoData();
        var listener = mock(DaoStateMonitoringService.Listener.class);
        fixture.monitor.addListener(listener);

        fixture.failCheckpoint();
        fixture.failCheckpoint();

        assertBlocked(fixture);
        verify(fixture.storage, times(1)).removeAndBackupAllDaoData();
        verify(listener, times(1)).onCheckpointFailed();
    }

    @Test
    void throwingListenerCannotRestoreReadiness() {
        var fixture = new DaoCheckpointTestFixture();
        fixture.monitor.addListener(new DaoStateMonitoringService.Listener() {
            @Override
            public void onCheckpointFailed() {
                assertBlocked(fixture);
                throw new IllegalStateException("Simulated listener failure");
            }
        });

        assertThrows(IllegalStateException.class, fixture::failCheckpoint);

        assertBlocked(fixture);
    }

    @Test
    void failureWithoutListenersSurvivesMatchingCheckpointSnapshotAndSync() {
        var fixture = new DaoCheckpointTestFixture();
        fixture.failCheckpoint();
        fixture.daoStateService.applySnapshot(new DaoState());
        fixture.monitor.applySnapshot(new LinkedList<>(List.of(new DaoStateHash(
                DaoCheckpointTestFixture.CHECKPOINT_HEIGHT,
                Utilities.decodeFromHex("202b0cf57ae9fa3db3507f006617213358ebabe7"), true))));
        fixture.daoStateService.onParseBlockChainComplete();

        assertBlocked(fixture);
    }

    private static void assertBlocked(DaoCheckpointTestFixture fixture) {
        assertFalse(fixture.facade.isDaoStateReadyAndInSync());
        assertTrue(fixture.monitor.isCheckpointFailed());
        assertThrows(IllegalStateException.class, fixture.monitor::assertCheckpointNotFailed);
    }
}
