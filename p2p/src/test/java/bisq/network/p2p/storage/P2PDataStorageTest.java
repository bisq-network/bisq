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
import bisq.network.p2p.storage.persistence.ProtectedDataStoreListener;
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

        ExpirableProtectedStoragePayload(KeyPair ownerKeys) {
            super(ownerKeys.getPublic());
            ttl = TimeUnit.DAYS.toMillis(90);
        }

        ExpirableProtectedStoragePayload(KeyPair ownerKeys, long ttl) {
            this(ownerKeys);
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

    // Common state for tests that initializes the P2PDataStore and mocks out the dependencies. Allows
    // shared state verification between all tests.
    static class TestState {
        final P2PDataStorage mockedStorage;
        final Broadcaster mockBroadcaster;

        final AppendOnlyDataStoreListener appendOnlyDataStoreListener;
        final ProtectedDataStoreListener protectedDataStoreListener;
        final HashMapChangedListener hashMapChangedListener;
        final Storage<SequenceNumberMap> mockSeqNrStorage;

        TestState() {
            this.mockBroadcaster = mock(Broadcaster.class);
            this.mockSeqNrStorage = mock(Storage.class);

            this.mockedStorage = new P2PDataStorage(mock(NetworkNode.class),
                    this.mockBroadcaster,
                    new AppendOnlyDataStoreServiceFake(),
                    new ProtectedDataStoreServiceFake(), mock(ResourceDataStoreService.class),
                    this.mockSeqNrStorage, Clock.systemUTC());

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
            int sequenceNumber) throws CryptoException {
        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new P2PDataStorage.DataAndSeqNrPair(protectedStoragePayload, sequenceNumber));
        byte[] signature = Sig.sign(entrySignerKeys.getPrivate(), hashOfDataAndSeqNr);

        return new ProtectedStorageEntry(protectedStoragePayload, entryOwnerKeys.getPublic(), sequenceNumber, signature);
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
            int sequenceNumber) throws CryptoException {

        MailboxStoragePayload payload = buildMailboxStoragePayload(payloadSenderPubKeyForAddOperation, payloadOwnerPubKey);

        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new P2PDataStorage.DataAndSeqNrPair(payload, sequenceNumber));
        byte[] signature = Sig.sign(entrySigner, hashOfDataAndSeqNr);
        return new ProtectedMailboxStorageEntry(payload,
                entryOwnerPubKey, sequenceNumber, signature, entryReceiversPubKey);
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

            verify(currentState.mockBroadcaster).broadcast(any(BroadcastMessage.class), any(NodeAddress.class), eq(null), eq(expectedIsDataOwner));

            verifySequenceNumberMapWriteContains(currentState, P2PDataStorage.get32ByteHashAsByteArray(protectedStorageEntry.getProtectedStoragePayload()), protectedStorageEntry.getSequenceNumber());
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

        protected abstract ProtectedStoragePayload createInstance(KeyPair payloadOwnerKeys);

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

        // Return a ProtectedStorageEntry that is valid for add.
        // Overridden for the MailboxPayloadTests since the add and remove owners are different
        ProtectedStorageEntry getProtectedStorageEntryForAdd(int sequenceNumber) throws CryptoException {

            // Entry signed and owned by same owner as payload
           return buildProtectedStorageEntry(this.protectedStoragePayload, this.payloadOwnerKeys, this.payloadOwnerKeys, sequenceNumber);
        }

        // Return a ProtectedStorageEntry that is valid for remove.
        // Overridden for the MailboxPayloadTests since the add and remove owners are different
        ProtectedStorageEntry getProtectedStorageEntryForRemove(int sequenceNumber) throws CryptoException {

            // Entry signed and owned by same owner as payload
            return buildProtectedStorageEntry(this.protectedStoragePayload, this.payloadOwnerKeys, this.payloadOwnerKeys, sequenceNumber);
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

            verifyProtectedStorageRemove(this.testState, beforeState, entry, expectInternalStateChange, this.expectIsDataOwner());
        }

        // TESTCASE: Adding a well-formed entry is successful
        @Test
        public void addProtectedStorageEntry() throws CryptoException {

            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);
        }

        // TESTCASE: Adding duplicate payload w/ same sequence number
        // TODO: Should adds() of existing sequence #s return false since they don't update state?
        @Test
        public void addProtectedStorageEntry_duplicateSeqNrGt0() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageAddAndVerify(entryForAdd, true, false);
        }

        // TESTCASE: Adding duplicate payload w/ 0 sequence number (special branch in code for logging)
        @Test
        public void addProtectedStorageEntry_duplicateSeqNrEq0() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(0);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageAddAndVerify(entryForAdd, true, false);
        }

        // TESTCASE: Adding duplicate payload for w/ lower sequence number
        @Test
        public void addProtectedStorageEntry_lowerSeqNr() throws CryptoException {
            ProtectedStorageEntry entryForAdd2 = this.getProtectedStorageEntryForAdd(2);
            ProtectedStorageEntry entryForAdd1 = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd2, true, true);
            doProtectedStorageAddAndVerify(entryForAdd1, false, false);
        }

        // TESTCASE: Adding duplicate payload for w/ greater sequence number
        @Test
        public void addProtectedStorageEntry_greaterSeqNr() throws CryptoException {
            ProtectedStorageEntry entryForAdd2 = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForAdd1 = this.getProtectedStorageEntryForAdd(2);
            doProtectedStorageAddAndVerify(entryForAdd2, true, true);
            doProtectedStorageAddAndVerify(entryForAdd1, true, true);
        }

        // TESTCASE: Add w/ same sequence number after remove of sequence number
        // XXXBUGXXX: Since removes aren't required to increase the sequence number, duplicate adds
        // can occur that will cause listeners to be signaled. Any well-intentioned nodes will create remove messages
        // that increment the seq #, but this may just fall into a larger effort to protect against malicious nodes.
/*        @Test
        public void addProtectectedStorageEntry_afterRemoveSameSeqNr() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true);

            // Should be false, false. Instead, the hashmap is updated and hashmap listeners are signaled.
            // Broadcast isn't called
            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }*/

        // TESTCASE: Entry signature does not match entry owner
        @Test
        public void addProtectedStorageEntry_EntrySignatureDoesntMatchEntryOwner() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(2);

            entryForAdd.updateSignature(new byte[] { 0 });
            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Payload owner and entry owner are not compatible for add operation
        @Test
        public void addProtectedStorageEntry_payloadOwnerEntryOwnerNotCompatible() throws NoSuchAlgorithmException, CryptoException {
            KeyPair notOwner = TestUtils.generateKeyPair();

            // For standard ProtectedStorageEntrys the entry owner must match the payload owner for adds
            ProtectedStorageEntry entryForAdd = buildProtectedStorageEntry(
                    this.protectedStoragePayload, notOwner, notOwner, 1);

            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Two valid, different adds have identical payloads. Ensure the second add does not overwrite the first even if seq # increases
        // Need to refactor a bit to test this. Specifically, we need a way to generate two Entrys
        // that pass ownerPubKey & signature checks, but have a collision with the hash of the payload. This isn't
        // possible to fabricate with the current structure.
        /* @Test
        public void addProtectedStorageEntry_PayloadHashCollision_Fails() {
            // TODO: Add test
        }*/

        // TESTCASE: Removing an item after successfully added (remove seq # == add seq #)
        // XXXBUGXXX A state change shouldn't occur. Any well-intentioned nodes will create remove messages
        // that increment the seq #, but this may just fall into a larger effort to protect against malicious nodes.
        @Test
        public void remove_seqNrEqAddSeqNr() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            // should be (false, false)
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true);
        }

        // TESTCASE: Removing an item after successfully added (remove seq # > add seq #)
        @Test
        public void remove_seqNrGtAddSeqNr() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true);
        }

        // TESTCASE: Removing an item before it was added
        @Test
        public void remove_notExists() throws CryptoException {
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);

            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Removing an item after successfully adding (remove seq # < add seq #)
        @Test
        public void remove_seqNrLessAddSeqNr() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(2);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Removing an item after successfully added (invalid remove entry signature)
        @Test
        public void remove_invalidEntrySig() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);
            entryForRemove.updateSignature(new byte[] { 0 });
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Payload owner and entry owner are not compatible for remove operation
        @Test
        public void remove_payloadOwnerEntryOwnerNotCompatible() throws NoSuchAlgorithmException, CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            KeyPair notOwner = TestUtils.generateKeyPair();

            // For standard ProtectedStorageEntrys the entry owner must match the payload owner for removes
            ProtectedStorageEntry entryForRemove = buildProtectedStorageEntry(
                    this.protectedStoragePayload, notOwner, notOwner, 1);

            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Add after removed (same seq #)
        @Test
        public void add_afterRemoveSameSeqNr() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true);

            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Add after removed (greater seq #)
        @Test
        public void add_afterRemoveGreaterSeqNr() throws CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true);

            entryForAdd = this.getProtectedStorageEntryForAdd(3);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);
        }

        // TESTCASE: Add after removed (lower seq #)
        @Test
        public void add_afterRemoveLessSeqNr() throws CryptoException {
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
        public void remove_lateAdd() throws CryptoException {
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

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys,1), true, false);
        }

        // TESTCASE: Duplicate refresh message (same seq #)
        @Test
        public void refreshTTL_duplicateRefreshSeqNrEqual() throws CryptoException {
            ProtectedStorageEntry entry = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entry, true, true);

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys, 2), true, true);
            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys, 2), true, false);
        }

        // TESTCASE: Duplicate refresh message (greater seq #)
        @Test
        public void refreshTTL_duplicateRefreshSeqNrGreater() throws CryptoException {
            ProtectedStorageEntry entry = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entry, true, true);

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys,2), true, true);
            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys,3), true, true);
        }

        // TESTCASE: Duplicate refresh message (lower seq #)
        @Test
        public void refreshTTL_duplicateRefreshSeqNrLower() throws CryptoException {
            ProtectedStorageEntry entry = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entry, true, true);

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys,3), true, true);
            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys,2), false, false);
        }

        // TESTCASE: Refresh previously removed entry
        @Test
        public void refreshTTL_refreshAfterRemove() throws CryptoException {
            ProtectedStorageEntry entry = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entry, true, true);
            doProtectedStorageRemoveAndVerify(entry, true, true);

            doRefreshTTLAndVerify(buildRefreshOfferMessage(entry, this.payloadOwnerKeys,3), false, false);
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
    public static class PersistableProtectedStoragePayloadTest extends ProtectedStorageEntryTestBase {
        private static class PersistableProtectedStoragePayload extends ProtectedStoragePayloadStub implements PersistablePayload {

            PersistableProtectedStoragePayload(PublicKey ownerPubKey) {
                super(ownerPubKey);
            }
        }

        @Override
        protected ProtectedStoragePayload createInstance(KeyPair payloadOwnerKeys) {
            return new PersistableProtectedStoragePayload(payloadOwnerKeys.getPublic());
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
                return testState.mockedStorage.removeMailboxData((ProtectedMailboxStorageEntry) entry, getTestNodeAddress(), true);
            }
        }

        @Override
        ProtectedStorageEntry getProtectedStorageEntryForAdd(int sequenceNumber) throws CryptoException {
            return buildProtectedMailboxStorageEntry(senderKeys.getPublic(), receiverKeys.getPublic(), senderKeys.getPrivate(), senderKeys.getPublic(), receiverKeys.getPublic(), sequenceNumber);
        }

        @Override
        ProtectedStorageEntry getProtectedStorageEntryForRemove(int sequenceNumber) throws CryptoException {
            return buildProtectedMailboxStorageEntry(senderKeys.getPublic(), receiverKeys.getPublic(), receiverKeys.getPrivate(), receiverKeys.getPublic(), receiverKeys.getPublic(), sequenceNumber);
        }

        @Override
        protected ProtectedStoragePayload createInstance(KeyPair payloadOwnerKeys) {
            return null;
        }

        // TESTCASE: Adding fails when Entry owner is different from sender
        @Test
        public void addProtectedStorageEntry_payloadOwnerEntryOwnerNotCompatible() throws CryptoException, NoSuchAlgorithmException {
            KeyPair notSender = TestUtils.generateKeyPair();

            ProtectedStorageEntry entryForAdd = buildProtectedMailboxStorageEntry(notSender.getPublic(), receiverKeys.getPublic(), senderKeys.getPrivate(), senderKeys.getPublic(), receiverKeys.getPublic(), 1);

            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Adding MailboxStoragePayload when Entry owner is different than sender does not overwrite existing payload
        @Test
        public void addProtectedStorageEntry_payloadOwnerEntryOwnerNotCompatibleNoSideEffect() throws CryptoException, NoSuchAlgorithmException {
            KeyPair notSender = TestUtils.generateKeyPair();

            doProtectedStorageAddAndVerify(this.getProtectedStorageEntryForAdd(1), true, true);

            ProtectedStorageEntry invalidEntryForAdd = buildProtectedMailboxStorageEntry(notSender.getPublic(), receiverKeys.getPublic(), senderKeys.getPrivate(), senderKeys.getPublic(), receiverKeys.getPublic(), 1);

            doProtectedStorageAddAndVerify(invalidEntryForAdd, false, false);
        }

        // TESTCASE: Payload owner and entry owner are not compatible for remove operation
        @Test
        public void remove_payloadOwnerEntryOwnerNotCompatible() throws NoSuchAlgorithmException, CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            KeyPair notReceiver = TestUtils.generateKeyPair();

            ProtectedStorageEntry entryForRemove =  buildProtectedMailboxStorageEntry(senderKeys.getPublic(), receiverKeys.getPublic(), notReceiver.getPrivate(), notReceiver.getPublic(), receiverKeys.getPublic(), 1);

            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: Payload owner and entry.receiversPubKey are not compatible for remove operation
        @Test
        public void remove_payloadOwnerEntryReceiversPubKeyNotCompatible() throws NoSuchAlgorithmException, CryptoException {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            KeyPair notSender = TestUtils.generateKeyPair();

            ProtectedStorageEntry entryForRemove = buildProtectedMailboxStorageEntry(senderKeys.getPublic(), receiverKeys.getPublic(), receiverKeys.getPrivate(), receiverKeys.getPublic(), notSender.getPublic(), 1);

            doProtectedStorageRemoveAndVerify(entryForRemove, false, false);
        }

        // TESTCASE: receiversPubKey changed between add and remove
        // TODO: Current code does not check receiversPubKey on add() (payload.ownersPubKey == entry.receiversPubKey)
        // Can the code just check against payload.ownersPubKey in all cases and deprecate Entry.receiversPubKey?
        @Test
        public void remove_receiversPubKeyChanged() throws NoSuchAlgorithmException, CryptoException {
            KeyPair otherKeys = TestUtils.generateKeyPair();

            // Add an entry that has an invalid Entry.receiversPubKey. Unfortunately, this succeeds right now.
            ProtectedStorageEntry entryForAdd = buildProtectedMailboxStorageEntry(senderKeys.getPublic(), receiverKeys.getPublic(), senderKeys.getPrivate(), senderKeys.getPublic(), otherKeys.getPublic(), 1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            doProtectedStorageRemoveAndVerify(this.getProtectedStorageEntryForRemove(2), false, false);
        }


        // XXXBUGXXX: The P2PService calls remove() instead of removeFromMailbox() in the addMailboxData() path.
        // This test shows it will always fail even with a valid remove entry. Future work should be able to
        // combine the remove paths in the same way the add() paths are combined. This will require deprecating
        // the receiversPubKey field which is a duplicate of the ownerPubKey in the MailboxStoragePayload.
        // More investigation is needed.
        @Test
        public void remove_canCallWrongRemoveAndFail() throws CryptoException {

            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            SavedTestState beforeState = new SavedTestState(this.testState, entryForRemove);

            // Call remove(ProtectedStorageEntry) instead of removeFromMailbox(ProtectedMailboxStorageEntry) and verify
            // it fails
            boolean addResult = super.doRemove(entryForRemove);

            if (!this.useMessageHandler)
                Assert.assertFalse(addResult);

            // should succeed with expectedStatechange==true when remove paths are combined
            verifyProtectedStorageRemove(this.testState, beforeState, entryForRemove, false, this.expectIsDataOwner());
        }

        // TESTCASE: Verify misuse of the API (calling remove() instead of removeFromMailbox correctly errors with
        // a payload that is valid for remove of a non-mailbox entry.
        @Test
        public void remove_canCallWrongRemoveAndFailInvalidPayload() throws CryptoException {

            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            SavedTestState beforeState = new SavedTestState(this.testState, entryForAdd);

            // Call remove(ProtectedStorageEntry) instead of removeFromMailbox(ProtectedMailboxStorageEntry) and verify
            // it fails with a payload that isn't signed by payload.ownerPubKey
            boolean addResult = super.doRemove(entryForAdd);

            if (!this.useMessageHandler)
                Assert.assertFalse(addResult);

            verifyProtectedStorageRemove(this.testState, beforeState, entryForAdd, false, this.expectIsDataOwner());
        }

        // TESTCASE: Add after removed when add-once required (greater seq #)
        @Override
        @Test
        public void add_afterRemoveGreaterSeqNr() throws CryptoException {
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

            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys);
            ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);

            SavedTestState beforeState = new SavedTestState(this.testState, protectedStorageEntry);
            Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true));

            verifyProtectedStorageAdd(this.testState, beforeState, protectedStorageEntry, true, true);
        }

        // TESTCASE: Adding an entry from the getProtectedStorageEntry API of an existing item correctly updates the item
        @Test
        public void getProtectedStorageEntry() throws NoSuchAlgorithmException, CryptoException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();

            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys);
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

            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys);
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

            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys);

            RefreshOfferMessage refreshOfferMessage = this.testState.mockedStorage.getRefreshTTLMessage(protectedStoragePayload, ownerKeys);

            SavedTestState beforeState = new SavedTestState(this.testState, refreshOfferMessage);
            Assert.assertFalse(this.testState.mockedStorage.refreshTTL(refreshOfferMessage, getTestNodeAddress(), true));

            verifyRefreshTTL(this.testState, beforeState, refreshOfferMessage, false, true);
        }

        // TESTCASE: Updating an entry from the getRefreshTTLMessage API correctly "refreshes" the item
        @Test
        public void getRefreshTTLMessage() throws NoSuchAlgorithmException, CryptoException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();

            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys);
            ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true);

            RefreshOfferMessage refreshOfferMessage = this.testState.mockedStorage.getRefreshTTLMessage(protectedStoragePayload, ownerKeys);
            this.testState.mockedStorage.refreshTTL(refreshOfferMessage, getTestNodeAddress(), true);

            refreshOfferMessage = this.testState.mockedStorage.getRefreshTTLMessage(protectedStoragePayload, ownerKeys);

            SavedTestState beforeState = new SavedTestState(this.testState, refreshOfferMessage);
            Assert.assertTrue(this.testState.mockedStorage.refreshTTL(refreshOfferMessage, getTestNodeAddress(), true));

            verifyRefreshTTL(this.testState, beforeState, refreshOfferMessage, true, true);
        }

        // TESTCASE: Updating an entry from the getRefreshTTLMessage API correctly "refreshes" the item when it was originally added from onMessage path
        @Test
        public void getRefreshTTLMessage_FirstOnMessageSecondAPI() throws NoSuchAlgorithmException, CryptoException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();

            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys);
            ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
            this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, getTestNodeAddress(), null, true);

            Connection mockedConnection = mock(Connection.class);
            when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

            this.testState.mockedStorage.onMessage(new AddDataMessage(protectedStorageEntry), mockedConnection);

            RefreshOfferMessage refreshOfferMessage = this.testState.mockedStorage.getRefreshTTLMessage(protectedStoragePayload, ownerKeys);

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
            Assert.assertFalse(this.testState.mockedStorage.removeMailboxData(protectedMailboxStorageEntry, getTestNodeAddress(), true));

            verifyProtectedStorageRemove(this.testState, beforeState, protectedMailboxStorageEntry, false, true);
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
            Assert.assertTrue(this.testState.mockedStorage.removeMailboxData(protectedMailboxStorageEntry, getTestNodeAddress(), true));

            verifyProtectedStorageRemove(this.testState, beforeState, protectedMailboxStorageEntry, true, true);
        }

        // TESTCASE: Removing a mailbox message that was added from the onMessage handler
        @Test
        public void getMailboxDataWithSignedSeqNr_ValidRemoveAddFromMessage() throws NoSuchAlgorithmException, CryptoException {
            KeyPair receiverKeys = TestUtils.generateKeyPair();
            KeyPair senderKeys = TestUtils.generateKeyPair();

            ProtectedStorageEntry protectedStorageEntry =
                    buildProtectedMailboxStorageEntry(senderKeys.getPublic(), receiverKeys.getPublic(), senderKeys.getPrivate(),
                            senderKeys.getPublic(), receiverKeys.getPublic(), 1);

            Connection mockedConnection = mock(Connection.class);
            when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(getTestNodeAddress()));

            this.testState.mockedStorage.onMessage(new AddDataMessage(protectedStorageEntry), mockedConnection);

            MailboxStoragePayload mailboxStoragePayload = (MailboxStoragePayload) protectedStorageEntry.getProtectedStoragePayload();

            ProtectedMailboxStorageEntry protectedMailboxStorageEntry =
                    this.testState.mockedStorage.getMailboxDataWithSignedSeqNr(mailboxStoragePayload, receiverKeys, receiverKeys.getPublic());

            SavedTestState beforeState = new SavedTestState(this.testState, protectedMailboxStorageEntry);
            Assert.assertTrue(this.testState.mockedStorage.removeMailboxData(protectedMailboxStorageEntry, getTestNodeAddress(), true));

            verifyProtectedStorageRemove(this.testState, beforeState, protectedMailboxStorageEntry, true, true);
        }
    }

    public static class DisconnectTest {
        private TestState testState;
        private Connection mockedConnection;

        private static ProtectedStorageEntry populateTestState(TestState testState, long ttl) throws CryptoException, NoSuchAlgorithmException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();
            ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayload(ownerKeys, ttl);

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

            this.testState.mockedStorage.onDisconnect(CloseConnectionReason.SOCKET_CLOSED, mockedConnection);

            verifyStateAfterDisconnect(this.testState, beforeState, true, false);
        }
    }
}
