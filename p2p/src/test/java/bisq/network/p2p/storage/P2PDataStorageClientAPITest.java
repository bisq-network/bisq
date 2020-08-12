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
import bisq.network.p2p.storage.mocks.ExpirableProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Version;
import bisq.common.crypto.CryptoException;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static bisq.network.p2p.storage.TestState.*;

/**
 * Tests of the P2PDataStore Client API entry points.
 *
 * These tests validate the client code path that uses the pattern addProtectedStorageEntry(getProtectedStorageEntry())
 * as opposed to the onMessage() handler or DataRequest paths.
 */
public class P2PDataStorageClientAPITest {
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

        ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);
        Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, TestState.getTestNodeAddress(), null));

        this.testState.verifyProtectedStorageAdd(beforeState, protectedStorageEntry, true, true, true, true);
    }

    // TESTCASE: Adding an entry from the getProtectedStorageEntry API of an existing item correctly updates the item
    @Test
    public void getProtectedStorageEntry() throws NoSuchAlgorithmException, CryptoException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();

        ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);

        Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, TestState.getTestNodeAddress(), null));

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);
        protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, TestState.getTestNodeAddress(), null);

        this.testState.verifyProtectedStorageAdd(beforeState, protectedStorageEntry, true, true, true, true);
    }

    // TESTCASE: Adding an entry from the getProtectedStorageEntry API of an existing item (added from onMessage path) correctly updates the item
    @Test
    public void getProtectedStorageEntry_FirstOnMessageSecondAPI() throws NoSuchAlgorithmException, CryptoException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();

        ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);

        Connection mockedConnection = mock(Connection.class);
        when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

        this.testState.mockedStorage.onMessage(new AddDataMessage(protectedStorageEntry), mockedConnection);

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);
        protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, TestState.getTestNodeAddress(), null));

        this.testState.verifyProtectedStorageAdd(beforeState, protectedStorageEntry, true, true, true, true);
    }

    // TESTCASE: Updating an entry from the getRefreshTTLMessage API correctly errors if the item hasn't been seen
    @Test
    public void getRefreshTTLMessage_NoExists() throws NoSuchAlgorithmException, CryptoException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();

        ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayloadStub(ownerKeys.getPublic());

        RefreshOfferMessage refreshOfferMessage = this.testState.mockedStorage.getRefreshTTLMessage(protectedStoragePayload, ownerKeys);

        SavedTestState beforeState = this.testState.saveTestState(refreshOfferMessage);
        Assert.assertFalse(this.testState.mockedStorage.refreshTTL(refreshOfferMessage, TestState.getTestNodeAddress()));

        this.testState.verifyRefreshTTL(beforeState, refreshOfferMessage, false);
    }

    // TESTCASE: Updating an entry from the getRefreshTTLMessage API correctly "refreshes" the item
    @Test
    public void getRefreshTTLMessage() throws NoSuchAlgorithmException, CryptoException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();

        ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, TestState.getTestNodeAddress(), null);

        RefreshOfferMessage refreshOfferMessage = this.testState.mockedStorage.getRefreshTTLMessage(protectedStoragePayload, ownerKeys);
        this.testState.mockedStorage.refreshTTL(refreshOfferMessage, TestState.getTestNodeAddress());

        refreshOfferMessage = this.testState.mockedStorage.getRefreshTTLMessage(protectedStoragePayload, ownerKeys);

        this.testState.incrementClock();

        SavedTestState beforeState = this.testState.saveTestState(refreshOfferMessage);
        Assert.assertTrue(this.testState.mockedStorage.refreshTTL(refreshOfferMessage, TestState.getTestNodeAddress()));

        this.testState.verifyRefreshTTL(beforeState, refreshOfferMessage, true);
    }

    // TESTCASE: Updating an entry from the getRefreshTTLMessage API correctly "refreshes" the item when it was originally added from onMessage path
    @Test
    public void getRefreshTTLMessage_FirstOnMessageSecondAPI() throws NoSuchAlgorithmException, CryptoException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();

        ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = this.testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, TestState.getTestNodeAddress(), null);

        Connection mockedConnection = mock(Connection.class);
        when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

        this.testState.mockedStorage.onMessage(new AddDataMessage(protectedStorageEntry), mockedConnection);

        RefreshOfferMessage refreshOfferMessage = this.testState.mockedStorage.getRefreshTTLMessage(protectedStoragePayload, ownerKeys);

        this.testState.incrementClock();

        SavedTestState beforeState = this.testState.saveTestState(refreshOfferMessage);
        Assert.assertTrue(this.testState.mockedStorage.refreshTTL(refreshOfferMessage, TestState.getTestNodeAddress()));

        this.testState.verifyRefreshTTL(beforeState, refreshOfferMessage, true);
    }

    // TESTCASE: Removing a non-existent mailbox entry from the getMailboxDataWithSignedSeqNr API
    @Test
    public void getMailboxDataWithSignedSeqNr_RemoveNoExist() throws NoSuchAlgorithmException, CryptoException {
        KeyPair receiverKeys = TestUtils.generateKeyPair();
        KeyPair senderKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = TestState.buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());

        ProtectedMailboxStorageEntry protectedMailboxStorageEntry =
                this.testState.mockedStorage.getMailboxDataWithSignedSeqNr(mailboxStoragePayload, receiverKeys, receiverKeys.getPublic());

        SavedTestState beforeState = this.testState.saveTestState(protectedMailboxStorageEntry);
        Assert.assertTrue(this.testState.mockedStorage.remove(protectedMailboxStorageEntry, TestState.getTestNodeAddress()));

        this.testState.verifyProtectedStorageRemove(beforeState, protectedMailboxStorageEntry, false, false, true, true);
    }

    // TESTCASE: Adding, then removing a mailbox message from the getMailboxDataWithSignedSeqNr API
    @Test
    public void getMailboxDataWithSignedSeqNr_AddThenRemove() throws NoSuchAlgorithmException, CryptoException {
        KeyPair receiverKeys = TestUtils.generateKeyPair();
        KeyPair senderKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = TestState.buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());

        ProtectedMailboxStorageEntry protectedMailboxStorageEntry =
                this.testState.mockedStorage.getMailboxDataWithSignedSeqNr(mailboxStoragePayload, senderKeys, receiverKeys.getPublic());

        Assert.assertTrue(this.testState.mockedStorage.addProtectedStorageEntry(protectedMailboxStorageEntry, TestState.getTestNodeAddress(), null));

        protectedMailboxStorageEntry =
                this.testState.mockedStorage.getMailboxDataWithSignedSeqNr(mailboxStoragePayload, receiverKeys, receiverKeys.getPublic());

        SavedTestState beforeState = this.testState.saveTestState(protectedMailboxStorageEntry);
        Assert.assertTrue(this.testState.mockedStorage.remove(protectedMailboxStorageEntry, TestState.getTestNodeAddress()));

        this.testState.verifyProtectedStorageRemove(beforeState, protectedMailboxStorageEntry, true, true, true, true);
    }

    // TESTCASE: Removing a mailbox message that was added from the onMessage handler
    @Test
    public void getMailboxDataWithSignedSeqNr_ValidRemoveAddFromMessage() throws NoSuchAlgorithmException, CryptoException {
        KeyPair receiverKeys = TestUtils.generateKeyPair();
        KeyPair senderKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = TestState.buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());

        ProtectedMailboxStorageEntry protectedMailboxStorageEntry =
                this.testState.mockedStorage.getMailboxDataWithSignedSeqNr(mailboxStoragePayload, senderKeys, receiverKeys.getPublic());

        Connection mockedConnection = mock(Connection.class);
        when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

        this.testState.mockedStorage.onMessage(new AddDataMessage(protectedMailboxStorageEntry), mockedConnection);

        mailboxStoragePayload = (MailboxStoragePayload) protectedMailboxStorageEntry.getProtectedStoragePayload();

        protectedMailboxStorageEntry =
                this.testState.mockedStorage.getMailboxDataWithSignedSeqNr(mailboxStoragePayload, receiverKeys, receiverKeys.getPublic());

        SavedTestState beforeState = this.testState.saveTestState(protectedMailboxStorageEntry);
        Assert.assertTrue(this.testState.mockedStorage.remove(protectedMailboxStorageEntry, TestState.getTestNodeAddress()));

        this.testState.verifyProtectedStorageRemove(beforeState, protectedMailboxStorageEntry, true, true, true, true);
    }
}
