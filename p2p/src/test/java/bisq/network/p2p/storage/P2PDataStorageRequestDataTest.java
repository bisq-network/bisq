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
import bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.mocks.ProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.util.Set;

import org.mockito.MockitoAnnotations;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class P2PDataStorageRequestDataTest {
    private TestState testState;

    private NodeAddress localNodeAddress;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        this.testState = new TestState();

        this.localNodeAddress = new NodeAddress("localhost", 8080);

        // Set up basic capabilities to ensure message contains it
        Capabilities.app.addAll(Capability.MEDIATION);
    }

    /**
     * Returns true if the target bytes are found in the container set.
     */
    private boolean byteSetContains(Set<byte[]> container, byte[] target) {
        // Set<byte[]>.contains() doesn't do a deep compare, so generate a Set<ByteArray> so equals() does what
        // we want
        Set<P2PDataStorage.ByteArray> translatedContainer =
                P2PDataStorage.ByteArray.convertBytesSetToByteArraySet(container);

        return translatedContainer.contains(new P2PDataStorage.ByteArray(target));
    }

    /**
     * Generates a unique ProtectedStorageEntry that is valid for add. This is used to initialize P2PDataStorage state
     * so the tests can validate the correct behavior. Adds of identical payloads with different sequence numbers
     * is not supported.
     */
    private ProtectedStorageEntry getProtectedStorageEntryForAdd() throws NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();

        ProtectedStoragePayload protectedStoragePayload = new ProtectedStoragePayloadStub(ownerKeys.getPublic());

        ProtectedStorageEntry stub = mock(ProtectedStorageEntry.class);
        when(stub.getOwnerPubKey()).thenReturn(ownerKeys.getPublic());
        when(stub.isValidForAddOperation()).thenReturn(true);
        when(stub.matchesRelevantPubKey(any(ProtectedStorageEntry.class))).thenReturn(true);
        when(stub.getSequenceNumber()).thenReturn(1);
        when(stub.getProtectedStoragePayload()).thenReturn(protectedStoragePayload);

        return stub;
    }

    // TESTCASE: P2PDataStorage with no entries returns an empty PreliminaryGetDataRequest
    @Test
    public void buildPreliminaryGetDataRequest_EmptyP2PDataStore() {
        PreliminaryGetDataRequest getDataRequest = this.testState.mockedStorage.buildPreliminaryGetDataRequest(1);

        Assert.assertEquals(getDataRequest.getNonce(), 1);
        Assert.assertEquals(getDataRequest.getSupportedCapabilities(), Capabilities.app);
        Assert.assertTrue(getDataRequest.getExcludedKeys().isEmpty());
    }

    // TESTCASE: P2PDataStorage with no entries returns an empty PreliminaryGetDataRequest
    @Test
    public void buildGetUpdatedDataRequest_EmptyP2PDataStore() {
        GetUpdatedDataRequest getDataRequest =
                this.testState.mockedStorage.buildGetUpdatedDataRequest(this.localNodeAddress, 1);

        Assert.assertEquals(getDataRequest.getNonce(), 1);
        Assert.assertEquals(getDataRequest.getSenderNodeAddress(), this.localNodeAddress);
        Assert.assertTrue(getDataRequest.getExcludedKeys().isEmpty());
    }

    // TESTCASE: P2PDataStorage with PersistableNetworkPayloads and ProtectedStorageEntry generates
    // correct GetDataRequestMessage with both sets of keys.
    @Test
    public void buildPreliminaryGetDataRequest_FilledP2PDataStore() throws NoSuchAlgorithmException {
        PersistableNetworkPayload toAdd1 = new PersistableNetworkPayloadStub(new byte[] { 1 });
        PersistableNetworkPayload toAdd2 = new PersistableNetworkPayloadStub(new byte[] { 2 });
        ProtectedStorageEntry toAdd3 = getProtectedStorageEntryForAdd();
        ProtectedStorageEntry toAdd4 = getProtectedStorageEntryForAdd();

        this.testState.mockedStorage.addPersistableNetworkPayload(toAdd1, this.localNodeAddress, false);
        this.testState.mockedStorage.addPersistableNetworkPayload(toAdd2, this.localNodeAddress, false);

        this.testState.mockedStorage.addProtectedStorageEntry(toAdd3, this.localNodeAddress, null);
        this.testState.mockedStorage.addProtectedStorageEntry(toAdd4, this.localNodeAddress, null);

        PreliminaryGetDataRequest getDataRequest = this.testState.mockedStorage.buildPreliminaryGetDataRequest(1);

        Assert.assertEquals(getDataRequest.getNonce(), 1);
        Assert.assertEquals(getDataRequest.getSupportedCapabilities(), Capabilities.app);
        Assert.assertEquals(4, getDataRequest.getExcludedKeys().size());
        Assert.assertTrue(byteSetContains(getDataRequest.getExcludedKeys(), toAdd1.getHash()));
        Assert.assertTrue(byteSetContains(getDataRequest.getExcludedKeys(), toAdd2.getHash()));
        Assert.assertTrue(byteSetContains(getDataRequest.getExcludedKeys(),
                P2PDataStorage.get32ByteHash(toAdd3.getProtectedStoragePayload())));
        Assert.assertTrue(byteSetContains(getDataRequest.getExcludedKeys(),
                P2PDataStorage.get32ByteHash(toAdd4.getProtectedStoragePayload())));
    }

    // TESTCASE: P2PDataStorage with PersistableNetworkPayloads and ProtectedStorageEntry generates
    // correct GetDataRequestMessage with both sets of keys.
    @Test
    public void requestData_FilledP2PDataStore_GetUpdatedDataRequest() throws NoSuchAlgorithmException {
        PersistableNetworkPayload toAdd1 = new PersistableNetworkPayloadStub(new byte[] { 1 });
        PersistableNetworkPayload toAdd2 = new PersistableNetworkPayloadStub(new byte[] { 2 });
        ProtectedStorageEntry toAdd3 = getProtectedStorageEntryForAdd();
        ProtectedStorageEntry toAdd4 = getProtectedStorageEntryForAdd();

        this.testState.mockedStorage.addPersistableNetworkPayload(toAdd1, this.localNodeAddress, false);
        this.testState.mockedStorage.addPersistableNetworkPayload(toAdd2, this.localNodeAddress, false);

        this.testState.mockedStorage.addProtectedStorageEntry(toAdd3, this.localNodeAddress, null);
        this.testState.mockedStorage.addProtectedStorageEntry(toAdd4, this.localNodeAddress, null);

        GetUpdatedDataRequest getDataRequest =
                this.testState.mockedStorage.buildGetUpdatedDataRequest(this.localNodeAddress, 1);

        Assert.assertEquals(getDataRequest.getNonce(), 1);
        Assert.assertEquals(getDataRequest.getSenderNodeAddress(), this.localNodeAddress);
        Assert.assertEquals(4, getDataRequest.getExcludedKeys().size());
        Assert.assertTrue(byteSetContains(getDataRequest.getExcludedKeys(), toAdd1.getHash()));
        Assert.assertTrue(byteSetContains(getDataRequest.getExcludedKeys(), toAdd2.getHash()));
        Assert.assertTrue(byteSetContains(getDataRequest.getExcludedKeys(),
                P2PDataStorage.get32ByteHash(toAdd3.getProtectedStoragePayload())));
        Assert.assertTrue(byteSetContains(getDataRequest.getExcludedKeys(),
                P2PDataStorage.get32ByteHash(toAdd4.getProtectedStoragePayload())));
    }
}
