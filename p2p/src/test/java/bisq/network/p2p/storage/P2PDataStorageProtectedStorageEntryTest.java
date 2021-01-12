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

import bisq.network.p2p.TestUtils;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.storage.messages.AddDataMessage;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;
import bisq.network.p2p.storage.messages.RemoveDataMessage;
import bisq.network.p2p.storage.messages.RemoveMailboxDataMessage;
import bisq.network.p2p.storage.mocks.PersistableExpirableProtectedStoragePayloadStub;
import bisq.network.p2p.storage.mocks.ProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Version;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Sig;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static bisq.network.p2p.storage.TestState.SavedTestState;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


/**
 * Tests of the P2PDataStore entry points that use the ProtectedStorageEntry type
 *
 * The abstract base class ProtectedStorageEntryTestBase defines the common test cases and each Entry and Payload type
 * that needs to be tested is set up through extending the base class and overriding the createInstance() and
 * getEntryClass() methods to give the common tests a different combination to test.
 *
 * Each subclass (Entry & Payload combination) can optionally add additional tests that verify functionality only relevant
 * to that combination.
 *
 * Each test case is run through 2 entry points to validate the correct behavior
 * 1. Client API [addProtectedStorageEntry(), refreshTTL(), remove()]
 * 2. onMessage() [AddDataMessage, RefreshOfferMessage, RemoveDataMessage]
 */
@SuppressWarnings("unused")
public class P2PDataStorageProtectedStorageEntryTest {
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
                when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

                testState.mockedStorage.onMessage(new RemoveDataMessage(entry), mockedConnection);

                return true;
            } else {
                return testState.mockedStorage.remove(entry, TestState.getTestNodeAddress());
            }
        }

        boolean doAdd(ProtectedStorageEntry protectedStorageEntry) {
            if (this.useMessageHandler) {
                Connection mockedConnection = mock(Connection.class);
                when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

                testState.mockedStorage.onMessage(new AddDataMessage(protectedStorageEntry), mockedConnection);

                return true;
            } else {
                return this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry,
                        TestState.getTestNodeAddress(), null);
            }
        }

        boolean doRefreshTTL(RefreshOfferMessage refreshOfferMessage) {
            if (this.useMessageHandler) {
                Connection mockedConnection = mock(Connection.class);
                when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

                testState.mockedStorage.onMessage(refreshOfferMessage, mockedConnection);

                return true;
            } else {
                return this.testState.mockedStorage.refreshTTL(refreshOfferMessage, TestState.getTestNodeAddress());
            }
        }

        ProtectedStorageEntry getProtectedStorageEntryForAdd(int sequenceNumber, boolean validForAdd, boolean matchesRelevantPubKey) {
            ProtectedStorageEntry stub = mock(entryClass);
            when(stub.getOwnerPubKey()).thenReturn(this.payloadOwnerKeys.getPublic());
            when(stub.isValidForAddOperation()).thenReturn(validForAdd);
            when(stub.matchesRelevantPubKey(any(ProtectedStorageEntry.class))).thenReturn(matchesRelevantPubKey);
            when(stub.getSequenceNumber()).thenReturn(sequenceNumber);
            when(stub.getProtectedStoragePayload()).thenReturn(protectedStoragePayload);

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

            SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);

            boolean addResult = this.doAdd(protectedStorageEntry);

            if (!this.useMessageHandler)
                Assert.assertEquals(expectedReturnValue, addResult);

            if (expectedStateChange) {
                this.testState.verifyProtectedStorageAdd(
                        beforeState, protectedStorageEntry, true, true, true, true);
            } else{
                this.testState.verifyProtectedStorageAdd(
                        beforeState, protectedStorageEntry, false, false, false, false);
            }
        }

        void doProtectedStorageRemoveAndVerify(ProtectedStorageEntry entry,
                                               boolean expectedReturnValue,
                                               boolean expectedHashMapAndDataStoreUpdated,
                                               boolean expectedListenersSignaled,
                                               boolean expectedBroadcast,
                                               boolean expectedSeqNrWrite) {

            SavedTestState beforeState = this.testState.saveTestState(entry);

            boolean addResult = this.doRemove(entry);

            if (!this.useMessageHandler)
                Assert.assertEquals(expectedReturnValue, addResult);

            this.testState.verifyProtectedStorageRemove(beforeState, entry, expectedHashMapAndDataStoreUpdated, expectedListenersSignaled, expectedBroadcast, expectedSeqNrWrite);
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
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false, false, false, false);

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

        /// Valid remove tests (isValidForRemove() and isMetadataEquals() return true)

        // TESTCASE: Removing an item after successfully added (remove seq # == add seq #)
        @Test
        public void remove_seqNrEqAddSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            doProtectedStorageRemoveAndVerify(entryForRemove, false, false, false, false, false);
        }

        // TESTCASE: Removing an item after successfully added (remove seq # > add seq #)
        @Test
        public void remove_seqNrGtAddSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true, true, true, true);
        }

        // TESTCASE: Removing an item before it was added. This triggers a SequenceNumberMap write and broadcast
        @Test
        public void remove_notExists() {
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);

            doProtectedStorageRemoveAndVerify(entryForRemove, true, false, false, true, true);
        }

        // TESTCASE: Removing an item after successfully adding (remove seq # < add seq #)
        @Test
        public void remove_seqNrLessAddSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(2);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false, false, false, false);
        }

        // TESTCASE: Add after removed (same seq #)
        @Test
        public void add_afterRemoveSameSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true, true, true, true);

            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Add after removed (greater seq #)
        @Test
        public void add_afterRemoveGreaterSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true, true, true, true);

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
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false, false, false, false);
        }

        // TESTCASE: Remove fails if Entry is valid for remove, but metadata doesn't match remove target
        @Test
        public void remove_EntryNotmatchesRelevantPubKey() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2, true, false);
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false, false, false, false);
        }

        // TESTCASE: Remove fails if Entry is not valid for remove and metadata doesn't match remove target
        @Test
        public void remove_EntryNotisValidForRemoveOperationNotmatchesRelevantPubKey() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2, false, false);
            doProtectedStorageRemoveAndVerify(entryForRemove, false, false, false, false, false);
        }


        // TESTCASE: Add after removed (lower seq #)
        @Test
        public void add_afterRemoveLessSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(2);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(3);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true, true, true, true);

            entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Received remove for nonexistent item that was later received
        @Test
        public void remove_lateAdd() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);

            this.doRemove(entryForRemove);

            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }

        // TESTCASE: Invalid remove doesn't block a valid add (isValidForRemove == false | matchesRelevantPubKey == false)
        @Test
        public void remove_entryNotIsValidForRemoveDoesntBlockAdd1() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1, false, false);

            this.doRemove(entryForRemove);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);
        }

        // TESTCASE: Invalid remove doesn't block a valid add (isValidForRemove == false | matchesRelevantPubKey == true)
        @Test
        public void remove_entryNotIsValidForRemoveDoesntBlockAdd2() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(1, false, true);

            this.doRemove(entryForRemove);

            doProtectedStorageAddAndVerify(entryForAdd, true, true);
        }
    }

    /**
     * Runs the common test cases defined in ProtectedStorageEntryTestBase against a ProtectedStorageEntry
     * wrapper and ProtectedStoragePayload payload.
     */
    public static class ProtectedStorageEntryTest extends ProtectedStorageEntryTestBase {

        @Override
        protected ProtectedStoragePayload createInstance(KeyPair payloadOwnerKeys) {
            return new ProtectedStoragePayloadStub(payloadOwnerKeys.getPublic());
        }

        @Override
        protected Class<ProtectedStorageEntry> getEntryClass() {
            return ProtectedStorageEntry.class;
        }

        static RefreshOfferMessage buildRefreshOfferMessage(ProtectedStoragePayload protectedStoragePayload,
                                                            KeyPair ownerKeys,
                                                            int sequenceNumber) throws CryptoException {

            P2PDataStorage.ByteArray hashOfPayload = P2PDataStorage.get32ByteHashAsByteArray(protectedStoragePayload);

            byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new P2PDataStorage.DataAndSeqNrPair(protectedStoragePayload, sequenceNumber));
            byte[] signature = Sig.sign(ownerKeys.getPrivate(), hashOfDataAndSeqNr);
            return new RefreshOfferMessage(hashOfDataAndSeqNr, signature, hashOfPayload.bytes, sequenceNumber);
        }

        RefreshOfferMessage buildRefreshOfferMessage(ProtectedStorageEntry protectedStorageEntry, KeyPair ownerKeys, int sequenceNumber) throws CryptoException {
            return buildRefreshOfferMessage(protectedStorageEntry.getProtectedStoragePayload(), ownerKeys, sequenceNumber);
        }

        void doRefreshTTLAndVerify(RefreshOfferMessage refreshOfferMessage, boolean expectedReturnValue, boolean expectStateChange) {
            SavedTestState beforeState = this.testState.saveTestState(refreshOfferMessage);

            boolean returnValue = this.doRefreshTTL(refreshOfferMessage);

            if (!this.useMessageHandler)
                Assert.assertEquals(expectedReturnValue, returnValue);

            this.testState.verifyRefreshTTL(beforeState, refreshOfferMessage, expectStateChange);
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
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true, true, true, true);

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

        // TESTCASE: After restart, identical sequence numbers are accepted ONCE. We need a way to reconstruct
        // in-memory ProtectedStorageEntrys from seed and peer nodes around startup time.
        @Test
        public void addProtectedStorageEntry_afterRestartCanAddDuplicateSeqNr() {
            ProtectedStorageEntry toAdd1 = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(toAdd1, true, true);

            this.testState.simulateRestart();

            // Can add equal seqNr only once
            doProtectedStorageAddAndVerify(toAdd1, true, true);

            // Can't add equal seqNr twice
            doProtectedStorageAddAndVerify(toAdd1, false, false);
        }

        // TESTCASE: After restart, old sequence numbers are not accepted
        @Test
        public void addProtectedStorageEntry_afterRestartCanNotAddLowerSeqNr() {
            ProtectedStorageEntry toAdd1 = this.getProtectedStorageEntryForAdd(1);
            ProtectedStorageEntry toAdd2 = this.getProtectedStorageEntryForAdd(2);
            doProtectedStorageAddAndVerify(toAdd2, true, true);

            this.testState.simulateRestart();

            doProtectedStorageAddAndVerify(toAdd1, false, false);
        }
    }

    /**
     * Runs the common test cases defined in ProtectedStorageEntryTestBase against a ProtectedStorageEntry
     * wrapper and PersistableExpirableProtectedStoragePayload payload.
     */
    public static class PersistableExpirableProtectedStoragePayloadStubTest extends ProtectedStorageEntryTestBase {
        @Override
        protected ProtectedStoragePayload createInstance(KeyPair payloadOwnerKeys) {
            return new PersistableExpirableProtectedStoragePayloadStub(payloadOwnerKeys.getPublic());
        }

        @Override
        protected Class<ProtectedStorageEntry> getEntryClass() {
            return ProtectedStorageEntry.class;
        }


        // Tests that just apply to PersistablePayload objects

        // TESTCASE: Ensure the HashMap is the same before and after a restart
        @Test
        public void addProtectedStorageEntry_afterReadFromResourcesWithDuplicate_3629RegressionTest() {
            ProtectedStorageEntry protectedStorageEntry = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(protectedStorageEntry, true, true);

            Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> beforeRestart = this.testState.mockedStorage.getMap();

            this.testState.simulateRestart();

            Assert.assertEquals(beforeRestart, this.testState.mockedStorage.getMap());
        }

        // TESTCASE: After restart, identical sequence numbers are not accepted for persistent payloads
        @Test
        public void addProtectedStorageEntry_afterRestartCanNotAddDuplicateSeqNr() {
            ProtectedStorageEntry toAdd1 = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(toAdd1, true, true);

            this.testState.simulateRestart();

            // Can add equal seqNr only once
            doProtectedStorageAddAndVerify(toAdd1, false, false);
        }
    }

    /**
     * Runs the common test cases defined in ProtectedStorageEntryTestBase against a ProtectedMailboxStorageEntry
     * wrapper and MailboxStoragePayload payload.
     */
    public static class MailboxPayloadTest extends ProtectedStorageEntryTestBase {

        @Override
        protected ProtectedStoragePayload createInstance(KeyPair payloadOwnerKeys) {
            return TestState.buildMailboxStoragePayload(payloadOwnerKeys.getPublic(), payloadOwnerKeys.getPublic());
        }

        @Override
        protected Class<ProtectedMailboxStorageEntry> getEntryClass() {
            return ProtectedMailboxStorageEntry.class;
        }

        @Override
        @Before
        public void setUp() throws CryptoException, NoSuchAlgorithmException {
            super.setUp();

            // Deep in the bowels of protobuf we grab the messageID from the version module. This is required to hash the
            // full MailboxStoragePayload so make sure it is initialized.
            Version.setBaseCryptoNetworkId(1);
        }

        @Override
        boolean doRemove(ProtectedStorageEntry entry) {
            if (this.useMessageHandler) {
                Connection mockedConnection = mock(Connection.class);
                when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

                testState.mockedStorage.onMessage(new RemoveMailboxDataMessage((ProtectedMailboxStorageEntry) entry), mockedConnection);

                return true;
            } else {
                return testState.mockedStorage.remove(entry, TestState.getTestNodeAddress());
            }
        }

        // TESTCASE: Add after removed when add-once required (greater seq #)
        @Override
        @Test
        @Ignore //TODO fix test
        public void add_afterRemoveGreaterSeqNr() {
            ProtectedStorageEntry entryForAdd = this.getProtectedStorageEntryForAdd(1);
            doProtectedStorageAddAndVerify(entryForAdd, true, true);

            ProtectedStorageEntry entryForRemove = this.getProtectedStorageEntryForRemove(2);
            doProtectedStorageRemoveAndVerify(entryForRemove, true, true, true, true, true);

            entryForAdd = this.getProtectedStorageEntryForAdd(3);
            doProtectedStorageAddAndVerify(entryForAdd, false, false);
        }
    }
}
