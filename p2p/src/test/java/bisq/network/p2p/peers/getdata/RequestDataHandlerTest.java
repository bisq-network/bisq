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
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.SeedNodeOnlyInitialDataResponsePayload;

import bisq.common.proto.network.NetworkEnvelope;

import com.google.common.util.concurrent.SettableFuture;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RequestDataHandlerTest {
    private static final NodeAddress PEER_NODE_ADDRESS = new NodeAddress("peer", 8080);

    @Test
    public void onMessage_nonSeedResponseFiltersSeedNodeOnlyPayloadsBeforeStorage() {
        NetworkNode networkNode = mock(NetworkNode.class);
        P2PDataStorage dataStorage = mock(P2PDataStorage.class);
        PeerManager peerManager = mock(PeerManager.class);
        RequestDataHandler.Listener listener = mock(RequestDataHandler.Listener.class);

        AtomicInteger requestNonce = new AtomicInteger();
        when(dataStorage.buildPreliminaryGetDataRequest(anyInt())).thenAnswer(invocation -> {
            int nonce = invocation.getArgument(0);
            requestNonce.set(nonce);
            return new PreliminaryGetDataRequest(nonce, Collections.emptySet());
        });

        SettableFuture<Connection> sendFuture = SettableFuture.create();
        sendFuture.set(mock(Connection.class));
        when(networkNode.sendMessage(eq(PEER_NODE_ADDRESS), any(NetworkEnvelope.class))).thenReturn(sendFuture);

        RequestDataHandler requestDataHandler = new RequestDataHandler(networkNode, dataStorage, peerManager, listener);
        requestDataHandler.requestData(PEER_NODE_ADDRESS, true, false);

        PersistableNetworkPayload normalPayload = new PersistableNetworkPayloadStub(new byte[]{1});
        PersistableNetworkPayload seedNodeOnlyPayload = new SeedNodeOnlyPersistableNetworkPayloadStub(new byte[]{2});
        GetDataResponse getDataResponse = new GetDataResponse(
                Collections.emptySet(),
                Set.of(normalPayload, seedNodeOnlyPayload),
                requestNonce.get(),
                false,
                true);
        Connection connection = mock(Connection.class);
        when(connection.getPeersNodeAddressOptional()).thenReturn(Optional.of(PEER_NODE_ADDRESS));

        requestDataHandler.onMessage(getDataResponse, connection);

        ArgumentCaptor<GetDataResponse> getDataResponseCaptor = ArgumentCaptor.forClass(GetDataResponse.class);
        verify(dataStorage).processGetDataResponse(getDataResponseCaptor.capture(), eq(PEER_NODE_ADDRESS), eq(false));
        GetDataResponse filteredGetDataResponse = getDataResponseCaptor.getValue();

        assertEquals(1, filteredGetDataResponse.getPersistableNetworkPayloadSet().size());
        assertTrue(filteredGetDataResponse.getPersistableNetworkPayloadSet().contains(normalPayload));
        assertFalse(filteredGetDataResponse.getPersistableNetworkPayloadSet().contains(seedNodeOnlyPayload));
        verify(listener).onComplete(true);
    }

    static class SeedNodeOnlyPersistableNetworkPayloadStub extends PersistableNetworkPayloadStub
            implements SeedNodeOnlyInitialDataResponsePayload {

        SeedNodeOnlyPersistableNetworkPayloadStub(byte[] hash) {
            super(hash);
        }
    }
}
