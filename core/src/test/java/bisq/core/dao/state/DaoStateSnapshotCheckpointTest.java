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
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.monitoring.network.DaoStateNetworkService;
import bisq.core.dao.state.model.DaoState;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.storage.DaoStateStorageService;
import bisq.core.user.Preferences;

import bisq.network.p2p.seed.SeedNodeRepository;

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DaoStateSnapshotCheckpointTest {
    private final GenesisTxInfo genesis = mock(GenesisTxInfo.class);
    private final DaoStateService state = spy(new DaoStateService(new DaoState(), genesis, null));
    private final DaoStateStorageService storage = mock(DaoStateStorageService.class);
    private final DaoStateMonitoringService monitor = mock(DaoStateMonitoringService.class);
    private final Preferences preferences = mock(Preferences.class);
    private final DaoStateSnapshotService snapshots = snapshotService(monitor);

    @Test
    void failedStateCannotCreateGridSnapshotOrHash() {
        configureState(140);
        when(preferences.isUseFullModeDaoMonitor()).thenReturn(true);
        when(monitor.isCheckpointFailed()).thenReturn(true);

        snapshots.maybeCreateSnapshot(blockAt(140));
        snapshots.onDaoStateChanged(blockAt(140));

        verify(state, never()).getBsqStateCloneExcludingBlocks();
        verifyNoInteractions(storage);
        verify(monitor, never()).createHashFromBlock(any());
    }

    @Test
    void checkpointFailureDuringHashCreationPreventsSameBlockSnapshot() {
        configureState(572000);
        doReturn(new byte[]{1, 2, 3}).when(state).getSerializedStateForHashChain();
        when(preferences.isUseFullModeDaoMonitor()).thenReturn(true);
        DaoStateMonitoringService realMonitor = new DaoStateMonitoringService(state, storage,
                mock(DaoStateNetworkService.class), genesis, mock(SeedNodeRepository.class),
                preferences, null, false, false);
        realMonitor.applySnapshot(new LinkedList<>(List.of(new DaoStateHash(571999, new byte[20], false))));

        snapshotService(realMonitor).onDaoStateChanged(blockAt(572000));

        assertTrue(realMonitor.isCheckpointFailed());
        verify(state, never()).getBsqStateCloneExcludingBlocks();
        verify(storage, never()).requestPersistence(any(), anyList(), any(), any());
    }

    @Test
    void failedStateCannotRegisterPostSyncHandler() {
        when(monitor.isCheckpointFailed()).thenReturn(true);

        snapshots.onParseBlockChainComplete();

        verifyNoInteractions(storage);
        verify(monitor, never()).setCreateSnapshotHandler(any());
    }

    @Test
    void queuedPostSyncHandlerRechecksFailure() {
        snapshots.onParseBlockChainComplete();
        ArgumentCaptor<Runnable> handler = ArgumentCaptor.forClass(Runnable.class);
        verify(monitor).setCreateSnapshotHandler(handler.capture());
        when(monitor.isCheckpointFailed()).thenReturn(true);

        handler.getValue().run();

        verify(state, never()).getBsqStateCloneExcludingBlocks();
        verifyNoInteractions(storage);
    }

    @Test
    void earlierPersistenceCompletionCannotCaptureFailedState() {
        configureState(140);
        snapshots.maybeCreateSnapshot(blockAt(140));
        configureState(160);
        snapshots.maybeCreateSnapshot(blockAt(160));
        ArgumentCaptor<protobuf.DaoState> saved = ArgumentCaptor.forClass(protobuf.DaoState.class);
        ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
        verify(storage).requestPersistence(saved.capture(), anyList(), any(), completion.capture());
        assertEquals(140, saved.getValue().getChainHeight());
        when(monitor.isCheckpointFailed()).thenReturn(true);
        clearInvocations(state);

        completion.getValue().run();
        snapshots.maybeCreateSnapshot(blockAt(180));

        verify(state, never()).getBsqStateCloneExcludingBlocks();
        verify(storage, times(1)).requestPersistence(any(), anyList(), any(), any());
    }

    @Test
    void healthyPersistenceCompletionCapturesNextCandidate() {
        configureState(140);
        snapshots.maybeCreateSnapshot(blockAt(140));
        configureState(160);
        snapshots.maybeCreateSnapshot(blockAt(160));
        ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
        verify(storage).requestPersistence(any(), anyList(), any(), completion.capture());
        completion.getValue().run();
        configureState(180);

        snapshots.maybeCreateSnapshot(blockAt(180));

        ArgumentCaptor<protobuf.DaoState> saved = ArgumentCaptor.forClass(protobuf.DaoState.class);
        verify(storage, times(2)).requestPersistence(saved.capture(), anyList(), any(), any());
        assertEquals(List.of(140, 160), saved.getAllValues().stream()
                .map(protobuf.DaoState::getChainHeight).toList());
    }

    @Test
    void healthyPostSyncHandlerPersistsSnapshot() {
        configureState(140);
        snapshots.onParseBlockChainComplete();
        ArgumentCaptor<Runnable> handler = ArgumentCaptor.forClass(Runnable.class);
        verify(monitor).setCreateSnapshotHandler(handler.capture());

        handler.getValue().run();

        ArgumentCaptor<protobuf.DaoState> saved = ArgumentCaptor.forClass(protobuf.DaoState.class);
        verify(storage).requestPersistence(saved.capture(), anyList(), any(), any());
        assertEquals(140, saved.getValue().getChainHeight());
    }

    private DaoStateSnapshotService snapshotService(DaoStateMonitoringService monitoringService) {
        return new DaoStateSnapshotService(state, genesis, storage, monitoringService,
                null, null, preferences, null, true);
    }

    private void configureState(int height) {
        when(genesis.getGenesisBlockHeight()).thenReturn(100);
        doReturn(height).when(state).getChainHeight();
        doReturn(height).when(state).getBlockHeightOfLastBlock();
        doReturn(List.of(blockAt(height))).when(state).getBlocks();
        doReturn(List.of(blockAt(height))).when(state).getBlocksFromBlockHeight(anyInt());
        doReturn(protobuf.DaoState.newBuilder().setChainHeight(height).build())
                .when(state).getBsqStateCloneExcludingBlocks();
        when(monitor.getDaoStateHashChain()).thenReturn(new LinkedList<>());
        clearInvocations(monitor);
    }

    private static Block blockAt(int height) {
        return new Block(height, 0, "hash-" + height, "hash-" + (height - 1));
    }
}
