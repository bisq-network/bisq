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
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.storage.TestState;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.mocks.ProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.ProcessOncePersistableNetworkPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;

import com.google.common.util.concurrent.SettableFuture;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;



import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class GetDataRequestOnMessageTest {
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

    static class LazyPersistableNetworkPayloadStub extends PersistableNetworkPayloadStub
                                                   implements ProcessOncePersistableNetworkPayload {

        LazyPersistableNetworkPayloadStub(byte[] hash) {
            super(hash);
        }
    }

    // TESTCASE: GetDataResponse processing. This large tests includes all interesting variations of state
    @Test
    public void onMessage_GetDataResponseTest() throws NoSuchAlgorithmException {

        PersistableNetworkPayload pnp_onLocalNodeOnly = new PersistableNetworkPayloadStub(new byte[] { 1 });
        PersistableNetworkPayload pnp_inRequestOnly = new LazyPersistableNetworkPayloadStub(new byte[]{2});
        PersistableNetworkPayload pnp_onLocalNodeAndRequest = new PersistableNetworkPayloadStub(new byte[] { 3 });
        ProtectedStorageEntry pse_onLocalNodeOnly = getProtectedStorageEntryForAdd();
        ProtectedStorageEntry pse_inRequestOnly = getProtectedStorageEntryForAdd();
        ProtectedStorageEntry pse_onLocalNodeAndRequest = getProtectedStorageEntryForAdd();

        this.testState.getMockedStorage().addPersistableNetworkPayload(
                pnp_onLocalNodeOnly, this.localNodeAddress, false, false, false, false);
        this.testState.getMockedStorage().addPersistableNetworkPayload(
                pnp_onLocalNodeAndRequest, this.localNodeAddress, false, false, false, false);
        this.testState.getMockedStorage().addProtectedStorageEntry(pse_onLocalNodeOnly, this.localNodeAddress, null, false);
        this.testState.getMockedStorage().addProtectedStorageEntry(pse_onLocalNodeAndRequest, this.localNodeAddress, null, false);

        RequestDataHandler handler = new RequestDataHandler(
                this.networkNode,
                this.testState.getMockedStorage(),
                mock(PeerManager.class),
                mock(RequestDataHandler.Listener.class)
        );

        GetDataResponse getDataResponse = new GetDataResponse(
                new HashSet<>(Arrays.asList(pse_inRequestOnly, pse_onLocalNodeAndRequest)),
                new HashSet<>(Arrays.asList(pnp_inRequestOnly, pnp_onLocalNodeAndRequest)),
                handler.nonce, false);

        NodeAddress peersNodeAddress = new NodeAddress("peer", 10);

        // Make a request with the sole reason to set the peersNodeAddress
        SettableFuture<Connection> sendFuture = mock(SettableFuture.class);
        when(networkNode.sendMessage(any(NodeAddress.class), any(GetDataRequest.class))).thenReturn(sendFuture);
        handler.requestData(peersNodeAddress, true);

        Connection connection = mock(Connection.class);
        when(connection.getPeersNodeAddressOptional()).thenReturn(Optional.of(peersNodeAddress));
            handler.onMessage(getDataResponse, connection);

        Assert.assertEquals(3, this.testState.getMockedStorage().getMap().size());
        Assert.assertTrue(this.testState.getMockedStorage().getAppendOnlyDataStoreMap().containsValue(pnp_onLocalNodeOnly));
        Assert.assertTrue(this.testState.getMockedStorage().getAppendOnlyDataStoreMap().containsValue(pnp_inRequestOnly));
        Assert.assertTrue(this.testState.getMockedStorage().getAppendOnlyDataStoreMap().containsValue(pnp_onLocalNodeAndRequest));

        Assert.assertEquals(3, this.testState.getMockedStorage().getMap().size());
        Assert.assertTrue(this.testState.getMockedStorage().getMap().containsValue(pse_onLocalNodeOnly));
        Assert.assertTrue(this.testState.getMockedStorage().getMap().containsValue(pse_inRequestOnly));
        Assert.assertTrue(this.testState.getMockedStorage().getMap().containsValue(pse_onLocalNodeAndRequest));
    }
}
