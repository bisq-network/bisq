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
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.storage.messages.AddDataMessage;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.messages.BroadcastMessage;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;
import bisq.network.p2p.storage.messages.RemoveDataMessage;
import bisq.network.p2p.storage.messages.RemoveMailboxDataMessage;
import bisq.network.p2p.storage.mocks.AppendOnlyDataStoreServiceFake;
import bisq.network.p2p.storage.mocks.ClockFake;
import bisq.network.p2p.storage.mocks.MapStoreServiceFake;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;
import bisq.network.p2p.storage.persistence.RemovedPayloadsService;
import bisq.network.p2p.storage.persistence.ResourceDataStoreService;
import bisq.network.p2p.storage.persistence.SequenceNumberMap;

import bisq.common.crypto.Sig;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistablePayload;

import java.security.PublicKey;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.mockito.ArgumentCaptor;

import org.junit.Assert;

import static org.mockito.Mockito.*;

/**
 * Test object that stores a P2PDataStore instance as well as the mock objects necessary for state validation.
 *
 * Used in the P2PDataStorage*Test(s) in order to leverage common test set up and validation.
 */
public class TestState {
    static final int MAX_SEQUENCE_NUMBER_MAP_SIZE_BEFORE_PURGE = 5;

    P2PDataStorage mockedStorage;
    final Broadcaster mockBroadcaster;

    final AppendOnlyDataStoreListener appendOnlyDataStoreListener;
    private final HashMapChangedListener hashMapChangedListener;
    private final PersistenceManager<SequenceNumberMap> mockSeqNrPersistenceManager;
    private final ProtectedDataStoreService protectedDataStoreService;
    final ClockFake clockFake;
    private RemovedPayloadsService removedPayloadsService;

    TestState() {
        this.mockBroadcaster = mock(Broadcaster.class);
        this.mockSeqNrPersistenceManager = mock(PersistenceManager.class);
        this.removedPayloadsService = mock(RemovedPayloadsService.class);
        this.clockFake = new ClockFake();
        this.protectedDataStoreService = new ProtectedDataStoreService();

        this.mockedStorage = new P2PDataStorage(mock(NetworkNode.class),
                this.mockBroadcaster,
                new AppendOnlyDataStoreServiceFake(),
                this.protectedDataStoreService, mock(ResourceDataStoreService.class),
                this.mockSeqNrPersistenceManager,
                removedPayloadsService,
                this.clockFake,
                MAX_SEQUENCE_NUMBER_MAP_SIZE_BEFORE_PURGE);

        this.appendOnlyDataStoreListener = mock(AppendOnlyDataStoreListener.class);
        this.hashMapChangedListener = mock(HashMapChangedListener.class);
        this.protectedDataStoreService.addService(new MapStoreServiceFake());

        this.mockedStorage = createP2PDataStorageForTest(
                this.mockBroadcaster,
                this.protectedDataStoreService,
                this.mockSeqNrPersistenceManager,
                this.clockFake,
                this.hashMapChangedListener,
                this.appendOnlyDataStoreListener,
                removedPayloadsService);

        when(this.mockSeqNrPersistenceManager.getPersisted())
                .thenReturn(this.mockedStorage.sequenceNumberMap);
    }


    /**
     * Re-initializes the in-memory data structures from the storage objects to simulate a node restarting. Important
     * to note that the current TestState uses Test Doubles instead of actual disk storage so this is just "simulating"
     * not running the entire storage code paths.
     */
    void simulateRestart() {
        this.removedPayloadsService = mock(RemovedPayloadsService.class);
        this.mockedStorage = createP2PDataStorageForTest(
                this.mockBroadcaster,
                this.protectedDataStoreService,
                this.mockSeqNrPersistenceManager,
                this.clockFake,
                this.hashMapChangedListener,
                this.appendOnlyDataStoreListener,
                removedPayloadsService);

        when(this.mockSeqNrPersistenceManager.getPersisted())
                .thenReturn(this.mockedStorage.sequenceNumberMap);
    }

    private static P2PDataStorage createP2PDataStorageForTest(
            Broadcaster broadcaster,
            ProtectedDataStoreService protectedDataStoreService,
            PersistenceManager<SequenceNumberMap> sequenceNrMapPersistenceManager,
            ClockFake clock,
            HashMapChangedListener hashMapChangedListener,
            AppendOnlyDataStoreListener appendOnlyDataStoreListener,
            RemovedPayloadsService removedPayloadsService) {

        P2PDataStorage p2PDataStorage = new P2PDataStorage(mock(NetworkNode.class),
                broadcaster,
                new AppendOnlyDataStoreServiceFake(),
                protectedDataStoreService,
                mock(ResourceDataStoreService.class),
                sequenceNrMapPersistenceManager,
                removedPayloadsService,
                clock,
                MAX_SEQUENCE_NUMBER_MAP_SIZE_BEFORE_PURGE);

        // Currently TestState only supports reading ProtectedStorageEntries off disk.
        p2PDataStorage.readFromResourcesSync("unused");
        p2PDataStorage.readPersistedSync();

        p2PDataStorage.addHashMapChangedListener(hashMapChangedListener);
        p2PDataStorage.addAppendOnlyDataStoreListener(appendOnlyDataStoreListener);

        return p2PDataStorage;
    }

    private void resetState() {
        reset(this.mockBroadcaster);
        reset(this.appendOnlyDataStoreListener);
        reset(this.hashMapChangedListener);
    }

    void incrementClock() {
        this.clockFake.increment(TimeUnit.HOURS.toMillis(1));
    }

    public static NodeAddress getTestNodeAddress() {
        return new NodeAddress("address", 8080);
    }

    /**
     * Common test helpers that verify the correct events were signaled based on the test expectation and before/after states.
     */
    private void verifySequenceNumberMapWriteContains(P2PDataStorage.ByteArray payloadHash, int sequenceNumber) {
        Assert.assertEquals(sequenceNumber, mockSeqNrPersistenceManager.getPersisted().get(payloadHash).sequenceNr);
    }

    void verifyPersistableAdd(SavedTestState beforeState,
                              PersistableNetworkPayload persistableNetworkPayload,
                              boolean expectedHashMapAndDataStoreUpdated,
                              boolean expectedListenersSignaled,
                              boolean expectedBroadcast) {
        P2PDataStorage.ByteArray hash = new P2PDataStorage.ByteArray(persistableNetworkPayload.getHash());

        if (expectedHashMapAndDataStoreUpdated)
            Assert.assertEquals(persistableNetworkPayload, this.mockedStorage.appendOnlyDataStoreService.getMap(persistableNetworkPayload).get(hash));
        else
            Assert.assertEquals(beforeState.persistableNetworkPayloadBeforeOp, this.mockedStorage.appendOnlyDataStoreService.getMap(persistableNetworkPayload).get(hash));

        if (expectedListenersSignaled)
            verify(this.appendOnlyDataStoreListener).onAdded(persistableNetworkPayload);
        else
            verify(this.appendOnlyDataStoreListener, never()).onAdded(persistableNetworkPayload);

        if (expectedBroadcast)
            verify(this.mockBroadcaster).broadcast(any(AddPersistableNetworkPayloadMessage.class), nullable(NodeAddress.class));
        else
            verify(this.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), nullable(NodeAddress.class));
    }

    void verifyProtectedStorageAdd(SavedTestState beforeState,
                                   ProtectedStorageEntry protectedStorageEntry,
                                   boolean expectedHashMapAndDataStoreUpdated,
                                   boolean expectedListenersSignaled,
                                   boolean expectedBroadcast,
                                   boolean expectedSequenceNrMapWrite) {
        P2PDataStorage.ByteArray hashMapHash = P2PDataStorage.get32ByteHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload());

        if (expectedHashMapAndDataStoreUpdated) {
            Assert.assertEquals(protectedStorageEntry, this.mockedStorage.getMap().get(hashMapHash));

            if (protectedStorageEntry.getProtectedStoragePayload() instanceof PersistablePayload)
                Assert.assertEquals(protectedStorageEntry, this.protectedDataStoreService.getMap().get(hashMapHash));
        } else {
            Assert.assertEquals(beforeState.protectedStorageEntryBeforeOp, this.mockedStorage.getMap().get(hashMapHash));
            Assert.assertEquals(beforeState.protectedStorageEntryBeforeOpDataStoreMap, this.protectedDataStoreService.getMap().get(hashMapHash));
        }

        if (expectedListenersSignaled) {
            verify(this.hashMapChangedListener).onAdded(Collections.singletonList(protectedStorageEntry));
        } else {
            verify(this.hashMapChangedListener, never()).onAdded(Collections.singletonList(protectedStorageEntry));
        }

        if (expectedBroadcast) {
            final ArgumentCaptor<BroadcastMessage> captor = ArgumentCaptor.forClass(BroadcastMessage.class);
            // If we remove the last argument (isNull()) tests fail. No idea why as the broadcast method has an
            // overloaded method with nullable listener. Seems a testframework issue as it should not matter if the
            // method with listener is called with null argument or the other method with no listener. We removed the
            // null value from all other calls but here we can't as it breaks the test.
            verify(this.mockBroadcaster).broadcast(captor.capture(), nullable(NodeAddress.class), isNull());

            BroadcastMessage broadcastMessage = captor.getValue();
            Assert.assertTrue(broadcastMessage instanceof AddDataMessage);
            Assert.assertEquals(protectedStorageEntry, ((AddDataMessage) broadcastMessage).getProtectedStorageEntry());
        } else {
            verify(this.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), nullable(NodeAddress.class));
        }

        if (expectedSequenceNrMapWrite) {
            this.verifySequenceNumberMapWriteContains(P2PDataStorage.get32ByteHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload()), protectedStorageEntry.getSequenceNumber());
        }
    }

    void verifyProtectedStorageRemove(SavedTestState beforeState,
                                      ProtectedStorageEntry protectedStorageEntry,
                                      boolean expectedHashMapAndDataStoreUpdated,
                                      boolean expectedListenersSignaled,
                                      boolean expectedBroadcast,
                                      boolean expectedSeqNrWrite) {

        verifyProtectedStorageRemove(beforeState, Collections.singletonList(protectedStorageEntry),
                expectedHashMapAndDataStoreUpdated, expectedListenersSignaled, expectedBroadcast,
                expectedSeqNrWrite);
    }

    void verifyProtectedStorageRemove(SavedTestState beforeState,
                                      Collection<ProtectedStorageEntry> protectedStorageEntries,
                                      boolean expectedHashMapAndDataStoreUpdated,
                                      boolean expectedListenersSignaled,
                                      boolean expectedBroadcast,
                                      boolean expectedSeqNrWrite) {

        // The default matcher expects orders to stay the same. So, create a custom matcher function since
        // we don't care about the order.
        if (expectedListenersSignaled) {
            final ArgumentCaptor<Collection<ProtectedStorageEntry>> argument = ArgumentCaptor.forClass(Collection.class);
            verify(this.hashMapChangedListener).onRemoved(argument.capture());

            Set<ProtectedStorageEntry> actual = new HashSet<>(argument.getValue());
            Set<ProtectedStorageEntry> expected = new HashSet<>(protectedStorageEntries);

            // Ensure we didn't remove duplicates
            Assert.assertEquals(protectedStorageEntries.size(), expected.size());
            Assert.assertEquals(argument.getValue().size(), actual.size());
            Assert.assertEquals(expected, actual);
        } else {
            verify(this.hashMapChangedListener, never()).onRemoved(any());
        }

        if (!expectedBroadcast)
            verify(this.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), nullable(NodeAddress.class));


        protectedStorageEntries.forEach(protectedStorageEntry -> {
            P2PDataStorage.ByteArray hashMapHash = P2PDataStorage.get32ByteHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload());

            if (expectedSeqNrWrite)
                this.verifySequenceNumberMapWriteContains(P2PDataStorage.get32ByteHashAsByteArray(
                        protectedStorageEntry.getProtectedStoragePayload()), protectedStorageEntry.getSequenceNumber());

            if (expectedBroadcast) {
                if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry)
                    verify(this.mockBroadcaster).broadcast(any(RemoveMailboxDataMessage.class), nullable(NodeAddress.class));
                else
                    verify(this.mockBroadcaster).broadcast(any(RemoveDataMessage.class), nullable(NodeAddress.class));
            }


            if (expectedHashMapAndDataStoreUpdated) {
                Assert.assertNull(this.mockedStorage.getMap().get(hashMapHash));

                if (protectedStorageEntry.getProtectedStoragePayload() instanceof PersistablePayload)
                    Assert.assertNull(this.protectedDataStoreService.getMap().get(hashMapHash));

            } else {
                Assert.assertEquals(beforeState.protectedStorageEntryBeforeOp, this.mockedStorage.getMap().get(hashMapHash));
            }
        });
    }

    void verifyRefreshTTL(SavedTestState beforeState,
                          RefreshOfferMessage refreshOfferMessage,
                          boolean expectedStateChange) {
        P2PDataStorage.ByteArray payloadHash = new P2PDataStorage.ByteArray(refreshOfferMessage.getHashOfPayload());

        ProtectedStorageEntry entryAfterRefresh = this.mockedStorage.getMap().get(payloadHash);

        if (expectedStateChange) {
            Assert.assertNotNull(entryAfterRefresh);
            Assert.assertEquals(refreshOfferMessage.getSequenceNumber(), entryAfterRefresh.getSequenceNumber());
            Assert.assertEquals(refreshOfferMessage.getSignature(), entryAfterRefresh.getSignature());
            Assert.assertTrue(entryAfterRefresh.getCreationTimeStamp() > beforeState.creationTimestampBeforeUpdate);

            final ArgumentCaptor<BroadcastMessage> captor = ArgumentCaptor.forClass(BroadcastMessage.class);
            verify(this.mockBroadcaster).broadcast(captor.capture(), nullable(NodeAddress.class));

            BroadcastMessage broadcastMessage = captor.getValue();
            Assert.assertTrue(broadcastMessage instanceof RefreshOfferMessage);
            Assert.assertEquals(refreshOfferMessage, broadcastMessage);

            this.verifySequenceNumberMapWriteContains(payloadHash, refreshOfferMessage.getSequenceNumber());
        } else {

            // Verify the existing entry is unchanged
            if (beforeState.protectedStorageEntryBeforeOp != null) {
                Assert.assertEquals(entryAfterRefresh, beforeState.protectedStorageEntryBeforeOp);
                Assert.assertEquals(beforeState.protectedStorageEntryBeforeOp.getSequenceNumber(), entryAfterRefresh.getSequenceNumber());
                Assert.assertEquals(beforeState.protectedStorageEntryBeforeOp.getSignature(), entryAfterRefresh.getSignature());
                Assert.assertEquals(beforeState.creationTimestampBeforeUpdate, entryAfterRefresh.getCreationTimeStamp());
            }

            verify(this.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), nullable(NodeAddress.class));
        }
    }

    static MailboxStoragePayload buildMailboxStoragePayload(PublicKey senderKey, PublicKey receiverKey) {
        // Need to be able to take the hash which leverages protobuf Messages
        protobuf.StoragePayload messageMock = mock(protobuf.StoragePayload.class);
        when(messageMock.toByteArray()).thenReturn(Sig.getPublicKeyBytes(receiverKey));

        MailboxStoragePayload payloadMock = mock(MailboxStoragePayload.class);
        when(payloadMock.getOwnerPubKey()).thenReturn(receiverKey);
        when(payloadMock.getSenderPubKeyForAddOperation()).thenReturn(senderKey);
        when(payloadMock.toProtoMessage()).thenReturn(messageMock);

        return payloadMock;
    }

    SavedTestState saveTestState(PersistableNetworkPayload persistableNetworkPayload) {
        return new SavedTestState(this, persistableNetworkPayload);
    }

    SavedTestState saveTestState(ProtectedStorageEntry protectedStorageEntry) {
        return new SavedTestState(this, protectedStorageEntry);
    }

    SavedTestState saveTestState(RefreshOfferMessage refreshOfferMessage) {
        return new SavedTestState(this, refreshOfferMessage);
    }

    /**
     * Wrapper object for TestState state that needs to be saved for future validation. Used in multiple tests
     * to verify that the state before and after an operation matched the expectation.
     */
    static class SavedTestState {
        final TestState state;

        // Used in PersistableNetworkPayload tests
        PersistableNetworkPayload persistableNetworkPayloadBeforeOp;

        // Used in ProtectedStorageEntry tests
        ProtectedStorageEntry protectedStorageEntryBeforeOp;
        ProtectedStorageEntry protectedStorageEntryBeforeOpDataStoreMap;

        long creationTimestampBeforeUpdate;

        private SavedTestState(TestState state) {
            this.state = state;
            this.creationTimestampBeforeUpdate = 0;
            this.state.resetState();
        }

        private SavedTestState(TestState testState, PersistableNetworkPayload persistableNetworkPayload) {
            this(testState);
            P2PDataStorage.ByteArray hash = new P2PDataStorage.ByteArray(persistableNetworkPayload.getHash());
            this.persistableNetworkPayloadBeforeOp = testState.mockedStorage.appendOnlyDataStoreService.getMap(persistableNetworkPayload).get(hash);
        }

        private SavedTestState(TestState testState, ProtectedStorageEntry protectedStorageEntry) {
            this(testState);

            P2PDataStorage.ByteArray hashMapHash = P2PDataStorage.get32ByteHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload());
            this.protectedStorageEntryBeforeOp = testState.mockedStorage.getMap().get(hashMapHash);
            this.protectedStorageEntryBeforeOpDataStoreMap = testState.protectedDataStoreService.getMap().get(hashMapHash);


            this.creationTimestampBeforeUpdate = (this.protectedStorageEntryBeforeOp != null) ? this.protectedStorageEntryBeforeOp.getCreationTimeStamp() : 0;
        }

        private SavedTestState(TestState testState, RefreshOfferMessage refreshOfferMessage) {
            this(testState);

            P2PDataStorage.ByteArray hashMapHash = new P2PDataStorage.ByteArray(refreshOfferMessage.getHashOfPayload());
            this.protectedStorageEntryBeforeOp = testState.mockedStorage.getMap().get(hashMapHash);

            this.creationTimestampBeforeUpdate = (this.protectedStorageEntryBeforeOp != null) ? this.protectedStorageEntryBeforeOp.getCreationTimeStamp() : 0;
        }
    }
}
