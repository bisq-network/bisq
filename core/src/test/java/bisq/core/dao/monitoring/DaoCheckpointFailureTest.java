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
import bisq.core.dao.state.model.DaoState;

import java.io.IOException;

import java.util.LinkedList;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DaoCheckpointFailureTest {
    private final DaoCheckpointTestFixture fixture = new DaoCheckpointTestFixture();

    @Test
    void checkpointFailureIsRecordedOnlyOnce() throws IOException {
        assertFalse(fixture.monitor.isCheckpointFailed());
        assertDoesNotThrow(fixture.monitor::assertCheckpointNotFailed);

        fixture.failCheckpoint();
        fixture.failCheckpoint();

        assertTrue(fixture.monitor.isCheckpointFailed());
        assertThrows(IllegalStateException.class, fixture.monitor::assertCheckpointNotFailed);
        verify(fixture.storage, times(1)).removeAndBackupAllDaoData();
    }

    @Test
    void snapshotAndSynchronizationDoNotResetFailure() {
        fixture.failCheckpoint();

        fixture.daoStateService.applySnapshot(new DaoState());
        fixture.monitor.applySnapshot(new LinkedList<>());
        fixture.daoStateService.onParseBlockChainComplete();

        assertTrue(fixture.monitor.isCheckpointFailed());
        assertThrows(IllegalStateException.class, fixture.monitor::assertCheckpointNotFailed);
    }

    @Test
    void checkpointFailureDoesNotChangeConsensusSerialization() {
        byte[] before = fixture.daoStateService.getSerializedStateForHashChain();

        fixture.failCheckpoint();

        assertArrayEquals(before, fixture.daoStateService.getSerializedStateForHashChain());
    }
}
