package bisq.network.p2p.storage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.BroadcastHandler;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.storage.messages.AddDataMessage;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.messages.BroadcastMessage;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;
import bisq.network.p2p.storage.messages.RemoveDataMessage;
import bisq.network.p2p.storage.messages.RemoveMailboxDataMessage;
import bisq.network.p2p.storage.mocks.AppendOnlyDataStoreServiceFake;
import bisq.network.p2p.storage.mocks.ClockFake;
import bisq.network.p2p.storage.mocks.ProtectedDataStoreServiceFake;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreListener;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;
import bisq.network.p2p.storage.persistence.ResourceDataStoreService;
import bisq.network.p2p.storage.persistence.SequenceNumberMap;

import bisq.common.crypto.Sig;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.storage.Storage;

import java.security.PublicKey;

import java.time.Clock;

import java.util.concurrent.TimeUnit;

import org.junit.Assert;

import org.mockito.ArgumentCaptor;

import static org.mockito.Mockito.*;

/**
 * Test object that stores a P2PDataStore instance as well as the mock objects necessary for state validation.
 *
 * Used in the P2PDataStorage*Test(s) in order to leverage common test set up and validation.
 */
public class TestState {
    final P2PDataStorage mockedStorage;
    final Broadcaster mockBroadcaster;

    final AppendOnlyDataStoreListener appendOnlyDataStoreListener;
    private final ProtectedDataStoreListener protectedDataStoreListener;
    final HashMapChangedListener hashMapChangedListener;
    private final Storage<SequenceNumberMap> mockSeqNrStorage;
    final ClockFake clockFake;

    /**
     * Subclass of P2PDataStorage that allows for easier testing, but keeps all functionality
     */
    static class P2PDataStorageForTest extends P2PDataStorage {

        P2PDataStorageForTest(NetworkNode networkNode,
                              Broadcaster broadcaster,
                              AppendOnlyDataStoreService appendOnlyDataStoreService,
                              ProtectedDataStoreService protectedDataStoreService,
                              ResourceDataStoreService resourceDataStoreService,
                              Storage<SequenceNumberMap> sequenceNumberMapStorage,
                              Clock clock) {
            super(networkNode, broadcaster, appendOnlyDataStoreService, protectedDataStoreService, resourceDataStoreService, sequenceNumberMapStorage, clock);

            this.maxSequenceNumberMapSizeBeforePurge = 5;
        }
    }

    TestState() {
        this.mockBroadcaster = mock(Broadcaster.class);
        this.mockSeqNrStorage = mock(Storage.class);
        this.clockFake = new ClockFake();

        this.mockedStorage = new P2PDataStorageForTest(mock(NetworkNode.class),
                this.mockBroadcaster,
                new AppendOnlyDataStoreServiceFake(),
                new ProtectedDataStoreServiceFake(), mock(ResourceDataStoreService.class),
                this.mockSeqNrStorage, this.clockFake);

        this.appendOnlyDataStoreListener = mock(AppendOnlyDataStoreListener.class);
        this.protectedDataStoreListener = mock(ProtectedDataStoreListener.class);
        this.hashMapChangedListener = mock(HashMapChangedListener.class);

        this.mockedStorage.addHashMapChangedListener(this.hashMapChangedListener);
        this.mockedStorage.addAppendOnlyDataStoreListener(this.appendOnlyDataStoreListener);
        this.mockedStorage.addProtectedDataStoreListener(this.protectedDataStoreListener);
    }

    private void resetState() {
        reset(this.mockBroadcaster);
        reset(this.appendOnlyDataStoreListener);
        reset(this.protectedDataStoreListener);
        reset(this.hashMapChangedListener);
        reset(this.mockSeqNrStorage);
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
    private static void verifySequenceNumberMapWriteContains(TestState testState,
                                                             P2PDataStorage.ByteArray payloadHash,
                                                             int sequenceNumber) {
        final ArgumentCaptor<SequenceNumberMap> captor = ArgumentCaptor.forClass(SequenceNumberMap.class);
        verify(testState.mockSeqNrStorage).queueUpForSave(captor.capture(), anyLong());

        SequenceNumberMap savedMap = captor.getValue();
        Assert.assertEquals(sequenceNumber, savedMap.get(payloadHash).sequenceNr);
    }

    static void verifyPersistableAdd(TestState currentState,
                                     SavedTestState beforeState,
                                     PersistableNetworkPayload persistableNetworkPayload,
                                     boolean expectedStateChange,
                                     boolean expectedBroadcastAndListenersSignaled,
                                     boolean expectedIsDataOwner) {
        P2PDataStorage.ByteArray hash = new P2PDataStorage.ByteArray(persistableNetworkPayload.getHash());

        if (expectedStateChange) {
            // Payload is accessible from get()
            Assert.assertEquals(persistableNetworkPayload, currentState.mockedStorage.getAppendOnlyDataStoreMap().get(hash));
        } else {
            // On failure, just ensure the state remained the same as before the add
            if (beforeState.persistableNetworkPayloadBeforeOp != null)
                Assert.assertEquals(beforeState.persistableNetworkPayloadBeforeOp, currentState.mockedStorage.getAppendOnlyDataStoreMap().get(hash));
            else
                Assert.assertNull(currentState.mockedStorage.getAppendOnlyDataStoreMap().get(hash));
        }

        if (expectedStateChange && expectedBroadcastAndListenersSignaled) {
            // Broadcast Called
            verify(currentState.mockBroadcaster).broadcast(any(AddPersistableNetworkPayloadMessage.class), any(NodeAddress.class),
                    eq(null), eq(expectedIsDataOwner));

            // Verify the listeners were updated once
            verify(currentState.appendOnlyDataStoreListener).onAdded(persistableNetworkPayload);

        } else {
            verify(currentState.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), any(NodeAddress.class), any(BroadcastHandler.Listener.class), anyBoolean());

            // Verify the listeners were never updated
            verify(currentState.appendOnlyDataStoreListener, never()).onAdded(persistableNetworkPayload);
        }
    }

    static void verifyProtectedStorageAdd(TestState currentState,
                                          SavedTestState beforeState,
                                          ProtectedStorageEntry protectedStorageEntry,
                                          boolean expectedStateChange,
                                          boolean expectedIsDataOwner) {
        P2PDataStorage.ByteArray hashMapHash = P2PDataStorage.get32ByteHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload());
        P2PDataStorage.ByteArray storageHash = P2PDataStorage.getCompactHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload());

        if (expectedStateChange) {
            Assert.assertEquals(protectedStorageEntry, currentState.mockedStorage.getMap().get(hashMapHash));

            // PersistablePayload payloads need to be written to disk and listeners signaled... unless the hash already exists in the protectedDataStore.
            // Note: this behavior is different from the HashMap listeners that are signaled on an increase in seq #, even if the hash already exists.
            // TODO: Should the behavior be identical between this and the HashMap listeners?
            // TODO: Do we want ot overwrite stale values in order to persist updated sequence numbers and timestamps?
            if (protectedStorageEntry.getProtectedStoragePayload() instanceof PersistablePayload && beforeState.protectedStorageEntryBeforeOpDataStoreMap == null) {
                Assert.assertEquals(protectedStorageEntry, currentState.mockedStorage.getProtectedDataStoreMap().get(storageHash));
                verify(currentState.protectedDataStoreListener).onAdded(protectedStorageEntry);
            } else {
                Assert.assertEquals(beforeState.protectedStorageEntryBeforeOpDataStoreMap, currentState.mockedStorage.getProtectedDataStoreMap().get(storageHash));
                verify(currentState.protectedDataStoreListener, never()).onAdded(protectedStorageEntry);
            }

            verify(currentState.hashMapChangedListener).onAdded(protectedStorageEntry);

            final ArgumentCaptor<BroadcastMessage> captor = ArgumentCaptor.forClass(BroadcastMessage.class);
            verify(currentState.mockBroadcaster).broadcast(captor.capture(), any(NodeAddress.class),
                    eq(null), eq(expectedIsDataOwner));

            BroadcastMessage broadcastMessage = captor.getValue();
            Assert.assertTrue(broadcastMessage instanceof AddDataMessage);
            Assert.assertEquals(protectedStorageEntry, ((AddDataMessage) broadcastMessage).getProtectedStorageEntry());

            verifySequenceNumberMapWriteContains(currentState, P2PDataStorage.get32ByteHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload()), protectedStorageEntry.getSequenceNumber());
        } else {
            Assert.assertEquals(beforeState.protectedStorageEntryBeforeOp, currentState.mockedStorage.getMap().get(hashMapHash));
            Assert.assertEquals(beforeState.protectedStorageEntryBeforeOpDataStoreMap, currentState.mockedStorage.getProtectedDataStoreMap().get(storageHash));

            verify(currentState.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), any(NodeAddress.class), any(BroadcastHandler.Listener.class), anyBoolean());

            // Internal state didn't change... nothing should be notified
            verify(currentState.hashMapChangedListener, never()).onAdded(protectedStorageEntry);
            verify(currentState.protectedDataStoreListener, never()).onAdded(protectedStorageEntry);
            verify(currentState.mockSeqNrStorage, never()).queueUpForSave(any(SequenceNumberMap.class), anyLong());
        }
    }

    static void verifyProtectedStorageRemove(TestState currentState,
                                             SavedTestState beforeState,
                                             ProtectedStorageEntry protectedStorageEntry,
                                             boolean expectedStateChange,
                                             boolean expectedBroadcastOnStateChange,
                                             boolean expectedSeqNrWriteOnStateChange,
                                             boolean expectedIsDataOwner) {
        P2PDataStorage.ByteArray hashMapHash = P2PDataStorage.get32ByteHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload());
        P2PDataStorage.ByteArray storageHash = P2PDataStorage.getCompactHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload());

        if (expectedStateChange) {
            Assert.assertNull(currentState.mockedStorage.getMap().get(hashMapHash));

            if (protectedStorageEntry.getProtectedStoragePayload() instanceof PersistablePayload) {
                Assert.assertNull(currentState.mockedStorage.getProtectedDataStoreMap().get(storageHash));

                verify(currentState.protectedDataStoreListener).onRemoved(protectedStorageEntry);
            }

            verify(currentState.hashMapChangedListener).onRemoved(protectedStorageEntry);

            if (expectedSeqNrWriteOnStateChange)
                verifySequenceNumberMapWriteContains(currentState, P2PDataStorage.get32ByteHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload()), protectedStorageEntry.getSequenceNumber());

            if (expectedBroadcastOnStateChange) {
                if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry)
                    verify(currentState.mockBroadcaster).broadcast(any(RemoveMailboxDataMessage.class), any(NodeAddress.class), eq(null), eq(expectedIsDataOwner));
                else
                    verify(currentState.mockBroadcaster).broadcast(any(RemoveDataMessage.class), any(NodeAddress.class), eq(null), eq(expectedIsDataOwner));
            }

        } else {
            Assert.assertEquals(beforeState.protectedStorageEntryBeforeOp, currentState.mockedStorage.getMap().get(hashMapHash));

            verify(currentState.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), any(NodeAddress.class), any(BroadcastHandler.Listener.class), anyBoolean());
            verify(currentState.hashMapChangedListener, never()).onAdded(protectedStorageEntry);
            verify(currentState.protectedDataStoreListener, never()).onAdded(protectedStorageEntry);
            verify(currentState.mockSeqNrStorage, never()).queueUpForSave(any(SequenceNumberMap.class), anyLong());
        }
    }

    static void verifyRefreshTTL(TestState currentState,
                                 SavedTestState beforeState,
                                 RefreshOfferMessage refreshOfferMessage,
                                 boolean expectedStateChange,
                                 boolean expectedIsDataOwner) {
        P2PDataStorage.ByteArray payloadHash = new P2PDataStorage.ByteArray(refreshOfferMessage.getHashOfPayload());

        ProtectedStorageEntry entryAfterRefresh = currentState.mockedStorage.getMap().get(payloadHash);

        if (expectedStateChange) {
            Assert.assertNotNull(entryAfterRefresh);
            Assert.assertEquals(refreshOfferMessage.getSequenceNumber(), entryAfterRefresh.getSequenceNumber());
            Assert.assertEquals(refreshOfferMessage.getSignature(), entryAfterRefresh.getSignature());
            Assert.assertTrue(entryAfterRefresh.getCreationTimeStamp() > beforeState.creationTimestampBeforeUpdate);

            final ArgumentCaptor<BroadcastMessage> captor = ArgumentCaptor.forClass(BroadcastMessage.class);
            verify(currentState.mockBroadcaster).broadcast(captor.capture(), any(NodeAddress.class),
                    eq(null), eq(expectedIsDataOwner));

            BroadcastMessage broadcastMessage = captor.getValue();
            Assert.assertTrue(broadcastMessage instanceof RefreshOfferMessage);
            Assert.assertEquals(refreshOfferMessage, broadcastMessage);

            verifySequenceNumberMapWriteContains(currentState, payloadHash, refreshOfferMessage.getSequenceNumber());
        } else {

            // Verify the existing entry is unchanged
            if (beforeState.protectedStorageEntryBeforeOp != null) {
                Assert.assertEquals(entryAfterRefresh, beforeState.protectedStorageEntryBeforeOp);
                Assert.assertEquals(beforeState.protectedStorageEntryBeforeOp.getSequenceNumber(), entryAfterRefresh.getSequenceNumber());
                Assert.assertEquals(beforeState.protectedStorageEntryBeforeOp.getSignature(), entryAfterRefresh.getSignature());
                Assert.assertEquals(beforeState.creationTimestampBeforeUpdate, entryAfterRefresh.getCreationTimeStamp());
            }

            verify(currentState.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), any(NodeAddress.class), any(BroadcastHandler.Listener.class), anyBoolean());
            verify(currentState.mockSeqNrStorage, never()).queueUpForSave(any(SequenceNumberMap.class), anyLong());
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

        SavedTestState(TestState testState, PersistableNetworkPayload persistableNetworkPayload) {
            this(testState);
            P2PDataStorage.ByteArray hash = new P2PDataStorage.ByteArray(persistableNetworkPayload.getHash());
            this.persistableNetworkPayloadBeforeOp = testState.mockedStorage.getAppendOnlyDataStoreMap().get(hash);
        }

        SavedTestState(TestState testState, ProtectedStorageEntry protectedStorageEntry) {
            this(testState);

            P2PDataStorage.ByteArray storageHash = P2PDataStorage.getCompactHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload());
            this.protectedStorageEntryBeforeOpDataStoreMap = testState.mockedStorage.getProtectedDataStoreMap().get(storageHash);

            P2PDataStorage.ByteArray hashMapHash = P2PDataStorage.get32ByteHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload());
            this.protectedStorageEntryBeforeOp = testState.mockedStorage.getMap().get(hashMapHash);

            this.creationTimestampBeforeUpdate = (this.protectedStorageEntryBeforeOp != null) ? this.protectedStorageEntryBeforeOp.getCreationTimeStamp() : 0;
        }

        SavedTestState(TestState testState, RefreshOfferMessage refreshOfferMessage) {
            this(testState);

            P2PDataStorage.ByteArray hashMapHash = new P2PDataStorage.ByteArray(refreshOfferMessage.getHashOfPayload());
            this.protectedStorageEntryBeforeOp = testState.mockedStorage.getMap().get(hashMapHash);

            this.creationTimestampBeforeUpdate = (this.protectedStorageEntryBeforeOp != null) ? this.protectedStorageEntryBeforeOp.getCreationTimeStamp() : 0;
        }
    }
}
