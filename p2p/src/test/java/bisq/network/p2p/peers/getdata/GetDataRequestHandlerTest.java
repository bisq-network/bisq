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
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.TestState;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.mocks.ProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.Proto;
import bisq.common.app.Capabilities;
import bisq.common.app.Capability;

import com.google.common.util.concurrent.SettableFuture;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

public class GetDataRequestHandlerTest {
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

        // Set up basic capabilities to ensure message contains it. Ensure it is unique from other tests
        // so we catch mismatch bugs.
        Capabilities.app.addAll(Capability.DAO_FULL_NODE);
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

    // TESTCASE: Construct a request that requires excluding duplicates and adding missing entrys for
    // PersistableNetworkPayloads and ProtectedStorageEntrys to verify the correct keys are added to the response.
    @Test
    public void handle_PreliminaryGetDataRequestTest() throws NoSuchAlgorithmException {

        // Construct a seed node w/ 2 PersistableNetworkPayloads and 2 ProtectedStorageEntrys and a PreliminaryGetDataRequest
        // that only contains 1 known PersistableNetworkPayload and 1 known ProtectedStorageEntry as well as 2 unknowns
        PersistableNetworkPayload pnp_onSeedNodeNotInRequest = new PersistableNetworkPayloadStub(new byte[] { 1 });
        PersistableNetworkPayload pnp_onSeedNodeAndInRequest = new PersistableNetworkPayloadStub(new byte[] { 2 });
        PersistableNetworkPayload pnp_onlyInRequest = new PersistableNetworkPayloadStub(new byte[] { 3 });
        ProtectedStorageEntry pse_onSeedNodeNotInRequest = getProtectedStorageEntryForAdd();
        ProtectedStorageEntry pse_onSeedNodeAndInRequest = getProtectedStorageEntryForAdd();
        ProtectedStorageEntry pse_onlyInRequest = getProtectedStorageEntryForAdd();

        this.testState.getMockedStorage().addPersistableNetworkPayload(
                pnp_onSeedNodeNotInRequest, this.localNodeAddress, false, false, false, false);
        this.testState.getMockedStorage().addPersistableNetworkPayload(
                pnp_onSeedNodeAndInRequest, this.localNodeAddress, false, false, false, false);
        this.testState.getMockedStorage().addProtectedStorageEntry(pse_onSeedNodeNotInRequest, this.localNodeAddress, null, false);
        this.testState.getMockedStorage().addProtectedStorageEntry(pse_onSeedNodeAndInRequest, this.localNodeAddress, null, false);

        SettableFuture<Connection> sendFuture = mock(SettableFuture.class);
        final ArgumentCaptor<GetDataResponse> captor = ArgumentCaptor.forClass(GetDataResponse.class);
        when(this.networkNode.sendMessage(any(Connection.class), captor.capture())).thenReturn(sendFuture);

        PreliminaryGetDataRequest getDataRequest = mock(PreliminaryGetDataRequest.class);
        HashSet<byte[]> knownKeysSet = new HashSet<>(Arrays.asList(
                pnp_onSeedNodeAndInRequest.getHash(),
                pnp_onlyInRequest.getHash(),
                P2PDataStorage.get32ByteHash(pse_onSeedNodeAndInRequest.getProtectedStoragePayload()),
                P2PDataStorage.get32ByteHash(pse_onlyInRequest.getProtectedStoragePayload())));
        when(getDataRequest.getNonce()).thenReturn(1);
        when(getDataRequest.getExcludedKeys()).thenReturn(knownKeysSet);

        Connection connection = mock(Connection.class);
        when(connection.noCapabilityRequiredOrCapabilityIsSupported(any(Proto.class))).thenReturn(true);

        GetDataRequestHandler handler =
                new GetDataRequestHandler(this.networkNode, this.testState.getMockedStorage(), null);
        handler.handle(getDataRequest, connection);

        // Verify the request node is sent back only the 2 missing payloads
        GetDataResponse getDataResponse = captor.getValue();
        Assert.assertEquals(getDataResponse.getRequestNonce(), getDataRequest.getNonce());
        Assert.assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
        Assert.assertEquals(getDataResponse.getRequestNonce(), getDataRequest.getNonce());
        Assert.assertFalse(getDataResponse.isGetUpdatedDataResponse());
        Assert.assertEquals(getDataResponse.getPersistableNetworkPayloadSet(), new HashSet<>(Collections.singletonList(pnp_onSeedNodeNotInRequest)));
        Assert.assertEquals(getDataResponse.getDataSet(), new HashSet<>(Collections.singletonList(pse_onSeedNodeNotInRequest)));
    }

    // TESTCASE: Same as above, but with an GetUpdatedDataRequest
    @Test
    public void handle_GetUpdatedDataRequestTest() throws NoSuchAlgorithmException {

        // Construct a seed node w/ 2 PersistableNetworkPayloads and 2 ProtectedStorageEntrys and a PreliminaryGetDataRequest
        // that only contains 1 known PersistableNetworkPayload and 1 known ProtectedStorageEntry as well as 2 unknowns
        PersistableNetworkPayload pnp_onSeedNodeNotInRequest = new PersistableNetworkPayloadStub(new byte[] { 1 });
        PersistableNetworkPayload pnp_onSeedNodeAndInRequest = new PersistableNetworkPayloadStub(new byte[] { 2 });
        PersistableNetworkPayload pnp_onlyInRequest = new PersistableNetworkPayloadStub(new byte[] { 3 });
        ProtectedStorageEntry pse_onSeedNodeNotInRequest = getProtectedStorageEntryForAdd();
        ProtectedStorageEntry pse_onSeedNodeAndInRequest = getProtectedStorageEntryForAdd();
        ProtectedStorageEntry pse_onlyInRequest = getProtectedStorageEntryForAdd();

        this.testState.getMockedStorage().addPersistableNetworkPayload(
                pnp_onSeedNodeNotInRequest, this.localNodeAddress, false, false, false, false);
        this.testState.getMockedStorage().addPersistableNetworkPayload(
                pnp_onSeedNodeAndInRequest, this.localNodeAddress, false, false, false, false);
        this.testState.getMockedStorage().addProtectedStorageEntry(pse_onSeedNodeNotInRequest, this.localNodeAddress, null, false);
        this.testState.getMockedStorage().addProtectedStorageEntry(pse_onSeedNodeAndInRequest, this.localNodeAddress, null, false);

        SettableFuture<Connection> sendFuture = mock(SettableFuture.class);
        final ArgumentCaptor<GetDataResponse> captor = ArgumentCaptor.forClass(GetDataResponse.class);
        when(this.networkNode.sendMessage(any(Connection.class), captor.capture())).thenReturn(sendFuture);

        GetUpdatedDataRequest getDataRequest = mock(GetUpdatedDataRequest.class);
        HashSet<byte[]> knownKeysSet = new HashSet<>(Arrays.asList(
                pnp_onSeedNodeAndInRequest.getHash(),
                pnp_onlyInRequest.getHash(),
                P2PDataStorage.get32ByteHash(pse_onSeedNodeAndInRequest.getProtectedStoragePayload()),
                P2PDataStorage.get32ByteHash(pse_onlyInRequest.getProtectedStoragePayload())));
        when(getDataRequest.getNonce()).thenReturn(1);
        when(getDataRequest.getExcludedKeys()).thenReturn(knownKeysSet);

        Connection connection = mock(Connection.class);
        when(connection.noCapabilityRequiredOrCapabilityIsSupported(any(Proto.class))).thenReturn(true);

        GetDataRequestHandler handler =
                new GetDataRequestHandler(this.networkNode, this.testState.getMockedStorage(), null);
        handler.handle(getDataRequest, connection);

        // Verify the request node is sent back only the 2 missing payloads
        GetDataResponse getDataResponse = captor.getValue();
        Assert.assertEquals(getDataResponse.getRequestNonce(), getDataRequest.getNonce());
        Assert.assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
        Assert.assertEquals(getDataResponse.getRequestNonce(), getDataRequest.getNonce());
        Assert.assertTrue(getDataResponse.isGetUpdatedDataResponse());
        Assert.assertEquals(getDataResponse.getPersistableNetworkPayloadSet(), new HashSet<>(Collections.singletonList(pnp_onSeedNodeNotInRequest)));
        Assert.assertEquals(getDataResponse.getDataSet(), new HashSet<>(Collections.singletonList(pse_onSeedNodeNotInRequest)));
    }
}
