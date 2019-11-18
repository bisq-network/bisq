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
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.storage.mocks.ExpirableProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.crypto.CryptoException;
import bisq.common.proto.persistable.PersistablePayload;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static bisq.network.p2p.storage.TestState.*;

/**
 * Tests of the P2PDataStore ConnectionListener interface.
 */
public class P2PDataStoreDisconnectTest {
    private TestState testState;
    private Connection mockedConnection;

    private static ProtectedStorageEntry populateTestState(TestState testState,
                                                           long ttl) throws CryptoException, NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStoragePayload protectedStoragePayload = new ExpirableProtectedStoragePayloadStub(ownerKeys.getPublic(), ttl);

        ProtectedStorageEntry protectedStorageEntry = testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);
        testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, TestState.getTestNodeAddress(), null, false);

        return protectedStorageEntry;
    }

    private static void verifyStateAfterDisconnect(TestState currentState,
                                                   SavedTestState beforeState,
                                                   boolean wasRemoved,
                                                   boolean wasTTLReduced) {
        ProtectedStorageEntry protectedStorageEntry = beforeState.protectedStorageEntryBeforeOp;

        currentState.verifyProtectedStorageRemove(beforeState, protectedStorageEntry,
                wasRemoved, false, false, false);

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

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);

        this.testState.mockedStorage.onDisconnect(CloseConnectionReason.SOCKET_CLOSED, mockedConnection);

        verifyStateAfterDisconnect(this.testState, beforeState, false, false);
    }

    // TESTCASE: Unintended disconnects reduce the TTL for entrys that match disconnected peer
    @Test
    public void connectionClosedReduceTTL() throws NoSuchAlgorithmException, CryptoException {
        when(this.mockedConnection.hasPeersNodeAddress()).thenReturn(true);
        when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

        ProtectedStorageEntry protectedStorageEntry = populateTestState(testState, TimeUnit.DAYS.toMillis(90));

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);

        this.testState.mockedStorage.onDisconnect(CloseConnectionReason.SOCKET_CLOSED, mockedConnection);

        verifyStateAfterDisconnect(this.testState, beforeState, false, true);
    }

    // TESTCASE: Unintended disconnects don't reduce TTL for entrys that are not from disconnected peer
    @Test
    public void connectionClosedSkipsItemsNotFromPeer() throws NoSuchAlgorithmException, CryptoException {
        when(this.mockedConnection.hasPeersNodeAddress()).thenReturn(true);
        when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(new NodeAddress("notTestNode", 2020)));

        ProtectedStorageEntry protectedStorageEntry = populateTestState(testState, 1);

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);

        this.testState.mockedStorage.onDisconnect(CloseConnectionReason.SOCKET_CLOSED, mockedConnection);

        verifyStateAfterDisconnect(this.testState, beforeState, false, false);
    }

    // TESTCASE: Unintended disconnects expire entrys that match disconnected peer and TTL is low enough for expire
    @Test
    public void connectionClosedReduceTTLAndExpireItemsFromPeer() throws NoSuchAlgorithmException, CryptoException {
        when(this.mockedConnection.hasPeersNodeAddress()).thenReturn(true);
        when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

        ProtectedStorageEntry protectedStorageEntry = populateTestState(testState, 1);

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);

        // Increment the time by 1 hour which will put the protectedStorageState outside TTL
        this.testState.incrementClock();

        this.testState.mockedStorage.onDisconnect(CloseConnectionReason.SOCKET_CLOSED, mockedConnection);

        verifyStateAfterDisconnect(this.testState, beforeState, true, false);
    }

    // TESTCASE: ProtectedStoragePayloads implementing the PersistablePayload interface are correctly removed
    // from the persistent store during the onDisconnect path.
    @Test
    public void connectionClosedReduceTTLAndExpireItemsFromPeerPersistable()
                                                                    throws NoSuchAlgorithmException, CryptoException {

        class ExpirablePersistentProtectedStoragePayloadStub
                                         extends ExpirableProtectedStoragePayloadStub implements PersistablePayload {

            public ExpirablePersistentProtectedStoragePayloadStub(PublicKey ownerPubKey) {
                super(ownerPubKey, 0);
            }
        }

        when(this.mockedConnection.hasPeersNodeAddress()).thenReturn(true);
        when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStoragePayload protectedStoragePayload =
                new ExpirablePersistentProtectedStoragePayloadStub(ownerKeys.getPublic());

        ProtectedStorageEntry protectedStorageEntry =
                testState.mockedStorage.getProtectedStorageEntry(protectedStoragePayload, ownerKeys);

        testState.mockedStorage.addProtectedStorageEntry(
                protectedStorageEntry, TestState.getTestNodeAddress(), null, false);

        SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);

        // Increment the time by 1 hour which will put the protectedStorageState outside TTL
        this.testState.incrementClock();

        this.testState.mockedStorage.onDisconnect(CloseConnectionReason.SOCKET_CLOSED, mockedConnection);

        verifyStateAfterDisconnect(this.testState, beforeState, true, false);
    }
}
