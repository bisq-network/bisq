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

package io.bisq.core.dao.blockchain;

import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

@RunWith(PowerMockRunner.class)
@PrepareForTest({ReadableBsqBlockChain.class, WritableBsqBlockChain.class, PersistenceProtoResolver.class, File.class})
public class BsqBlockChainTest {

    private SnapshotManager snapshotManager;

    @Before
    public void setup() {
        snapshotManager = new SnapshotManager(mock(ReadableBsqBlockChain.class), mock(WritableBsqBlockChain.class), mock(PersistenceProtoResolver.class), mock(File.class));
    }

    @Test
    public void testGetSnapshotHeight() {
        assertEquals(120, snapshotManager.getSnapshotHeight(102, 0, 10));
        assertEquals(120, snapshotManager.getSnapshotHeight(102, 100, 10));
        assertEquals(120, snapshotManager.getSnapshotHeight(102, 102, 10));
        assertEquals(120, snapshotManager.getSnapshotHeight(102, 119, 10));
        assertEquals(120, snapshotManager.getSnapshotHeight(102, 120, 10));
        assertEquals(120, snapshotManager.getSnapshotHeight(102, 121, 10));
        assertEquals(120, snapshotManager.getSnapshotHeight(102, 130, 10));
        assertEquals(120, snapshotManager.getSnapshotHeight(102, 139, 10));
        assertEquals(130, snapshotManager.getSnapshotHeight(102, 140, 10));
        assertEquals(130, snapshotManager.getSnapshotHeight(102, 141, 10));
        assertEquals(990, snapshotManager.getSnapshotHeight(102, 1000, 10));
    }

    @Test
    public void testSnapshotHeight() {
        assertFalse(snapshotManager.isSnapshotHeight(102, 0, 10));
        assertFalse(snapshotManager.isSnapshotHeight(102, 80, 10));
        assertFalse(snapshotManager.isSnapshotHeight(102, 90, 10));
        assertFalse(snapshotManager.isSnapshotHeight(102, 100, 10));
        assertFalse(snapshotManager.isSnapshotHeight(102, 119, 10));
        assertTrue(snapshotManager.isSnapshotHeight(102, 120, 10));
        assertTrue(snapshotManager.isSnapshotHeight(102, 130, 10));
        assertTrue(snapshotManager.isSnapshotHeight(102, 140, 10));
        assertTrue(snapshotManager.isSnapshotHeight(102, 200, 10));
        assertFalse(snapshotManager.isSnapshotHeight(102, 201, 10));
        assertFalse(snapshotManager.isSnapshotHeight(102, 199, 10));
    }
}
