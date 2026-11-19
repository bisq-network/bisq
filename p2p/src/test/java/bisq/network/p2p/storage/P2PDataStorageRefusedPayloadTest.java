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
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.messages.BroadcastMessage;
import bisq.network.p2p.storage.mocks.ClockFake;
import bisq.network.p2p.storage.mocks.MapStoreServiceFake;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;
import bisq.network.p2p.storage.persistence.RemovedPayloadsService;
import bisq.network.p2p.storage.persistence.ResourceDataStoreService;

import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistablePayload;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// A store can refuse a payload, as the store of the trade statistics does for an invalid statistic and at capacity.
// A refused payload is not announced to the listeners and not relayed.
class P2PDataStorageRefusedPayloadTest {
    private final PersistableNetworkPayload payload = new PersistableNetworkPayloadStub(true);
    private Broadcaster broadcaster;
    private AppendOnlyDataStoreListener listener;
    private P2PDataStorage storage;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        broadcaster = mock(Broadcaster.class);
        listener = mock(AppendOnlyDataStoreListener.class);
        AppendOnlyDataStoreService appendOnlyDataStoreService = new AppendOnlyDataStoreService();
        appendOnlyDataStoreService.addService(new MapStoreServiceFake() {
            @Override
            protected boolean addIfAbsent(P2PDataStorage.ByteArray hash, PersistablePayload payload) {
                return false;
            }
        });
        storage = new P2PDataStorage(mock(NetworkNode.class),
                broadcaster,
                appendOnlyDataStoreService,
                new ProtectedDataStoreService(),
                mock(ResourceDataStoreService.class),
                mock(PersistenceManager.class),
                mock(RemovedPayloadsService.class),
                new ClockFake(),
                TestState.MAX_SEQUENCE_NUMBER_MAP_SIZE_BEFORE_PURGE,
                P2PDataStorage.getDefaultMaxSize());
        storage.addAppendOnlyDataStoreListener(listener);
    }

    @Test
    void refusedPayloadOfTheClientApiIsNeitherAnnouncedNorRelayed() {
        assertFalse(storage.addPersistableNetworkPayload(payload, TestState.getTestNodeAddress(), true));

        verifyNeitherAnnouncedNorRelayed();
    }

    @Test
    void refusedPayloadOfAPeerIsNeitherAnnouncedNorRelayed() {
        Connection connection = mock(Connection.class);
        when(connection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

        storage.onMessage(new AddPersistableNetworkPayloadMessage(payload), connection);

        verifyNeitherAnnouncedNorRelayed();
    }

    private void verifyNeitherAnnouncedNorRelayed() {
        verify(listener, never()).onAdded(any());
        verify(broadcaster, never()).broadcast(any(BroadcastMessage.class), nullable(NodeAddress.class));
        assertFalse(storage.appendOnlyDataStoreService.getMap(payload)
                .containsKey(new P2PDataStorage.ByteArray(payload.getHash())));
    }
}
