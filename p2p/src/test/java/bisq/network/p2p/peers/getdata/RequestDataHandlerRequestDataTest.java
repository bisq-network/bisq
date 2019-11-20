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

package bisq.network.p2p.peers.getdata;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.TestUtils;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.TestState;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.mocks.ProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;

import com.google.common.util.concurrent.SettableFuture;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;



import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class RequestDataHandlerRequestDataTest {
    private TestState testState;

    @Mock
    NetworkNode networkNode;

    private NodeAddress localNodeAddress;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        this.testState = new TestState();

        this.localNodeAddress = new NodeAddress("localhost", 8080);
        when(networkNode.getNodeAddress()).thenReturn(this.localNodeAddress);

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
    public void requestData_EmptyP2PDataStore_PreliminaryGetDataRequest() {
        SettableFuture<Connection> sendFuture = mock(SettableFuture.class);

        final ArgumentCaptor<PreliminaryGetDataRequest> captor = ArgumentCaptor.forClass(PreliminaryGetDataRequest.class);
        when(networkNode.sendMessage(any(NodeAddress.class), captor.capture())).thenReturn(sendFuture);

        RequestDataHandler handler = new RequestDataHandler(
                this.networkNode,
                this.testState.getMockedStorage(),
                mock(PeerManager.class),
                mock(RequestDataHandler.Listener.class)
        );

        handler.requestData(this.localNodeAddress, true);

        // expect empty message since p2pDataStore is empty
        PreliminaryGetDataRequest getDataRequest = captor.getValue();

        Assert.assertEquals(getDataRequest.getNonce(), handler.nonce);
        Assert.assertEquals(getDataRequest.getSupportedCapabilities(), Capabilities.app);
        Assert.assertTrue(getDataRequest.getExcludedKeys().isEmpty());
    }

    // TESTCASE: P2PDataStorage with no entries returns an empty PreliminaryGetDataRequest
    @Test
    public void requestData_EmptyP2PDataStore_GetUpdatedDataRequest() {
        SettableFuture<Connection> sendFuture = mock(SettableFuture.class);

        final ArgumentCaptor<GetUpdatedDataRequest> captor = ArgumentCaptor.forClass(GetUpdatedDataRequest.class);
        when(networkNode.sendMessage(any(NodeAddress.class), captor.capture())).thenReturn(sendFuture);

        RequestDataHandler handler = new RequestDataHandler(
                this.networkNode,
                this.testState.getMockedStorage(),
                mock(PeerManager.class),
                mock(RequestDataHandler.Listener.class)
        );

        handler.requestData(this.localNodeAddress, false);

        // expect empty message since p2pDataStore is empty
        GetUpdatedDataRequest getDataRequest = captor.getValue();

        Assert.assertEquals(getDataRequest.getNonce(), handler.nonce);
        Assert.assertEquals(getDataRequest.getSenderNodeAddress(), this.localNodeAddress);
        Assert.assertTrue(getDataRequest.getExcludedKeys().isEmpty());
    }

    // TESTCASE: P2PDataStorage with PersistableNetworkPayloads and ProtectedStorageEntry generates
    // correct GetDataRequestMessage with both sets of keys.
    @Test
    public void requestData_FilledP2PDataStore_PreliminaryGetDataRequest() throws NoSuchAlgorithmException {
        SettableFuture<Connection> sendFuture = mock(SettableFuture.class);

        PersistableNetworkPayload toAdd1 = new PersistableNetworkPayloadStub(new byte[] { 1 });
        PersistableNetworkPayload toAdd2 = new PersistableNetworkPayloadStub(new byte[] { 2 });
        ProtectedStorageEntry toAdd3 = getProtectedStorageEntryForAdd();
        ProtectedStorageEntry toAdd4 = getProtectedStorageEntryForAdd();

        this.testState.getMockedStorage().addPersistableNetworkPayload(
                toAdd1, this.localNodeAddress, false, false, false, false);
        this.testState.getMockedStorage().addPersistableNetworkPayload(
                toAdd2, this.localNodeAddress, false, false, false, false);

        this.testState.getMockedStorage().addProtectedStorageEntry(toAdd3, this.localNodeAddress, null, false);
        this.testState.getMockedStorage().addProtectedStorageEntry(toAdd4, this.localNodeAddress, null, false);

        final ArgumentCaptor<PreliminaryGetDataRequest> captor = ArgumentCaptor.forClass(PreliminaryGetDataRequest.class);
        when(networkNode.sendMessage(any(NodeAddress.class), captor.capture())).thenReturn(sendFuture);

        RequestDataHandler handler = new RequestDataHandler(
                this.networkNode,
                this.testState.getMockedStorage(),
                mock(PeerManager.class),
                mock(RequestDataHandler.Listener.class)
        );

        handler.requestData(this.localNodeAddress, true);

        // expect empty message since p2pDataStore is empty
        PreliminaryGetDataRequest getDataRequest = captor.getValue();

        Assert.assertEquals(getDataRequest.getNonce(), handler.nonce);
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
        SettableFuture<Connection> sendFuture = mock(SettableFuture.class);

        PersistableNetworkPayload toAdd1 = new PersistableNetworkPayloadStub(new byte[] { 1 });
        PersistableNetworkPayload toAdd2 = new PersistableNetworkPayloadStub(new byte[] { 2 });
        ProtectedStorageEntry toAdd3 = getProtectedStorageEntryForAdd();
        ProtectedStorageEntry toAdd4 = getProtectedStorageEntryForAdd();

        this.testState.getMockedStorage().addPersistableNetworkPayload(
                toAdd1, this.localNodeAddress, false, false, false, false);
        this.testState.getMockedStorage().addPersistableNetworkPayload(
                toAdd2, this.localNodeAddress, false, false, false, false);

        this.testState.getMockedStorage().addProtectedStorageEntry(toAdd3, this.localNodeAddress, null, false);
        this.testState.getMockedStorage().addProtectedStorageEntry(toAdd4, this.localNodeAddress, null, false);

        final ArgumentCaptor<GetUpdatedDataRequest> captor = ArgumentCaptor.forClass(GetUpdatedDataRequest.class);
        when(networkNode.sendMessage(any(NodeAddress.class), captor.capture())).thenReturn(sendFuture);

        RequestDataHandler handler = new RequestDataHandler(
                this.networkNode,
                this.testState.getMockedStorage(),
                mock(PeerManager.class),
                mock(RequestDataHandler.Listener.class)
        );

        handler.requestData(this.localNodeAddress, false);

        // expect empty message since p2pDataStore is empty
        GetUpdatedDataRequest getDataRequest = captor.getValue();

        Assert.assertEquals(getDataRequest.getNonce(), handler.nonce);
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
