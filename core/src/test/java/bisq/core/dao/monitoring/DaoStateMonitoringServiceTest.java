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

import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.monitoring.network.DaoStateNetworkService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.GenesisTxInfo;
import bisq.core.dao.state.model.blockchain.Block;
import bisq.core.dao.state.storage.DaoStateStorageService;
import bisq.core.user.Preferences;

import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.crypto.Hash;
import bisq.common.util.Utilities;

import java.nio.file.Files;

import java.io.File;
import java.io.IOException;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

public class DaoStateMonitoringServiceTest {
    private static final int CHECKPOINT_HEIGHT = 586920;
    private static final String CHECKPOINT_HASH = "523aaad4e760f6ac6196fec1b3ec9a2f42e5b272";
    private static final String OTHER_HASH = "0000000000000000000000000000000000000000";

    private DaoStateMonitoringService service;
    private DaoStateStorageService daoStateStorageService;
    private DaoStateMonitoringService.Listener listener;

    @BeforeEach
    public void setup() {
        daoStateStorageService = mock(DaoStateStorageService.class);
        listener = mock(DaoStateMonitoringService.Listener.class);

        service = new DaoStateMonitoringService(
                mock(DaoStateService.class),
                daoStateStorageService,
                mock(DaoStateNetworkService.class),
                mock(GenesisTxInfo.class),
                mock(SeedNodeRepository.class),
                mock(Preferences.class),
                mock(File.class),
                false,
                false
        );
        service.addListener(listener);
    }

    @Test
    public void testLoadCheckpointsFromResource() {
        Map<Integer, String> checkpoints = DaoStateMonitoringService.loadCheckpoints();

        assertEquals(CHECKPOINT_HASH, checkpoints.get(CHECKPOINT_HEIGHT));
    }

    @Test
    public void testMaybeVerifyCheckpoint_noCheckpointEntryForBlockHeight() throws IOException {
        Map<Integer, String> checkpoints = new HashMap<>();
        LinkedList<DaoStateHash> hashChain = new LinkedList<>();
        hashChain.add(new DaoStateHash(CHECKPOINT_HEIGHT, Utilities.decodeFromHex(CHECKPOINT_HASH), true));

        service.maybeVerifyCheckpoint(CHECKPOINT_HEIGHT, checkpoints, hashChain);

        verify(daoStateStorageService, never()).removeAndBackupAllDaoData();
        verify(listener, never()).onCheckpointFailed();
    }

    @Test
    public void testMaybeVerifyCheckpoint_matchingCheckpoint() throws IOException {
        Map<Integer, String> checkpoints = new HashMap<>();
        checkpoints.put(CHECKPOINT_HEIGHT, CHECKPOINT_HASH);
        LinkedList<DaoStateHash> hashChain = new LinkedList<>();
        hashChain.add(new DaoStateHash(CHECKPOINT_HEIGHT, Utilities.decodeFromHex(CHECKPOINT_HASH), true));

        service.maybeVerifyCheckpoint(CHECKPOINT_HEIGHT, checkpoints, hashChain);

        verify(daoStateStorageService, never()).removeAndBackupAllDaoData();
        verify(listener, never()).onCheckpointFailed();
    }

    @Test
    public void testMaybeVerifyCheckpoint_mismatchingCheckpoint() throws IOException {
        Map<Integer, String> checkpoints = new HashMap<>();
        checkpoints.put(CHECKPOINT_HEIGHT, CHECKPOINT_HASH);
        LinkedList<DaoStateHash> hashChain = new LinkedList<>();
        hashChain.add(new DaoStateHash(CHECKPOINT_HEIGHT, Utilities.decodeFromHex(OTHER_HASH), true));

        service.maybeVerifyCheckpoint(CHECKPOINT_HEIGHT, checkpoints, hashChain);

        verify(daoStateStorageService, times(1)).removeAndBackupAllDaoData();
        verify(listener, times(1)).onCheckpointFailed();
    }

    @Test
    public void testMaybeVerifyCheckpoint_failureIgnoredAfterFirstMismatch() throws IOException {
        Map<Integer, String> checkpoints = new HashMap<>();
        checkpoints.put(CHECKPOINT_HEIGHT, CHECKPOINT_HASH);
        LinkedList<DaoStateHash> hashChain = new LinkedList<>();
        hashChain.add(new DaoStateHash(CHECKPOINT_HEIGHT, Utilities.decodeFromHex(OTHER_HASH), true));

        service.maybeVerifyCheckpoint(CHECKPOINT_HEIGHT, checkpoints, hashChain);
        service.maybeVerifyCheckpoint(CHECKPOINT_HEIGHT, checkpoints, hashChain);

        verify(daoStateStorageService, times(1)).removeAndBackupAllDaoData();
        verify(listener, times(1)).onCheckpointFailed();
    }

    @Test
    public void testMaybeVerifyCheckpoint_checkpointEntryButNoHashInChain() throws IOException {
        Map<Integer, String> checkpoints = new HashMap<>();
        checkpoints.put(CHECKPOINT_HEIGHT, CHECKPOINT_HASH);
        LinkedList<DaoStateHash> hashChain = new LinkedList<>();

        service.maybeVerifyCheckpoint(CHECKPOINT_HEIGHT, checkpoints, hashChain);

        verify(daoStateStorageService, never()).removeAndBackupAllDaoData();
        verify(listener, never()).onCheckpointFailed();
    }

    @Test
    public void testMaybeVerifyCheckpoint_peersHashIsNotVerified() throws IOException {
        // A hash taken over from a seed node or from resources reflects the peers' view, not the validity of our
        // local DAO state, so a mismatch must not trigger a resync.
        Map<Integer, String> checkpoints = new HashMap<>();
        checkpoints.put(CHECKPOINT_HEIGHT, CHECKPOINT_HASH);
        LinkedList<DaoStateHash> hashChain = new LinkedList<>();
        hashChain.add(new DaoStateHash(CHECKPOINT_HEIGHT, Utilities.decodeFromHex(OTHER_HASH), false));

        service.maybeVerifyCheckpoint(CHECKPOINT_HEIGHT, checkpoints, hashChain);

        verify(daoStateStorageService, never()).removeAndBackupAllDaoData();
        verify(listener, never()).onCheckpointFailed();
    }

    @Test
    public void testOnParseBlockChainComplete_verifiesCheckpointsFromRestoredHashChain() throws IOException {
        // Checkpoints are usually far below the snapshot height, so those blocks are never parsed again.
        // The hash chain restored via applySnapshot must still be verified.
        LinkedList<DaoStateHash> persisted = new LinkedList<>();
        persisted.add(new DaoStateHash(CHECKPOINT_HEIGHT, Utilities.decodeFromHex(OTHER_HASH), true));
        service.applySnapshot(persisted);

        service.onParseBlockChainComplete();

        verify(daoStateStorageService, times(1)).removeAndBackupAllDaoData();
        verify(listener, times(1)).onCheckpointFailed();
    }

    @Test
    public void testOnParseBlockChainComplete_passesCheckpointFromRestoredHashChain() throws IOException {
        LinkedList<DaoStateHash> persisted = new LinkedList<>();
        persisted.add(new DaoStateHash(CHECKPOINT_HEIGHT, Utilities.decodeFromHex(CHECKPOINT_HASH), true));
        service.applySnapshot(persisted);

        service.onParseBlockChainComplete();

        verify(daoStateStorageService, never()).removeAndBackupAllDaoData();
        verify(listener, never()).onCheckpointFailed();
    }

    @Test
    public void testLoadCheckpointsResourceHashesAreCanonicalHex() {
        // A malformed hash in the resource file would make every node fail the checkpoint and wipe its DAO
        // data, so loadCheckpoints rejects anything but 40 lower-case hex chars.
        DaoStateMonitoringService.loadCheckpoints().forEach((height, hash) -> {
            assertTrue(height > 0, "Block height must be positive but was " + height);
            assertTrue(hash.matches("[0-9a-f]{40}"),
                    "Checkpoint hash at height " + height + " is not 40 lower-case hex chars: " + hash);
        });
    }

    @Test
    public void testCreateHashFromBlock_dumpsCheckpointAtHeightDivisibleBy1000(@TempDir File tempDir) throws IOException {
        byte[] stateBytes = new byte[]{1, 2, 3};
        DaoStateMonitoringService service = createServiceForDump(1000, tempDir, true, stateBytes);

        service.createHashFromBlock(new Block(1000, 0L, "blockHash", "previousBlockHash"));

        File dumpFile = new File(tempDir, "dao_state_hash_checkpoints.txt");
        assertTrue(dumpFile.exists());
        String expectedHash = Utilities.encodeToHex(Hash.getSha256Ripemd160hash(stateBytes));
        assertEquals("1000," + expectedHash + System.lineSeparator(),
                Files.readString(dumpFile.toPath()));
    }

    @Test
    public void testCreateHashFromBlock_noDumpAtHeightNotDivisibleBy1000(@TempDir File tempDir) throws IOException {
        DaoStateMonitoringService service = createServiceForDump(1001, tempDir, true, new byte[]{1, 2, 3});

        service.createHashFromBlock(new Block(1001, 0L, "blockHash", "previousBlockHash"));

        assertFalse(new File(tempDir, "dao_state_hash_checkpoints.txt").exists());
    }

    @Test
    public void testCreateHashFromBlock_noDumpWhenOptionDisabled(@TempDir File tempDir) throws IOException {
        DaoStateMonitoringService service = createServiceForDump(1000, tempDir, false, new byte[]{1, 2, 3});

        service.createHashFromBlock(new Block(1000, 0L, "blockHash", "previousBlockHash"));

        assertFalse(new File(tempDir, "dao_state_hash_checkpoints.txt").exists());
    }

    @Test
    public void testCreateHashFromBlock_verifiesCheckpointOfCreatedBlock(@TempDir File tempDir) throws IOException {
        // The hash of {1, 2, 3} does not match the bundled checkpoint at CHECKPOINT_HEIGHT, so the
        // checkpoint verification must trigger a resync once the hash for the block has been created.
        DaoStateStorageService daoStateStorageService = mock(DaoStateStorageService.class);
        DaoStateMonitoringService service = createServiceForDump(CHECKPOINT_HEIGHT, tempDir, false,
                new byte[]{1, 2, 3}, daoStateStorageService);

        service.createHashFromBlock(new Block(CHECKPOINT_HEIGHT, 0L, "blockHash", "previousBlockHash"));

        verify(daoStateStorageService, times(1)).removeAndBackupAllDaoData();
    }

    private DaoStateMonitoringService createServiceForDump(int genesisBlockHeight,
                                                           File appDataDir,
                                                           boolean dumpDaoStateHashCheckpoints,
                                                           byte[] stateBytes) {
        return createServiceForDump(genesisBlockHeight, appDataDir, dumpDaoStateHashCheckpoints, stateBytes,
                mock(DaoStateStorageService.class));
    }

    private DaoStateMonitoringService createServiceForDump(int genesisBlockHeight,
                                                           File appDataDir,
                                                           boolean dumpDaoStateHashCheckpoints,
                                                           byte[] stateBytes,
                                                           DaoStateStorageService daoStateStorageService) {
        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getSerializedStateForHashChain()).thenReturn(stateBytes);
        GenesisTxInfo genesisTxInfo = mock(GenesisTxInfo.class);
        when(genesisTxInfo.getGenesisBlockHeight()).thenReturn(genesisBlockHeight);
        return new DaoStateMonitoringService(
                daoStateService,
                daoStateStorageService,
                mock(DaoStateNetworkService.class),
                genesisTxInfo,
                mock(SeedNodeRepository.class),
                mock(Preferences.class),
                appDataDir,
                false,
                dumpDaoStateHashCheckpoints
        );
    }
}
