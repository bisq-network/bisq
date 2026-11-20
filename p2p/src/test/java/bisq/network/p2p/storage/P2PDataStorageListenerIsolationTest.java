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

package bisq.network.p2p.storage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.TestUtils;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.mocks.ExpirableProtectedStoragePayloadStub;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;

import bisq.common.crypto.CryptoException;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static bisq.network.p2p.storage.TestState.SavedTestState;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

// A listener that fails must not keep the change from the next listener, nor stop the change itself
class P2PDataStorageListenerIsolationTest {
    private TestState testState;
    private KeyPair ownerKeys;
    private ProtectedStoragePayload protectedStoragePayload;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        testState = new TestState();
        ownerKeys = TestUtils.generateKeyPair();
        protectedStoragePayload = new ExpirableProtectedStoragePayloadStub(ownerKeys.getPublic());
    }

    @Test
    void failingListenerDoesNotStopTheAddOfAnEntry() throws CryptoException {
        HashMapChangedListener nextListener = addFailingAndNextHashMapChangedListener();
        ProtectedStorageEntry entry = testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        SavedTestState beforeState = testState.saveTestState(entry);

        assertTrue(testState.mockedStorage.addProtectedStorageEntry(entry, TestState.getTestNodeAddress(), null));

        verify(nextListener).onAdded(Collections.singletonList(entry));
        testState.verifyProtectedStorageAdd(beforeState, entry, true, true, true, true);
    }

    @Test
    void failingListenerDoesNotStopTheRemoveOfAnEntry() throws CryptoException {
        ProtectedStorageEntry entry = testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        assertTrue(testState.mockedStorage.addProtectedStorageEntry(entry, TestState.getTestNodeAddress(), null));
        HashMapChangedListener nextListener = addFailingAndNextHashMapChangedListener();
        ProtectedStorageEntry entryForRemove =
                testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);

        assertTrue(testState.mockedStorage.remove(entryForRemove, TestState.getTestNodeAddress()));

        verify(nextListener).onRemoved(Collections.singletonList(entryForRemove));
    }

    @Test
    void failingListenerDoesNotStopTheAddOfAPersistableNetworkPayload() {
        AppendOnlyDataStoreListener failingListener = mock(AppendOnlyDataStoreListener.class);
        doThrow(new IllegalStateException("listener failure")).when(failingListener).onAdded(any());
        AppendOnlyDataStoreListener nextListener = mock(AppendOnlyDataStoreListener.class);
        testState.mockedStorage.addAppendOnlyDataStoreListener(failingListener);
        testState.mockedStorage.addAppendOnlyDataStoreListener(nextListener);
        PersistableNetworkPayload payload = new PersistableNetworkPayloadStub(true);

        assertTrue(testState.mockedStorage.addPersistableNetworkPayload(payload, TestState.getTestNodeAddress(), true));

        verify(nextListener).onAdded(payload);
        verify(testState.mockBroadcaster).broadcast(any(AddPersistableNetworkPayloadMessage.class),
                nullable(NodeAddress.class));
    }

    private HashMapChangedListener addFailingAndNextHashMapChangedListener() {
        HashMapChangedListener failingListener = mock(HashMapChangedListener.class);
        doThrow(new IllegalStateException("listener failure")).when(failingListener).onAdded(any());
        doThrow(new IllegalStateException("listener failure")).when(failingListener).onRemoved(any());
        HashMapChangedListener nextListener = mock(HashMapChangedListener.class);
        testState.mockedStorage.addHashMapChangedListener(failingListener);
        testState.mockedStorage.addHashMapChangedListener(nextListener);
        return nextListener;
    }
}
