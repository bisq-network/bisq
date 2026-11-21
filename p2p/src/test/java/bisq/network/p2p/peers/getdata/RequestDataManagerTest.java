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
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import bisq.network.p2p.seed.SeedNodeRepository;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.mocks.AppendOnlyDataStoreServiceFake;
import bisq.network.p2p.storage.mocks.ClockFake;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.SeedNodeOnlyInitialDataResponsePayload;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;
import bisq.network.p2p.storage.persistence.RemovedPayloadsService;
import bisq.network.p2p.storage.persistence.ResourceDataStoreService;

import bisq.common.app.Capabilities;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.network.GetDataResponsePriority;

import com.google.common.util.concurrent.SettableFuture;

import javafx.beans.property.SimpleObjectProperty;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RequestDataManagerTest {
    private static final NodeAddress MY_NODE_ADDRESS = new NodeAddress("me", 8080);
    private static final NodeAddress SEED_NODE_ADDRESS = new NodeAddress("seed", 8000);
    private static final NodeAddress PEER_NODE_ADDRESS = new NodeAddress("peer", 8080);

    // A requester drops the seed node only payloads of a node which is no seed node, so they must not cause the
    // response to be truncated. Otherwise the requester repeats the request and gets the same response again.
    @Test
    public void nonSeedNodeLeavesOutSeedNodeOnlyPayloadsBeforeTruncation() {
        PersistableNetworkPayload payload = new PersistableNetworkPayloadStub(new byte[]{1});
        List<PersistableNetworkPayload> seedNodeOnlyPayloads = List.of(
                new SeedNodeOnlyPersistableNetworkPayloadStub(new byte[]{2}),
                new SeedNodeOnlyPersistableNetworkPayloadStub(new byte[]{3}));

        GetDataResponse getDataResponse = respond(false, 2, List.of(payload), seedNodeOnlyPayloads);

        assertEquals(Set.of(payload), getDataResponse.getPersistableNetworkPayloadSet());
        assertFalse(getDataResponse.isWasTruncated());
    }

    @Test
    public void nonSeedNodeReportsTruncationOfPayloadsWhichTheRequesterKeeps() {
        List<PersistableNetworkPayload> payloads = List.of(
                new PersistableNetworkPayloadStub(new byte[]{1}),
                new PersistableNetworkPayloadStub(new byte[]{2}),
                new PersistableNetworkPayloadStub(new byte[]{3}));
        List<PersistableNetworkPayload> seedNodeOnlyPayloads = List.of(
                new SeedNodeOnlyPersistableNetworkPayloadStub(new byte[]{4}));

        GetDataResponse getDataResponse = respond(false, 2, payloads, seedNodeOnlyPayloads);

        assertEquals(2, getDataResponse.getPersistableNetworkPayloadSet().size());
        assertTrue(payloads.containsAll(getDataResponse.getPersistableNetworkPayloadSet()));
        assertTrue(getDataResponse.isWasTruncated());
    }

    @Test
    public void seedNodeSendsSeedNodeOnlyPayloads() {
        PersistableNetworkPayload payload = new PersistableNetworkPayloadStub(new byte[]{1});
        List<PersistableNetworkPayload> seedNodeOnlyPayloads = List.of(
                new SeedNodeOnlyPersistableNetworkPayloadStub(new byte[]{2}),
                new SeedNodeOnlyPersistableNetworkPayloadStub(new byte[]{3}));

        GetDataResponse getDataResponse = respond(true, 2, List.of(payload), seedNodeOnlyPayloads);

        assertEquals(Set.copyOf(seedNodeOnlyPayloads), getDataResponse.getPersistableNetworkPayloadSet());
        assertTrue(getDataResponse.isWasTruncated());
    }

    private static GetDataResponse respond(boolean isSeedNode,
                                           int maxEntries,
                                           List<PersistableNetworkPayload> payloads,
                                           List<PersistableNetworkPayload> seedNodeOnlyPayloads) {
        P2PDataStorage dataStorage = createDataStorage();
        payloads.forEach(payload -> dataStorage.addPersistableNetworkPayload(payload, null, false));
        seedNodeOnlyPayloads.forEach(payload -> dataStorage.addPersistableNetworkPayload(payload, null, false));

        NetworkNode networkNode = mock(NetworkNode.class);
        SimpleObjectProperty<NodeAddress> nodeAddressProperty = new SimpleObjectProperty<>();
        when(networkNode.nodeAddressProperty()).thenReturn(nodeAddressProperty);
        // The response is not sent, so the handler does not complete.
        when(networkNode.sendMessage(any(Connection.class), any(GetDataResponse.class)))
                .thenReturn(SettableFuture.create());
        SeedNodeRepository seedNodeRepository = mock(SeedNodeRepository.class);
        when(seedNodeRepository.getSeedNodeAddresses()).thenReturn(Set.of(SEED_NODE_ADDRESS));
        when(seedNodeRepository.isSeedNode(MY_NODE_ADDRESS)).thenReturn(isSeedNode);

        RequestDataManager requestDataManager = new RequestDataManager(networkNode,
                seedNodeRepository,
                dataStorage,
                mock(PeerManager.class),
                maxEntries);
        nodeAddressProperty.set(MY_NODE_ADDRESS);

        Connection connection = mock(Connection.class);
        when(connection.getUid()).thenReturn("uid");
        when(connection.getCapabilities()).thenReturn(new Capabilities());
        when(connection.getPeersNodeAddressOptional()).thenReturn(Optional.of(PEER_NODE_ADDRESS));

        requestDataManager.onMessage(new GetUpdatedDataRequest(PEER_NODE_ADDRESS, 1, Collections.emptySet()),
                connection);

        ArgumentCaptor<GetDataResponse> getDataResponseCaptor = ArgumentCaptor.forClass(GetDataResponse.class);
        verify(networkNode).sendMessage(eq(connection), getDataResponseCaptor.capture());
        return getDataResponseCaptor.getValue();
    }

    @SuppressWarnings("unchecked")
    private static P2PDataStorage createDataStorage() {
        return new P2PDataStorage(mock(NetworkNode.class),
                mock(Broadcaster.class),
                new AppendOnlyDataStoreServiceFake(),
                new ProtectedDataStoreService(),
                mock(ResourceDataStoreService.class),
                mock(PersistenceManager.class),
                mock(RemovedPayloadsService.class),
                new ClockFake(),
                5,
                P2PDataStorage.getDefaultMaxSize());
    }

    // Like AccountAgeWitness and SignedWitness, which a response contains before the payloads of low priority
    static class SeedNodeOnlyPersistableNetworkPayloadStub extends PersistableNetworkPayloadStub
            implements SeedNodeOnlyInitialDataResponsePayload {

        SeedNodeOnlyPersistableNetworkPayloadStub(byte[] hash) {
            super(hash);
        }

        @Override
        public GetDataResponsePriority getGetDataResponsePriority() {
            return GetDataResponsePriority.MID;
        }
    }
}
