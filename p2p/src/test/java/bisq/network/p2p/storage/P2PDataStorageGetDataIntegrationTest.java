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
import bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.storage.mocks.PersistableExpirableProtectedStoragePayloadStub;
import bisq.network.p2p.storage.mocks.ProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Capabilities;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class P2PDataStorageGetDataIntegrationTest {

    /**
     * Generates a unique ProtectedStorageEntry that is valid for add and remove.
     */
    private ProtectedStorageEntry getProtectedStorageEntry() throws NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();

        return getProtectedStorageEntry(
                ownerKeys.getPublic(), new ProtectedStoragePayloadStub(ownerKeys.getPublic()), 1);
    }

    private ProtectedStorageEntry getProtectedStorageEntry(
            PublicKey ownerPubKey,
            ProtectedStoragePayload protectedStoragePayload,
            int sequenceNumber) {
        ProtectedStorageEntry stub = mock(ProtectedStorageEntry.class);
        when(stub.getOwnerPubKey()).thenReturn(ownerPubKey);
        when(stub.isValidForAddOperation()).thenReturn(true);
        when(stub.isValidForRemoveOperation()).thenReturn(true);
        when(stub.matchesRelevantPubKey(any(ProtectedStorageEntry.class))).thenReturn(true);
        when(stub.getSequenceNumber()).thenReturn(sequenceNumber);
        when(stub.getProtectedStoragePayload()).thenReturn(protectedStoragePayload);

        return stub;
    }

    // TESTCASE: Basic synchronization of a ProtectedStorageEntry works between a seed node and client node
    @Test
    public void basicSynchronizationWorks() throws NoSuchAlgorithmException {
        TestState seedNodeTestState = new TestState();
        P2PDataStorage seedNode = seedNodeTestState.mockedStorage;

        TestState clientNodeTestState = new TestState();
        P2PDataStorage clientNode = clientNodeTestState.mockedStorage;

        ProtectedStorageEntry onSeedNode = getProtectedStorageEntry();
        seedNode.addProtectedStorageEntry(onSeedNode, null, null);

        GetDataRequest getDataRequest = clientNode.buildPreliminaryGetDataRequest(1);

        GetDataResponse getDataResponse = seedNode.buildGetDataResponse(
                getDataRequest, 1, new AtomicBoolean(), new AtomicBoolean(), new Capabilities());

        TestState.SavedTestState beforeState = clientNodeTestState.saveTestState(onSeedNode);
        clientNode.processGetDataResponse(getDataResponse, null);

        clientNodeTestState.verifyProtectedStorageAdd(
                beforeState, onSeedNode, true, true, false, true);
    }

    // TESTCASE: Synchronization after peer restart works for in-memory ProtectedStorageEntrys
    @Test
    public void basicSynchronizationWorksAfterRestartTransient() throws NoSuchAlgorithmException {
        ProtectedStorageEntry transientEntry = getProtectedStorageEntry();

        TestState seedNodeTestState = new TestState();
        P2PDataStorage seedNode = seedNodeTestState.mockedStorage;

        TestState clientNodeTestState = new TestState();
        P2PDataStorage clientNode = clientNodeTestState.mockedStorage;

        seedNode.addProtectedStorageEntry(transientEntry, null, null);

        clientNode.addProtectedStorageEntry(transientEntry, null, null);

        clientNodeTestState.simulateRestart();
        clientNode = clientNodeTestState.mockedStorage;

        GetDataRequest getDataRequest = clientNode.buildPreliminaryGetDataRequest(1);

        GetDataResponse getDataResponse = seedNode.buildGetDataResponse(
                getDataRequest, 1, new AtomicBoolean(), new AtomicBoolean(), new Capabilities());

        TestState.SavedTestState beforeState = clientNodeTestState.saveTestState(transientEntry);
        clientNode.processGetDataResponse(getDataResponse, null);

        clientNodeTestState.verifyProtectedStorageAdd(
                beforeState, transientEntry, true, true, false, true);
    }

    // TESTCASE: Synchronization after peer restart works for in-memory ProtectedStorageEntrys
    @Test
    public void basicSynchronizationWorksAfterRestartPersistent() throws NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStoragePayload persistentPayload =
                new PersistableExpirableProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry persistentEntry = getProtectedStorageEntry(ownerKeys.getPublic(), persistentPayload, 1);

        TestState seedNodeTestState = new TestState();
        P2PDataStorage seedNode = seedNodeTestState.mockedStorage;

        TestState clientNodeTestState = new TestState();
        P2PDataStorage clientNode = clientNodeTestState.mockedStorage;

        seedNode.addProtectedStorageEntry(persistentEntry, null, null);

        clientNode.addProtectedStorageEntry(persistentEntry, null, null);

        clientNodeTestState.simulateRestart();
        clientNode = clientNodeTestState.mockedStorage;

        GetDataRequest getDataRequest = clientNode.buildPreliminaryGetDataRequest(1);

        GetDataResponse getDataResponse = seedNode.buildGetDataResponse(
                getDataRequest, 1, new AtomicBoolean(), new AtomicBoolean(), new Capabilities());

        TestState.SavedTestState beforeState = clientNodeTestState.saveTestState(persistentEntry);
        clientNode.processGetDataResponse(getDataResponse, null);

        clientNodeTestState.verifyProtectedStorageAdd(
                beforeState, persistentEntry, false, false, false, false);
        Assert.assertTrue(clientNodeTestState.mockedStorage.getMap().containsValue(persistentEntry));
    }

    // TESTCASE: Removes seen only by the seednode should be replayed on the client node
    // during startup
    // XXXBUGXXX: #3610 Lost removes are never replayed.
    @Test
    public void lostRemoveNeverUpdated() throws NoSuchAlgorithmException {
        TestState seedNodeTestState = new TestState();
        P2PDataStorage seedNode = seedNodeTestState.mockedStorage;

        TestState clientNodeTestState = new TestState();
        P2PDataStorage clientNode = clientNodeTestState.mockedStorage;

        // Both nodes see the add
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStoragePayload protectedStoragePayload = new ProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry onSeedNodeAndClientNode = getProtectedStorageEntry(
                ownerKeys.getPublic(), protectedStoragePayload, 1);
        seedNode.addProtectedStorageEntry(onSeedNodeAndClientNode, null, null);
        clientNode.addProtectedStorageEntry(onSeedNodeAndClientNode, null, null);

        // Seed node sees the remove, but client node does not
        seedNode.remove(getProtectedStorageEntry(
                ownerKeys.getPublic(), protectedStoragePayload, 2), null);

        GetDataRequest getDataRequest = clientNode.buildPreliminaryGetDataRequest(1);

        GetDataResponse getDataResponse = seedNode.buildGetDataResponse(
                getDataRequest, 1, new AtomicBoolean(), new AtomicBoolean(), new Capabilities());

        TestState.SavedTestState beforeState = clientNodeTestState.saveTestState(onSeedNodeAndClientNode);
        clientNode.processGetDataResponse(getDataResponse, null);

        // Should succeed
        clientNodeTestState.verifyProtectedStorageRemove(
                beforeState, onSeedNodeAndClientNode, false, false, false, false);
    }
}
