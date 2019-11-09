package bisq.network.p2p.storage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.PrefixedSealedAndSignedMessage;
import bisq.network.p2p.TestUtils;
import bisq.network.p2p.mocks.MockPayload;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.BroadcastHandler;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.storage.messages.AddDataMessage;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.messages.BroadcastMessage;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;
import bisq.network.p2p.storage.messages.RemoveDataMessage;
import bisq.network.p2p.storage.messages.RemoveMailboxDataMessage;
import bisq.network.p2p.storage.mocks.*;
import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import bisq.network.p2p.storage.payload.RequiresOwnerIsOnlinePayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreListener;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;
import bisq.network.p2p.storage.persistence.ResourceDataStoreService;
import bisq.network.p2p.storage.persistence.SequenceNumberMap;

import bisq.common.app.Version;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.SealedAndSigned;
import bisq.common.crypto.Sig;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.storage.Storage;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

import java.time.Clock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

@RunWith(Enclosed.class)
public class P2PDataStorageTest {

    // Test class used for validating the ExpirablePayload, RequiresOwnerIsOnlinePayload marker interfaces
    static class ExpirableProtectedStoragePayload extends ProtectedStoragePayloadStub implements ExpirablePayload, RequiresOwnerIsOnlinePayload {
        private long ttl;

        ExpirableProtectedStoragePayload(PublicKey ownerPubKey) {
            super(ownerPubKey);
            ttl = TimeUnit.DAYS.toMillis(90);
        }

        ExpirableProtectedStoragePayload(PublicKey ownerPubKey, long ttl) {
            this(ownerPubKey);
            this.ttl = ttl;
        }

        @Override
        public NodeAddress getOwnerNodeAddress() {
            return getTestNodeAddress();
        }

        @Override
        public long getTTL() {
            return this.ttl;
        }
    }

    static class PersistableExpirableProtectedStoragePayload extends ExpirableProtectedStoragePayload implements PersistablePayload {

        PersistableExpirableProtectedStoragePayload(PublicKey ownerPubKey) {
            super(ownerPubKey);
        }

        PersistableExpirableProtectedStoragePayload(PublicKey ownerPubKey, long ttl) {
            super(ownerPubKey, ttl);
        }
    }

    // Common state for tests that initializes the P2PDataStore and mocks out the dependencies. Allows
    // shared state verification between all tests.
    static class TestState {
        final P2PDataStorage mockedStorage;
        final Broadcaster mockBroadcaster;

        final AppendOnlyDataStoreListener appendOnlyDataStoreListener;
        final ProtectedDataStoreListener protectedDataStoreListener;
        final HashMapChangedListener hashMapChangedListener;
        final Storage<SequenceNumberMap> mockSeqNrStorage;
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

        void resetState() {
            reset(this.mockBroadcaster);
            reset(this.appendOnlyDataStoreListener);
            reset(this.protectedDataStoreListener);
            reset(this.hashMapChangedListener);
            reset(this.mockSeqNrStorage);
        }

        void incrementClock() {
            this.clockFake.increment(TimeUnit.HOURS.toMillis(1));
        }
    }

    // Represents a snapshot of a TestState allowing easier verification of state before and after an operation.
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

    private static NodeAddress getTestNodeAddress() {
        return new NodeAddress("address", 8080);
    }


    /*
     * Helper functions that create Payloads and Entrys for the various tests. This allow fabrication of a variety of
     * valid and invalid Entrys that are used to test the correct behavior.
     */
    private static ProtectedStorageEntry buildProtectedStorageEntry(
            ProtectedStoragePayload protectedStoragePayload,
            KeyPair entryOwnerKeys,
            KeyPair entrySignerKeys,
            int sequenceNumber,
            Clock clock) throws CryptoException {
        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new P2PDataStorage.DataAndSeqNrPair(protectedStoragePayload, sequenceNumber));
        byte[] signature = Sig.sign(entrySignerKeys.getPrivate(), hashOfDataAndSeqNr);

        return new ProtectedStorageEntry(protectedStoragePayload, entryOwnerKeys.getPublic(), sequenceNumber, signature, clock);
    }

    private static MailboxStoragePayload buildMailboxStoragePayload(PublicKey payloadSenderPubKeyForAddOperation,
                                                                    PublicKey payloadOwnerPubKey) {

        // Create unused, but well-formed sealedAndSigned so that a hash can be taken (internal to P2PDataStorage). Not actually validated.
        SealedAndSigned sealedAndSigned = new SealedAndSigned(new byte[] { 0 }, new byte[] { 0 }, new byte[] { 0 }, payloadOwnerPubKey);
        PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage =
                new PrefixedSealedAndSignedMessage(new NodeAddress("host", 1000), sealedAndSigned, new byte[] { 0 },
                        "UUID");

        return new MailboxStoragePayload(
                prefixedSealedAndSignedMessage, payloadSenderPubKeyForAddOperation, payloadOwnerPubKey);
    }

    private static ProtectedStorageEntry buildProtectedMailboxStorageEntry(
            PublicKey payloadSenderPubKeyForAddOperation,
            PublicKey payloadOwnerPubKey,
            PrivateKey entrySigner,
            PublicKey entryOwnerPubKey,
            PublicKey entryReceiversPubKey,
            int sequenceNumber,
            Clock clock) throws CryptoException {

        MailboxStoragePayload payload = buildMailboxStoragePayload(payloadSenderPubKeyForAddOperation, payloadOwnerPubKey);

        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new P2PDataStorage.DataAndSeqNrPair(payload, sequenceNumber));
        byte[] signature = Sig.sign(entrySigner, hashOfDataAndSeqNr);
        return new ProtectedMailboxStorageEntry(payload,
                entryOwnerPubKey, sequenceNumber, signature, entryReceiversPubKey, clock);
    }

    private static RefreshOfferMessage buildRefreshOfferMessage(ProtectedStoragePayload protectedStoragePayload,
                                                                KeyPair ownerKeys,
                                                                int sequenceNumber) throws CryptoException {

        P2PDataStorage.ByteArray hashOfPayload = P2PDataStorage.get32ByteHashAsByteArray(protectedStoragePayload);

        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new P2PDataStorage.DataAndSeqNrPair(protectedStoragePayload, sequenceNumber));
        byte[] signature = Sig.sign(ownerKeys.getPrivate(), hashOfDataAndSeqNr);
        return new RefreshOfferMessage(hashOfDataAndSeqNr, signature, hashOfPayload.bytes, sequenceNumber);
    }

    /*
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

    private static void verifyPersistableAdd(TestState currentState,
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

    private static void verifyProtectedStorageAdd(TestState currentState,
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

    private static void verifyProtectedStorageRemove(TestState currentState,
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

    private static void verifyRefreshTTL(TestState currentState,
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

    static class UnsupportedBroadcastMessage extends BroadcastMessage {

        UnsupportedBroadcastMessage() {
            super(0);
        }
    }

    public static class OnMessageHandlerTest {
        TestState testState;

        @Before
        public void setup() {
            this.testState = new TestState();
        }

        @Test
        public void invalidBroadcastMessage() {
            NetworkEnvelope envelope = new MockPayload("Mock");

            Connection mockedConnection = mock(Connection.class);
            when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

            this.testState.mockedStorage.onMessage(envelope, mockedConnection);

            verify(this.testState.appendOnlyDataStoreListener, never()).onAdded(any(PersistableNetworkPayload.class));
            verify(this.testState.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), any(NodeAddress.class), eq(null), anyBoolean());
        }

        @Test
        public void unsupportedBroadcastMessage() {
            NetworkEnvelope envelope = new UnsupportedBroadcastMessage();

            Connection mockedConnection = mock(Connection.class);
            when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

            this.testState.mockedStorage.onMessage(envelope, mockedConnection);

            verify(this.testState.appendOnlyDataStoreListener, never()).onAdded(any(PersistableNetworkPayload.class));
            verify(this.testState.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), any(NodeAddress.class), eq(null), anyBoolean());
        }

        @Test
        public void invalidConnectionObject() {
            PersistableNetworkPayload persistableNetworkPayload = new PersistableNetworkPayloadStub(true);
            NetworkEnvelope envelope = new AddPersistableNetworkPayloadMessage(persistableNetworkPayload);

            Connection mockedConnection = mock(Connection.class);
            when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.empty());

            this.testState.mockedStorage.onMessage(envelope, mockedConnection);

            verify(this.testState.appendOnlyDataStoreListener, never()).onAdded(any(PersistableNetworkPayload.class));
            verify(this.testState.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), any(NodeAddress.class), eq(null), anyBoolean());
        }
    }


    /*
     * Run each test case through all 4 entry points to validate the correct behavior:
     * 1. addPersistableNetworkPayloadFromInitialRequest()
     * 2. addPersistableNetworkPayload(reBroadcast=false)
     * 3. addPersistableNetworkPayload(reBroadcast=true)
     * 4. onMessage()
     */
    @RunWith(Parameterized.class)
    public abstract static class AddPersistableNetworkPayloadTest {
        TestState testState;

        @Parameterized.Parameter(0)
        public TestCase testCase;

        @Parameterized.Parameter(1)
        public boolean allowBroadcast;

        @Parameterized.Parameter(2)
        public boolean reBroadcast;

        @Parameterized.Parameter(3)
        public boolean checkDate;

        PersistableNetworkPayload persistableNetworkPayload;

        abstract PersistableNetworkPayload createInstance();

        enum TestCase {
            PUBLIC_API,
            ON_MESSAGE,
            INIT,
        }

        boolean expectBroadcastOnStateChange() {
            return this.testCase != TestCase.INIT;
        }

        boolean expectedIsDataOwner() {
            return this.testCase == TestCase.PUBLIC_API;
        }

        void doAddAndVerify(PersistableNetworkPayload persistableNetworkPayload, boolean expectedReturnValue, boolean expectedStateChange) {
            SavedTestState beforeState = new SavedTestState(this.testState, persistableNetworkPayload);

            if (this.testCase == TestCase.INIT) {
                Assert.assertEquals(expectedReturnValue, this.testState.mockedStorage.addPersistableNetworkPayloadFromInitialRequest(persistableNetworkPayload));
            } else if (this.testCase == TestCase.PUBLIC_API) {
                Assert.assertEquals(expectedReturnValue,
                        this.testState.mockedStorage.addPersistableNetworkPayload(persistableNetworkPayload, getTestNodeAddress(), true, this.allowBroadcast, this.reBroadcast, this.checkDate));
            } else { // onMessage
                Connection mockedConnection = mock(Connection.class);
                when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

                testState.mockedStorage.onMessage(new AddPersistableNetworkPayloadMessage(persistableNetworkPayload), mockedConnection);
            }

            verifyPersistableAdd(this.testState, beforeState, persistableNetworkPayload, expectedStateChange, this.expectBroadcastOnStateChange(), this.expectedIsDataOwner());
        }

        @Before
        public void setup() {
            this.persistableNetworkPayload = this.createInstance();

            this.testState = new TestState();
        }

        @Parameterized.Parameters(name = "{index}: Test with TestCase={0} allowBroadcast={1} reBroadcast={2} checkDate={3}")
        public static Collection<Object[]> data() {
            List<Object[]> data = new ArrayList<>();

            // Init doesn't use other parameters
            data.add(new Object[] { TestCase.INIT, false, false, false });

            // onMessage doesn't use other parameters
            data.add(new Object[] { TestCase.ON_MESSAGE, false, false, false });

            // Client API uses two permutations
            // Normal path
            data.add(new Object[] { TestCase.PUBLIC_API, true, true, false });

            // Refresh path
            data.add(new Object[] { TestCase.PUBLIC_API, true, false, false });

            return data;
        }

        @Test
        public void addPersistableNetworkPayload() {
            // First add should succeed regardless of parameters
            doAddAndVerify(this.persistableNetworkPayload, true, true);
        }

        @Test
        public void addPersistableNetworkPayloadDuplicate() {
            doAddAndVerify(this.persistableNetworkPayload, true, true);

            // Second call only succeeds if reBroadcast was set or we are adding through the init
            // path which just overwrites
            boolean expectedReturnValue = this.reBroadcast || this.testCase == TestCase.INIT;
            doAddAndVerify(this.persistableNetworkPayload, expectedReturnValue, false);
        }
    }

    public static class AddPersistableNetworkPayloadStubTest extends AddPersistableNetworkPayloadTest {
        @Override
        PersistableNetworkPayloadStub createInstance() {
            return new PersistableNetworkPayloadStub(true);
        }

        @Test
        public void invalidHash() {
            PersistableNetworkPayload persistableNetworkPayload = new PersistableNetworkPayloadStub(false);

            doAddAndVerify(persistableNetworkPayload, false, false);
        }
    }

    public static class AddPersistableDateTolerantPayloadTest extends AddPersistableNetworkPayloadTest {

        @Override
        DateTolerantPayloadStub createInstance() {
            return new DateTolerantPayloadStub(true);

        }

        @Test
        public void outOfTolerance() {
            PersistableNetworkPayload persistableNetworkPayload = new DateTolerantPayloadStub(false);

            // The onMessage path checks for tolerance
            boolean expectedReturn = this.testCase != TestCase.ON_MESSAGE;

            doAddAndVerify(persistableNetworkPayload, expectedReturn, expectedReturn);
        }
    }

    /*
     * Run each test through both entry points to validate the correct behavior:
     * 1. Client API [addProtectedStorageEntry(), refreshTTL(), remove()]
     * 2. onMessage() [AddDataMessage, RefreshOfferMessage, RemoveDataMessage]
     *
     * These Base tests do not handle the mailbox case. Those are found in the MailboxPayloadTest subclass that
     * extends these tests to reuse the common test cases.
     */
    @RunWith(Parameterized.class)
    abstract public static class ProtectedStorageEntryTestBase {
        TestState testState;
        Class<? extends ProtectedStorageEntry> entryClass;

        protected abstract ProtectedStoragePayload createInstance(KeyPair payloadOwnerKeys);
        protected abstract Class<? extends ProtectedStorageEntry> getEntryClass();

        // Used for tests of ProtectedStorageEntry and subclasses
        private ProtectedStoragePayload protectedStoragePayload;
        KeyPair payloadOwnerKeys;

        @Parameterized.Parameter(0)
        public boolean useMessageHandler;

        boolean expectIsDataOwner() {
            // The onMessage handler variant should always broadcast with isDataOwner == false
            // The Client API should always broadcast with isDataOwner == true
            return !useMessageHandler;
        }

        @Parameterized.Parameters(name = "{index}: Test with useMessageHandler={0}")
        public static Collection<Object[]> data() {
            List<Object[]> data = new ArrayList<>();

            boolean[] vals = new boolean[]{true, false};

            for (boolean useMessageHandler : vals)
                data.add(new Object[]{useMessageHandler});

            return data;
        }

        @Before
        public void setUp() throws CryptoException, NoSuchAlgorithmException {
            this.testState = new TestState();

            this.payloadOwnerKeys = TestUtils.generateKeyPair();
            this.protectedStoragePayload = createInstance(this.payloadOwnerKeys);
            this.entryClass = this.getEntryClass();
        }

        boolean doRemove(ProtectedStorageEntry entry) {
            if (this.useMessageHandler) {
                Connection mockedConnection = mock(Connection.class);
                when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

                testState.mockedStorage.onMessage(new RemoveDataMessage(entry), mockedConnection);

                return true;
            } else {
                // XXX: All callers just pass in true, a future patch can remove the argument.
                return testState.mockedStorage.remove(entry, getTestNodeAddress(), true);
            }
        }

        boolean doAdd(ProtectedStorageEntry protectedStorageEntry) {
            if (this.useMessageHandler) {
                Connection mockedConnection = mock(Connection.class);
                when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

                testState.mockedStorage.onMessage(new AddDataMessage(protectedStorageEntry), mockedConnection);

                return true;
            } else {
                // XXX: All external callers just pass in true for isDataOwner and allowBroadcast a future patch can
                // remove the argument.
                return this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry,
                        getTestNodeAddress(), null, true);
            }
        }

        boolean doRefreshTTL(RefreshOfferMessage refreshOfferMessage) {
            if (this.useMessageHandler) {
                Connection mockedConnection = mock(Connection.class);
                when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

                testState.mockedStorage.onMessage(refreshOfferMessage, mockedConnection);

                return true;
            } else {
                // XXX: All external callers just pass in true for isDataOwner a future patch can remove the argument.
                return this.testState.mockedStorage.refreshTTL(refreshOfferMessage, getTestNodeAddress(), true);
            }
        }

        ProtectedStorageEntry getProtectedStorageEntryForAdd(int sequenceNumber, boolean validForAdd, boolean matchesRelevantPubKey) {
            ProtectedStorageEntry stub = mock(this.entryClass);
            when(stub.getOwnerPubKey()).thenReturn(this.payloadOwnerKeys.getPublic());
            when(stub.isValidForAddOperation()).thenReturn(validForAdd);
            when(stub.matchesRelevantPubKey(any(ProtectedStorageEntry.class))).thenReturn(matchesRelevantPubKey);
            when(stub.getSequenceNumber()).thenReturn(sequenceNumber);
            when(stub.getProtectedStoragePayload()).thenReturn(this.protectedStoragePayload);

            return stub;
        }

        // Return a ProtectedStorageEntry that will pass all validity checks for add.
        ProtectedStorageEntry getProtectedStorageEntryForAdd(int sequenceNumber) {
            return getProtectedStorageEntryForAdd(sequenceNumber, true, true);
        }

        // Return a ProtectedStorageEntry that will pass all validity checks for remove.
        ProtectedStorageEntry getProtectedStorageEntryForRemove(int sequenceNumber, boolean validForRemove, boolean matchesRelevantPubKey) {
            ProtectedStorageEntry stub = mock(this.entryClass);
            when(stub.getOwnerPubKey()).thenReturn(this.payloadOwnerKeys.getPublic());
            when(stub.isValidForRemoveOperation()).thenReturn(validForRemove);
            when(stub.matchesRelevantPubKey(any(ProtectedStorageEntry.class))).thenReturn(matchesRelevantPubKey);
            when(stub.getSequenceNumber()).thenReturn(sequenceNumber);
            when(stub.getProtectedStoragePayload()).thenReturn(this.protectedStoragePayload);

            return stub;
        }

        ProtectedStorageEntry getProtectedStorageEntryForRemove(int sequenceNumber) {
            return getProtectedStorageEntryForRemove(sequenceNumber, true, true);
        }

        void doProtectedStorageAddAndVerify(ProtectedStorageEntry protectedStorageEntry,
                                            boolean expectedReturnValue,
                                            boolean expectedStateChange) {

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);

            boolean addResult = this.doAdd(protectedStorageEntry);

            if (!this.useMessageHandler)
                Assert.assertEquals(expectedReturnValue, addResult);

            verifyProtectedStorageAdd(this.testState, beforeState, protectedStorageEntry, expectedStateChange, this.expectIsDataOwner());
        }

        void doProtectedStorageRemoveAndVerify(ProtectedStorageEntry entry,
                                               boolean expectedReturnValue,
                                               boolean expectInternalStateChange) {

            SavedTestState beforeState = new SavedTestState(this.testState, entry);

            boolean addResult = this.doRemove(entry);

            if (!this.useMessageHandler)
                Assert.assertEquals(expectedReturnValue, addResult);

            verifyProtectedStorageRemove(this.testState, beforeState, entry, expectInternalStateChange, true, true, this.expectIsDataOwner());
        }

        /// Valid Add Tests (isValidForAdd() and matchesRelevantPubKey() return true)
        @Test
        public void addProtectedStorageEntry() {

            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);
        }

        // TESTCASE: Adding duplicate payload w/ same sequence number
        @Test
        public void addProtectedStorageEntry_duplicateSeqNrGt0() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Adding duplicate payload w/ 0 sequence number (special branch in code for logging)
        @Test
        public void addProtectedStorageEntry_duplicateSeqNrEq0() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(0);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Adding duplicate payload for w/ lower sequence number
        @Test
        public void addProtectedStorageEntry_lowerSeqNr() {
            ProtectedStorageEntry entryForAdd2 = this.getProtectedStorageEntryForAdd(2);
            ProtectedStorageEntry entryForAdd1 = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd2, true, true);
            doProtectedStorageAddAndVerify(entryForAdd1, false, false);
        }

        // TESTCASE: Adding duplicate payload for w/ greater sequence number
        @Test
        public void addProtectedStorageEntry_greaterSeqNr() {
            ProtectedStorageEntry entryForAdd2 = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForAdd1 = this.getProtectedStorageEntryForAdd(2);
            doProtectedStorageAddAndVerify(entryForAdd2, true, true);
            doProtectedStorageAddAndVerify(entryForAdd1, true, true);
        }

        // TESTCASE: Add w/ same sequence number after remove of sequence number
        // Regression test for old remove() behavior that succeeded if add.seq# == remove.seq#
        @Test
        public void addProtectectedStorageEntry_afterRemoveSameSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);

            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // Invalid add tests (isValidForAddOperation() || matchesRelevantPubKey()) returns false

        // TESTCASE: Add fails if Entry is not valid for add
        @Test
        public void addProtectedStorageEntry_EntryNotisValidForAddOperation() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1, false, true);
            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Add fails if Entry metadata does not match existing Entry
        @Test
        public void addProtectedStorageEntry_EntryNotmatchesRelevantPubKey() {
            // Add a valid entry
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            // Add an entry where metadata is different from first add, but otherwise is valid
            entryForAdd = this.getProtectedStorageEntryForAdd(2, true, false);
            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Add fails if Entry metadata does not match existing Entry and is not valid for add
        @Test
        public void addProtectedStorageEntry_EntryNotmatchesRelevantPubKeyNotisValidForAddOperation() {
            // Add a valid entry
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            // Add an entry where entry is not valid and metadata is different from first add
            entryForAdd = this.getProtectedStorageEntryForAdd(2, false, false);
            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Entry signature does not match entry owner
        @Ignore // Covered in ProtectedStorageEntryTest::isValidForAddOperation_BadSignature
        @Test
        public void addProtectedStorageEntry_EntrySignatureDoesntMatchEntryOwner() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(2);

            entryForAdd.updateSignature(new byte[] { 0 });
            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Payload owner and entry owner are not compatible for add operation
        @Test
        @Ignore // Covered in ProtectedStorageEntryTest::isValidForAddOperation_Mismatch
        public void addProtectedStorageEntry_payloadOwnerEntryOwnerNotCompatible() throws NoSuchAlgorithmException, CryptoException {
            KeyPair notOwner = TestUtils.generateKeyPair();

            // For standard ProtectedStorageEntrys the entry owner must match the payload owner for adds
            ProtectedStorageEntry entryForAdd = buildProtectedStorageEntry(
                    this.protectedStoragePayload, notOwner, notOwner, 1, this.testState.clockFake);

            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        /// Valid remove tests (isValidForRemove() and matchesRelevantPubKey() return true)

        // TESTCASE: Removing an item after successfully added (remove seq # == add seq #)
        @Test
        public void remove_seqNrEqAddSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Removing an item after successfully added (remove seq # > add seq #)
        @Test
        public void remove_seqNrGtAddSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true);
        }

        // TESTCASE: Removing an item before it was added
        @Test
        public void remove_notExists() {
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);

            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Removing an item after successfully adding (remove seq # < add seq #)
        @Test
        public void remove_seqNrLessAddSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(2);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Removing an item after successfully added (invalid remove entry signature)
        @Ignore // Covered in ProtectedStorageEntryTest::isValidForRemoveOperation_BadSignature
        @Test
        public void remove_invalidEntrySig() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);
            entryForRemove.updateSignature(new byte[] { 0 });
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Payload owner and entry owner are not compatible for remove operation
        @Test
        @Ignore // Covered in ProtectedStorageEntryTest::isValidForRemoveOperation_Mismatch
        public void remove_payloadOwnerEntryOwnerNotCompatible() throws NoSuchAlgorithmException, CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            KeyPair notOwner = TestUtils.generateKeyPair();

            // For standard ProtectedStorageEntrys the entry owner must match the payload owner for removes
            ProtectedStorageEntry entryForRemove = buildProtectedStorageEntry(
                    this.protectedStoragePayload, notOwner, notOwner, 2, this.testState.clockFake);

            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Add after removed (same seq #)
        @Test
        public void add_afterRemoveSameSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true);

            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Add after removed (greater seq #)
        @Test
        public void add_afterRemoveGreaterSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true);

            entryForAdd = this.getProtectedStorageEntryForAdd(3);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);
        }

        /// Invalid remove tests (isValidForRemoveOperation() || matchesRelevantPubKey()) returns false

        // TESTCASE: Remove fails if Entry isn't valid for remove
        @Test
        public void remove_EntryNotisValidForRemoveOperation() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2, false, true);
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Remove fails if Entry is valid for remove, but metadata doesn't match remove target
        @Test
        public void remove_EntryNotmatchesRelevantPubKey() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2, true, false);
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Remove fails if Entry is not valid for remove and metadata doesn't match remove target
        @Test
        public void remove_EntryNotisValidForRemoveOperationNotmatchesRelevantPubKey() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2, false, false);
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }


        // TESTCASE: Add after removed (lower seq #)
        @Test
        public void add_afterRemoveLessSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(2);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(3);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true);

            entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Received remove for nonexistent item that was later received
        // XXXBUGXXX: There may be cases where removes are reordered with adds (remove during pending GetDataRequest?).
        // The proper behavior may be to not add the late messages, but the current code will successfully add them
        // even in the AddOncePayload (mailbox) case.
        @Test
        public void remove_lateAdd() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);

            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);

            // should be (false, false)
            doProtectedStorageAddAndVerify(entryForAdd, true, true);
        }
    }

    // Runs the ProtectedStorageEntryTestBase tests against a basic (no marker interfaces) ProtectedStoragePayload
    public static class ProtectedStorageEntryTest extends ProtectedStorageEntryTestBase {

        @Override
        protected ProtectedStoragePayload createInstance(KeyPair payloadOwnerKeys) {
            return new ProtectedStoragePayloadStub(payloadOwnerKeys.getPublic());
        }

        @Override
        protected Class<ProtectedStorageEntry> getEntryClass() {
            return ProtectedStorageEntry.class;
        }

        RefreshOfferMessage buildRefreshOfferMessage(ProtectedStorageEntry protectedStorageEntry, KeyPair ownerKeys, int sequenceNumber) throws CryptoException {
            return P2PDataStorageTest.buildRefreshOfferMessage(protectedStorageEntry.getProtectedStoragePayload(), ownerKeys, sequenceNumber);
        }

        void doRefreshTTLAndVerify(RefreshOfferMessage refreshOfferMessage, boolean expectedReturnValue, boolean expectStateChange) {
            SavedTestState beforeState = new SavedTestState(this.testState, refreshOfferMessage);

            boolean returnValue = this.doRefreshTTL(refreshOfferMessage);

            if (!this.useMessageHandler)
                Assert.assertEquals(expectedReturnValue, returnValue);

            verifyRefreshTTL(this.testState, beforeState, refreshOfferMessage, expectStateChange, this.expectIsDataOwner());
        }

        // TESTCASE: Refresh an entry that doesn't exist
        @Test
        public void refreshTTL_noExist() throws CryptoException {
            ProtectedStorageEntry entry = this.getProtectedStorageEntryForAdd(1);

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys,1), false, false);
        }

        // TESTCASE: Refresh an entry where seq # is equal to last seq # seen
        @Test
        public void refreshTTL_existingEntry() throws CryptoException {
            ProtectedStorageEntry entry = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entry, true, true);

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys,1), false, false);
        }

        // TESTCASE: Duplicate refresh message (same seq #)
        @Test
        public void refreshTTL_duplicateRefreshSeqNrEqual() throws CryptoException {
            ProtectedStorageEntry entry = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entry, true, true);

            this.testState.incrementClock();

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys, 2), true, true);

            this.testState.incrementClock();

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys, 2), false, false);
        }

        // TESTCASE: Duplicate refresh message (greater seq #)
        @Test
        public void refreshTTL_duplicateRefreshSeqNrGreater() throws CryptoException {
            ProtectedStorageEntry entry = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entry, true, true);

            this.testState.incrementClock();

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys,2), true, true);

            this.testState.incrementClock();

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys,3), true, true);
        }

        // TESTCASE: Duplicate refresh message (lower seq #)
        @Test
        public void refreshTTL_duplicateRefreshSeqNrLower() throws CryptoException {
            ProtectedStorageEntry entry = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entry, true, true);

            this.testState.incrementClock();

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys,3), true, true);

            this.testState.incrementClock();

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys,2), false, false);
        }

        // TESTCASE: Refresh previously removed entry
        @Test
        public void refreshTTL_refreshAfterRemove() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true);

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entryForAdd, this.payloadOwnerKeys,3), false, false);
        }

        // TESTCASE: Refresh an entry, but owner doesn't match PubKey of original add owner
        @Test
        public void refreshTTL_refreshEntryOwnerOriginalOwnerMismatch() throws CryptoException, NoSuchAlgorithmException {
            ProtectedStorageEntry entry = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entry, true, true);

            KeyPair notOwner = TestUtils.generateKeyPair();
            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, notOwner, 2), false, false);
        }
    }

    // Runs the ProtectedStorageEntryTestBase tests against the PersistablePayload marker class
    public static class PersistableExpirableProtectedStoragePayloadTest extends ProtectedStorageEntryTestBase {
        @Override
        protected ProtectedStoragePayload createInstance(KeyPair payloadOwnerKeys) {
            return new PersistableExpirableProtectedStoragePayload(payloadOwnerKeys.getPublic());
        }

        @Override
        protected Class<ProtectedStorageEntry> getEntryClass() {
            return ProtectedStorageEntry.class;
        }

    }
    /*
      * Runs the ProtectedStorageEntryTestBase tests against the MailboxPayload. The rules for add/remove are different
      * so a few of the functions used in common tests are overridden so the test cases can be deduplicated. Additional
      * tests that just apply to the mailbox case are also added below.
     */
    public static class MailboxPayloadTest extends ProtectedStorageEntryTestBase {

        private KeyPair senderKeys;
        private KeyPair receiverKeys;

        @Override
        @Before
        public void setUp() throws CryptoException, NoSuchAlgorithmException {
            super.setUp();

            this.senderKeys = TestUtils.generateKeyPair();
            this.receiverKeys = TestUtils.generateKeyPair();

            // Deep in the bowels of protobuf we grab the messageID from the version module. This is required to hash the
            // full MailboxStoragePayload so make sure it is initialized.
            Version.setBaseCryptoNetworkId(1);
        }

        @Override
        boolean doRemove(ProtectedStorageEntry entry) {
            if (this.useMessageHandler) {
                Connection mockedConnection = mock(Connection.class);
                when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

                testState.mockedStorage.onMessage(new RemoveMailboxDataMessage((ProtectedMailboxStorageEntry) entry), mockedConnection);

                return true;
            } else {
                // XXX: All external callers just pass in true, a future patch can remove the argument.
                return testState.mockedStorage.remove(entry, getTestNodeAddress(), true);
            }
        }

        @Override
        protected ProtectedStoragePayload createInstance(KeyPair payloadOwnerKeys) {
            // Need to be able to take the hash which leverages protobuf Messages
            protobuf.StoragePayload messageMock = mock(protobuf.StoragePayload.class);
            when(messageMock.toByteArray()).thenReturn(Sig.getPublicKeyBytes(this.payloadOwnerKeys.getPublic()));

            MailboxStoragePayload payloadMock = mock(MailboxStoragePayload.class);
            when(payloadMock.toProtoMessage()).thenReturn(messageMock);

            return payloadMock;
        }

        protected Class<ProtectedMailboxStorageEntry> getEntryClass() {
            return ProtectedMailboxStorageEntry.class;
        }

        // TESTCASE: adding a MailboxStoragePayload wrapped in a ProtectedStorageEntry owned by the receiver should fail
        @Test
        @Ignore // Covered in ProtectedStorageEntryTest::isValidForAddOperation_invalidMailboxPayloadReceiver
        public void addProtectedStorageEntry_badWrappingReceiverEntry() throws CryptoException, NoSuchAlgorithmException {
            KeyPair senderKeys = TestUtils.generateKeyPair();
            KeyPair receiverKeys = TestUtils.generateKeyPair();

            ProtectedStorageEntry entryForAdd = buildProtectedStorageEntry(
                    buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic()), receiverKeys, receiverKeys, 1, this.testState.clockFake);

            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: adding a MailboxStoragePayload wrapped in a ProtectedStorageEntry owned by the sender should fail
        // XXXBUGXXX Miswrapped MailboxStoragePayload objects go through non-mailbox validation. This circumvents the
        // Entry.ownerPubKey == Payload.senderPubKeyForAddOperation checks.
        @Test
        @Ignore // Covered in ProtectedStorageEntryTest::isValidForAddOperation_invalidMailboxPayloadSender
        public void addProtectedStorageEntry_badWrappingSenderEntry() throws CryptoException, NoSuchAlgorithmException {
            KeyPair senderKeys = TestUtils.generateKeyPair();
            KeyPair receiverKeys = TestUtils.generateKeyPair();

            ProtectedStorageEntry entryForAdd = buildProtectedStorageEntry(
                    buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic()), senderKeys, senderKeys, 1, this.testState.clockFake);

            // should be (false, false)
            doProtectedStorageAddAndVerify(entryForAdd, true, true);
        }

        // TESTCASE: removing a MailboxStoragePayload wrapped in a ProtectedStorageEntry owned by the receiver should fail
        // XXX: The reason this fails correctly is extremely subtle. checkIfStoredDataPubKeyMatchesNewDataPubKey()
        // in the non-mailbox path sees the the Entry owner changed from the initial add request and fails. If we see
        // this invalid wrapper we should fail more explicitly.
        @Test
        @Ignore // Covered in ProtectedStorageEntryTest::isValidForRemoveOperation_invalidMailboxPayloadReceiver
        public void remove_badWrappingReceiverEntry() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = buildProtectedStorageEntry(
                    buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic()), receiverKeys, receiverKeys, 2, this.testState.clockFake);

            SavedTestState beforeState = new SavedTestState(this.testState, entryForRemove);

            boolean addResult = super.doRemove(entryForRemove);

            if (!this.useMessageHandler)
                Assert.assertEquals(false, addResult);

            verifyProtectedStorageRemove(this.testState, beforeState, entryForRemove, false, false, false, this.expectIsDataOwner());
        }

        // TESTCASE: removing a MailboxStoragePayload wrapped in a ProtectedStorageEntry owned by the sender should fail
        @Test
        @Ignore // Covered in ProtectedStorageEntryTest::isValidForRemoveOperation_invalidMailboxPayloadSender
        public void remove_badWrappingSenderEntry() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = buildProtectedStorageEntry(
                    buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic()), senderKeys, senderKeys, 2, this.testState.clockFake);

            SavedTestState beforeState = new SavedTestState(this.testState, entryForRemove);

            boolean addResult = super.doRemove(entryForRemove);

            // should be (false, false)
            if (!this.useMessageHandler)
                Assert.assertEquals(true, addResult);

            verifyProtectedStorageRemove(this.testState, beforeState, entryForRemove, true, true, true, this.expectIsDataOwner());
        }

        // TESTCASE: Adding fails when Entry owner is different from sender
        @Test
        @Ignore // Covered in ProtectedMailboxStorageEntryTest::isValidForAddOperation_EntryOwnerPayloadReceiverMismatch
        public void addProtectedStorageEntry_payloadOwnerEntryOwnerNotCompatible() throws CryptoException, NoSuchAlgorithmException {
            KeyPair notSender = TestUtils.generateKeyPair();

            ProtectedStorageEntry entryForAdd = buildProtectedMailboxStorageEntry(notSender.getPublic(), receiverKeys.getPublic(), senderKeys.getPrivate(), senderKeys.getPublic(), receiverKeys.getPublic(), 1, this.testState.clockFake);

            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Adding MailboxStoragePayload when Entry owner is different than sender does not overwrite existing payload
        @Ignore // Covered in ProtectedMailboxStorageEntryTest::isValidForAddOperation_EntryOwnerPayloadReceiverMismatch
        @Test
        public void addProtectedStorageEntry_payloadOwnerEntryOwnerNotCompatibleNoSideEffect() throws CryptoException, NoSuchAlgorithmException {
            KeyPair notSender = TestUtils.generateKeyPair();

            doProtectedStorageAddAndVerify(this.getProtectedStorageEntryForAdd(1), true, true);

            ProtectedStorageEntry invalidEntryForAdd = buildProtectedMailboxStorageEntry(notSender.getPublic(), receiverKeys.getPublic(), senderKeys.getPrivate(), senderKeys.getPublic(), receiverKeys.getPublic(), 1, this.testState.clockFake);

            doProtectedStorageAddAndVerify(invalidEntryForAdd, false, false);
        }

        // TESTCASE: Payload owner and entry owner are not compatible for remove operation
        @Ignore // Covered in ProtectedMailboxStorageEntryTest::validForRemoveEntryOwnerPayloadOwnerMismatch
        @Test
        public void remove_payloadOwnerEntryOwnerNotCompatible() throws NoSuchAlgorithmException, CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            KeyPair notReceiver = TestUtils.generateKeyPair();

            ProtectedStorageEntry entryForRemove =  buildProtectedMailboxStorageEntry(senderKeys.getPublic(), receiverKeys.getPublic(), notReceiver.getPrivate(), notReceiver.getPublic(), receiverKeys.getPublic(), 2, this.testState.clockFake);

            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Payload owner and entry.receiversPubKey are not compatible for remove operation
        @Ignore // Covered in ProtectedMailboxStorageEntryTest::isValidForRemoveOperation_ReceiversPubKeyMismatch
        @Test
        public void remove_payloadOwnerEntryReceiversPubKeyNotCompatible() throws NoSuchAlgorithmException, CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            KeyPair notSender = TestUtils.generateKeyPair();

            ProtectedStorageEntry entryForRemove = buildProtectedMailboxStorageEntry(senderKeys.getPublic(), receiverKeys.getPublic(), receiverKeys.getPrivate(), receiverKeys.getPublic(), notSender.getPublic(), 2, this.testState.clockFake);

            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: receiversPubKey changed between add and remove
        // TODO: Current code does not check receiversPubKey on add() (payload.ownersPubKey == entry.receiversPubKey)
        // Can the code just check against payload.ownersPubKey in all cases and deprecate Entry.receiversPubKey?
        @Ignore // Covered in ProtectedMailboxStorageEntryTest::isValidForAddOperation_EntryReceiverPayloadReceiverMismatch
        @Test
        public void remove_receiversPubKeyChanged() throws NoSuchAlgorithmException, CryptoException {
            KeyPair otherKeys = TestUtils.generateKeyPair();

            // Add an entry that has an invalid Entry.receiversPubKey. Unfortunately, this succeeds right now.
            ProtectedStorageEntry entryForAdd = buildProtectedMailboxStorageEntry(senderKeys.getPublic(), receiverKeys.getPublic(), senderKeys.getPrivate(), senderKeys.getPublic(), otherKeys.getPublic(), 1, this.testState.clockFake);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            doProtectedStorageRemoveAndVerify(this.getProtectedStorageEntryForRemove(2), false, false);
        }

        // TESTCASE: Add after removed when add-once required (greater seq #)
        @Override
        @Test
        public void add_afterRemoveGreaterSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true);

            entryForAdd = this.getProtectedStorageEntryForAdd(3);
            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }
    }

    public static class BuildEntryAPITests {
        private TestState testState;

        @Before
        public void setUp() {
            this.testState = new TestState();

            // Deep in the bowels of protobuf we grab the messageID from the version module. This is required to hash the
            // full MailboxStoragePayload so make sure it is initialized.
            Version.setBaseCryptoNetworkId(1);
        }

        // TESTCASE: Adding an entry from the getProtectedStorageEntry API correctly adds the item
        @Test
        public void getProtectedStorageEntry_NoExist() throws NoSuchAlgorithmException, CryptoException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();

            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys.getPublic());
            ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);
            Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true));

            verifyProtectedStorageAdd(this.testState, beforeState, protectedStorageEntry, true, true);
        }

        // TESTCASE: Adding an entry from the getProtectedStorageEntry API of an existing item correctly updates the item
        @Test
        public void getProtectedStorageEntry() throws NoSuchAlgorithmException, CryptoException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();

            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys.getPublic());
            ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);

            Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true));

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);
            protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true);

            verifyProtectedStorageAdd(this.testState, beforeState, protectedStorageEntry, true, true);
        }

        // TESTCASE: Adding an entry from the getProtectedStorageEntry API of an existing item (added from onMessage path) correctly updates the item
        @Test
        public void getProtectedStorageEntry_FirstOnMessageSecondAPI() throws NoSuchAlgorithmException, CryptoException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();

            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys.getPublic());
            ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);

            Connection mockedConnection = mock(Connection.class);
            when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

            this.testState.mockedStorage.onMessage(new AddDataMessage(protectedStorageEntry), mockedConnection);

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);
            protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true));

            verifyProtectedStorageAdd(this.testState, beforeState, protectedStorageEntry, true, true);
        }

        // TESTCASE: Updating an entry from the getRefreshTTLMessage API correctly errors if the item hasn't been seen
        @Test
        public void getRefreshTTLMessage_NoExists() throws NoSuchAlgorithmException, CryptoException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();

            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys.getPublic());

            RefreshOfferMessage refreshOfferMessage = this.testState.mockedStorage.getRefreshTTLMessage(protectedStoragePayload, ownerKeys);

            SavedTestState beforeState = new SavedTestState(this.testState, refreshOfferMessage);
            Assert.assertFalse(this.testState.mockedStorage.refreshTTL(refreshOfferMessage, getTestNodeAddress(), true));

            verifyRefreshTTL(this.testState, beforeState, refreshOfferMessage, false, true);
        }

        // TESTCASE: Updating an entry from the getRefreshTTLMessage API correctly "refreshes" the item
        @Test
        public void getRefreshTTLMessage() throws NoSuchAlgorithmException, CryptoException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();

            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys.getPublic());
            ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true);

            RefreshOfferMessage refreshOfferMessage = this.testState.mockedStorage.getRefreshTTLMessage(protectedStoragePayload, ownerKeys);
            this.testState.mockedStorage.refreshTTL(refreshOfferMessage, getTestNodeAddress(), true);

            refreshOfferMessage = this.testState.mockedStorage.getRefreshTTLMessage(protectedStoragePayload, ownerKeys);

            this.testState.incrementClock();

            SavedTestState beforeState = new SavedTestState(this.testState, refreshOfferMessage);
            Assert.assertTrue(this.testState.mockedStorage.refreshTTL(refreshOfferMessage, getTestNodeAddress(), true));

            verifyRefreshTTL(this.testState, beforeState, refreshOfferMessage, true, true);
        }

        // TESTCASE: Updating an entry from the getRefreshTTLMessage API correctly "refreshes" the item when it was originally added from onMessage path
        @Test
        public void getRefreshTTLMessage_FirstOnMessageSecondAPI() throws NoSuchAlgorithmException, CryptoException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();

            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys.getPublic());
            ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true);

            Connection mockedConnection = mock(Connection.class);
            when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

            this.testState.mockedStorage.onMessage(new AddDataMessage(protectedStorageEntry), mockedConnection);

            RefreshOfferMessage refreshOfferMessage = this.testState.mockedStorage.getRefreshTTLMessage(protectedStoragePayload, ownerKeys);

            this.testState.incrementClock();

            SavedTestState beforeState = new SavedTestState(this.testState, refreshOfferMessage);
            Assert.assertTrue(this.testState.mockedStorage.refreshTTL(refreshOfferMessage, getTestNodeAddress(), true));

            verifyRefreshTTL(this.testState, beforeState, refreshOfferMessage, true, true);
        }

        // TESTCASE: Removing a non-existent mailbox entry from the getMailboxDataWithSignedSeqNr API
        @Test
        public void getMailboxDataWithSignedSeqNr_RemoveNoExist() throws NoSuchAlgorithmException, CryptoException {
            KeyPair receiverKeys = TestUtils.generateKeyPair();
            KeyPair senderKeys = TestUtils.generateKeyPair();

            MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());

            ProtectedMailboxStorageEntry protectedMailboxStorageEntry =
                    this.testState.mockedStorage.getMailboxDataWithSignedSeqNr(mailboxStoragePayload, receiverKeys, receiverKeys.getPublic());

            SavedTestState beforeState = new SavedTestState(this.testState, protectedMailboxStorageEntry);
            Assert.assertFalse(this.testState.mockedStorage.remove(protectedMailboxStorageEntry, getTestNodeAddress(), true));

            verifyProtectedStorageRemove(this.testState, beforeState, protectedMailboxStorageEntry, false, true, true, true);
        }

        // TESTCASE: Adding, then removing a mailbox message from the getMailboxDataWithSignedSeqNr API
        @Test
        public void getMailboxDataWithSignedSeqNr_AddThenRemove() throws NoSuchAlgorithmException, CryptoException {
            KeyPair receiverKeys = TestUtils.generateKeyPair();
            KeyPair senderKeys = TestUtils.generateKeyPair();

            MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());

            ProtectedMailboxStorageEntry protectedMailboxStorageEntry =
                this.testState.mockedStorage.getMailboxDataWithSignedSeqNr(mailboxStoragePayload, senderKeys, receiverKeys.getPublic());

            Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedMailboxStorageEntry, getTestNodeAddress(), null, true));

            protectedMailboxStorageEntry =
                    this.testState.mockedStorage.getMailboxDataWithSignedSeqNr(mailboxStoragePayload, receiverKeys, receiverKeys.getPublic());

            SavedTestState beforeState = new SavedTestState(this.testState, protectedMailboxStorageEntry);
            Assert.assertTrue(this.testState.mockedStorage.remove(protectedMailboxStorageEntry, getTestNodeAddress(), true));

            verifyProtectedStorageRemove(this.testState, beforeState, protectedMailboxStorageEntry, true, true, true,true);
        }

        // TESTCASE: Removing a mailbox message that was added from the onMessage handler
        @Test
        public void getMailboxDataWithSignedSeqNr_ValidRemoveAddFromMessage() throws NoSuchAlgorithmException, CryptoException {
            KeyPair receiverKeys = TestUtils.generateKeyPair();
            KeyPair senderKeys = TestUtils.generateKeyPair();

            ProtectedStorageEntry protectedStorageEntry =
                    buildProtectedMailboxStorageEntry(senderKeys.getPublic(), receiverKeys.getPublic(), senderKeys.getPrivate(),
                            senderKeys.getPublic(), receiverKeys.getPublic(), 1, this.testState.clockFake);

            Connection mockedConnection = mock(Connection.class);
            when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

            this.testState.mockedStorage.onMessage(new AddDataMessage(protectedStorageEntry), mockedConnection);

            MailboxStoragePayload mailboxStoragePayload = (MailboxStoragePayload) protectedStorageEntry.getProtectedStoragePayload();

            ProtectedMailboxStorageEntry protectedMailboxStorageEntry =
                    this.testState.mockedStorage.getMailboxDataWithSignedSeqNr(mailboxStoragePayload, receiverKeys, receiverKeys.getPublic());

            SavedTestState beforeState = new SavedTestState(this.testState, protectedMailboxStorageEntry);
            Assert.assertTrue(this.testState.mockedStorage.remove(protectedMailboxStorageEntry, getTestNodeAddress(), true));

            verifyProtectedStorageRemove(this.testState, beforeState, protectedMailboxStorageEntry, true, true, true,true);
        }
    }

    public static class DisconnectTest {
        private TestState testState;
        private Connection mockedConnection;

        private static ProtectedStorageEntry populateTestState(TestState testState, long ttl) throws CryptoException, NoSuchAlgorithmException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();
            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys.getPublic(), ttl);

            ProtectedStorageEntry protectedStorageEntry = testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, false);

            return protectedStorageEntry;
        }

        private static void verifyStateAfterDisconnect(TestState currentState, SavedTestState beforeState, boolean wasRemoved, boolean wasTTLReduced) {
            ProtectedStorageEntry protectedStorageEntry = beforeState.protectedStorageEntryBeforeOp;

            P2PDataStorage.ByteArray hashMapHash = P2PDataStorage.get32ByteHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload());

            Assert.assertNotEquals(wasRemoved, currentState.mockedStorage.getMap().containsKey(hashMapHash));

            if (wasRemoved)
                verify(currentState.hashMapChangedListener).onRemoved(protectedStorageEntry);
            else
                verify(currentState.hashMapChangedListener, never()).onRemoved(any(ProtectedStorageEntry.class));

            if (wasTTLReduced)
                Assert.assertTrue(protectedStorageEntry.getCreationTimeStamp() < beforeState.creationTimestampBeforeUpdate);
            else
                Assert.assertEquals(protectedStorageEntry.getCreationTimeStamp(), beforeState.creationTimestampBeforeUpdate);
        }

        @Before
        public void setUp() {
            this.mockedConnection = mock(Connection.class);
            this.testState = new TestState();
        }

        // TESTCASE: Bad peer info
        @Test
        public void peerConnectionUnknown() {
            when(this.mockedConnection.hasPeersNodeAddress()).thenReturn(false);

            this.testState.mockedStorage.onDisconnect(CloseConnectionReason.SOCKET_CLOSED, mockedConnection);
        }

        // TESTCASE: Intended disconnects don't trigger expiration
        @Test
        public void connectionClosedIntended() {
            when(this.mockedConnection.hasPeersNodeAddress()).thenReturn(true);
            this.testState.mockedStorage.onDisconnect(CloseConnectionReason.CLOSE_REQUESTED_BY_PEER, mockedConnection);
        }

        // TESTCASE: Peer NodeAddress unknown
        @Test
        public void connectionClosedSkipsItemsPeerInfoBadState() throws NoSuchAlgorithmException, CryptoException {
            when(this.mockedConnection.hasPeersNodeAddress()).thenReturn(true);
            when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.empty());

            ProtectedStorageEntry protectedStorageEntry = populateTestState(testState, 1);

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);

            this.testState.mockedStorage.onDisconnect(CloseConnectionReason.SOCKET_CLOSED, mockedConnection);

            verifyStateAfterDisconnect(this.testState, beforeState, false, false);
        }

        // TESTCASE: Unintended disconnects reduce the TTL for entrys that match disconnected peer
        @Test
        public void connectionClosedReduceTTL() throws NoSuchAlgorithmException, CryptoException {
            when(this.mockedConnection.hasPeersNodeAddress()).thenReturn(true);
            when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

            ProtectedStorageEntry protectedStorageEntry = populateTestState(testState, TimeUnit.DAYS.toMillis(90));

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);

            this.testState.mockedStorage.onDisconnect(CloseConnectionReason.SOCKET_CLOSED, mockedConnection);

            verifyStateAfterDisconnect(this.testState, beforeState, false, true);
        }

        // TESTCASE: Unintended disconnects don't reduce TTL for entrys that are not from disconnected peer
        @Test
        public void connectionClosedSkipsItemsNotFromPeer() throws NoSuchAlgorithmException, CryptoException {
            when(this.mockedConnection.hasPeersNodeAddress()).thenReturn(true);
            when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(new NodeAddress("notTestNode", 2020)));

            ProtectedStorageEntry protectedStorageEntry = populateTestState(testState, 1);

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);

            this.testState.mockedStorage.onDisconnect(CloseConnectionReason.SOCKET_CLOSED, mockedConnection);

            verifyStateAfterDisconnect(this.testState, beforeState, false, false);
        }

        // TESTCASE: Unintended disconnects expire entrys that match disconnected peer and TTL is low enough for expire
        @Test
        public void connectionClosedReduceTTLAndExpireItemsFromPeer() throws NoSuchAlgorithmException, CryptoException {
            when(this.mockedConnection.hasPeersNodeAddress()).thenReturn(true);
            when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

            ProtectedStorageEntry protectedStorageEntry = populateTestState(testState, 1);

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);

            // Increment the time by 1 hour which will put the protectedStorageState outside TTL
            this.testState.incrementClock();

            this.testState.mockedStorage.onDisconnect(CloseConnectionReason.SOCKET_CLOSED, mockedConnection);

            verifyStateAfterDisconnect(this.testState, beforeState, true, false);
        }
    }

    public static class RemoveExpiredTests {
        TestState testState;

        @Before
        public void setUp() {
            this.testState = new TestState();

            // Deep in the bowels of protobuf we grab the messageID from the version module. This is required to hash the
            // full MailboxStoragePayload so make sure it is initialized.
            Version.setBaseCryptoNetworkId(1);
        }

        // TESTCASE: Correctly skips entries that are not expirable
        @Test
        public void removeExpiredEntries_SkipsNonExpirableEntries() throws NoSuchAlgorithmException, CryptoException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();
            ProtectedStoragePayload protectedStoragePayload = new ProtectedStoragePayloadStub(ownerKeys.getPublic());
            ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true));

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);
            this.testState.mockedStorage.removeExpiredEntries();

            verifyProtectedStorageRemove(this.testState, beforeState, protectedStorageEntry, false, false, false, false);
        }

        // TESTCASE: Correctly skips non-persistable entries that are not expired
        @Test
        public void removeExpiredEntries_SkipNonExpiredExpirableEntries() throws CryptoException, NoSuchAlgorithmException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();
            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys.getPublic());
            ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true));

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);
            this.testState.mockedStorage.removeExpiredEntries();

            verifyProtectedStorageRemove(this.testState, beforeState, protectedStorageEntry, false, false, false, false);
        }

        // TESTCASE: Correctly expires non-persistable entries that are expired
        @Test
        public void removeExpiredEntries_ExpiresExpiredExpirableEntries() throws CryptoException, NoSuchAlgorithmException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();
            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys.getPublic(), 0);
            ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true));

            // Increment the clock by an hour which will cause the Payloads to be outside the TTL range
            this.testState.incrementClock();

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);
            this.testState.mockedStorage.removeExpiredEntries();

            verifyProtectedStorageRemove(this.testState, beforeState, protectedStorageEntry, true, false, false, false);
        }

        // TESTCASE: Correctly skips persistable entries that are not expired
        @Test
        public void removeExpiredEntries_SkipNonExpiredPersistableExpirableEntries() throws CryptoException, NoSuchAlgorithmException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();
            ProtectedStoragePayload protectedStoragePayload = new PersistableExpirableProtectedStoragePayload(ownerKeys.getPublic());
            ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true));

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);
            this.testState.mockedStorage.removeExpiredEntries();

            verifyProtectedStorageRemove(this.testState, beforeState, protectedStorageEntry, false, false, false, false);
        }

        // TESTCASE: Correctly expires persistable entries that are expired
        @Test
        public void removeExpiredEntries_ExpiresExpiredPersistableExpirableEntries() throws CryptoException, NoSuchAlgorithmException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();
            ProtectedStoragePayload protectedStoragePayload = new PersistableExpirableProtectedStoragePayload(ownerKeys.getPublic(), 0);
            ProtectedStorageEntry protectedStorageEntry = testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            Assert.assertTrue(testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true));

            // Increment the clock by an hour which will cause the Payloads to be outside the TTL range
            this.testState.incrementClock();

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);
            this.testState.mockedStorage.removeExpiredEntries();

            verifyProtectedStorageRemove(this.testState, beforeState, protectedStorageEntry, true, false, false, false);
        }

        // TESTCASE: Ensure we try to purge old entries sequence number map when size exceeds the maximum size
        // and that entries less than PURGE_AGE_DAYS remain
        @Test
        public void removeExpiredEntries_PurgeSeqNrMap() throws CryptoException, NoSuchAlgorithmException {
            final int initialClockIncrement = 5;

            // Add 4 entries to our sequence number map that will be purged
            KeyPair purgedOwnerKeys = TestUtils.generateKeyPair();
            ProtectedStoragePayload purgedProtectedStoragePayload = new PersistableExpirableProtectedStoragePayload(purgedOwnerKeys.getPublic(), 0);
            ProtectedStorageEntry purgedProtectedStorageEntry = testState.mockedStorage.getProtectedStorageEntry(purgedProtectedStoragePayload, purgedOwnerKeys);

            Assert.assertTrue(testState.mockedStorage.addProtectedStorageEntry(purgedProtectedStorageEntry, getTestNodeAddress(), null, true));

            for (int i = 0; i < 4; ++i) {
                KeyPair ownerKeys = TestUtils.generateKeyPair();
                ProtectedStoragePayload protectedStoragePayload = new PersistableExpirableProtectedStoragePayload(ownerKeys.getPublic(), 0);
                ProtectedStorageEntry tmpEntry = testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
                Assert.assertTrue(testState.mockedStorage.addProtectedStorageEntry(tmpEntry, getTestNodeAddress(), null, true));
            }

            // Increment the time by 5 days which is less than the purge requirement. This will allow the map to have
            // some values that will be purged and others that will stay.
            this.testState.clockFake.increment(TimeUnit.DAYS.toMillis(initialClockIncrement));

            // Add a final entry that will not be purged
            KeyPair keepOwnerKeys = TestUtils.generateKeyPair();
            ProtectedStoragePayload keepProtectedStoragePayload = new PersistableExpirableProtectedStoragePayload(keepOwnerKeys.getPublic(), 0);
            ProtectedStorageEntry keepProtectedStorageEntry = testState.mockedStorage.getProtectedStorageEntry(keepProtectedStoragePayload, keepOwnerKeys);

            Assert.assertTrue(testState.mockedStorage.addProtectedStorageEntry(keepProtectedStorageEntry, getTestNodeAddress(), null, true));

            // P2PDataStorage::PURGE_AGE_DAYS == 10 days
            // Advance time past it so they will be valid purge targets
            this.testState.clockFake.increment(TimeUnit.DAYS.toMillis(P2PDataStorage.PURGE_AGE_DAYS + 1 - initialClockIncrement));

            // The first entry (11 days old) should be purged
            SavedTestState beforeState = new SavedTestState(this.testState, purgedProtectedStorageEntry);
            this.testState.mockedStorage.removeExpiredEntries();
            verifyProtectedStorageRemove(this.testState, beforeState, purgedProtectedStorageEntry, true, false, false, false);

            // Which means that an addition of a purged entry should succeed.
            beforeState = new SavedTestState(this.testState, purgedProtectedStorageEntry);
            Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(purgedProtectedStorageEntry, getTestNodeAddress(), null, false));
            verifyProtectedStorageAdd(this.testState, beforeState, purgedProtectedStorageEntry, true, false);

            // The second entry (5 days old) should still exist which means trying to add it again should fail.
            beforeState = new SavedTestState(this.testState, keepProtectedStorageEntry);
            Assert.assertFalse(this.testState.mockedStorage.addProtectedStorageEntry(keepProtectedStorageEntry, getTestNodeAddress(), null, false));
            verifyProtectedStorageAdd(this.testState, beforeState, keepProtectedStorageEntry, false, false);
        }
    }
}
