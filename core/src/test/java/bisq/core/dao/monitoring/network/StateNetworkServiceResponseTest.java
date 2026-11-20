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

package bisq.core.dao.monitoring.network;

import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.monitoring.network.messages.GetDaoStateHashesResponse;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.proto.network.NetworkEnvelope;

import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StateNetworkServiceResponseTest {
    // Height of the DAO genesis block on mainnet
    private static final int GENESIS_HEIGHT = 571_747;

    private final NetworkNode networkNode = mock(NetworkNode.class);
    private DaoStateNetworkService service;

    @BeforeEach
    void setUp() {
        when(networkNode.sendMessage(any(Connection.class), any(NetworkEnvelope.class)))
                .thenReturn(SettableFuture.create());
        service = new DaoStateNetworkService(networkNode,
                mock(PeerManager.class),
                mock(Broadcaster.class),
                mock(SeedNodeRepository.class));
    }

    @Test
    void sendsTheStateHashesOfTheHighestHeightsUpToTheLimit() {
        int limit = StateNetworkService.MAX_STATE_HASHES_PER_RESPONSE;

        GetDaoStateHashesResponse response = sendResponse(stateHashesFromGenesis(limit + 5));

        assertEquals(limit, response.getStateHashes().size());
        assertEquals(GENESIS_HEIGHT + 5, response.getStateHashes().get(0).getHeight());
        assertEquals(GENESIS_HEIGHT + limit + 4, response.getStateHashes().get(limit - 1).getHeight());
    }

    @Test
    void sortsTheStateHashesByHeight() {
        int limit = StateNetworkService.MAX_STATE_HASHES_PER_RESPONSE;
        List<DaoStateHash> stateHashes = new ArrayList<>(stateHashesFromGenesis(limit + 5));
        Collections.reverse(stateHashes);

        GetDaoStateHashesResponse response = sendResponse(stateHashes);

        assertEquals(IntStream.range(5, limit + 5).mapToObj(i -> GENESIS_HEIGHT + i).collect(Collectors.toList()),
                response.getStateHashes().stream().map(DaoStateHash::getHeight).collect(Collectors.toList()));
    }

    @Test
    void sendsAllStateHashesBelowTheLimit() {
        assertEquals(10, sendResponse(stateHashesFromGenesis(10)).getStateHashes().size());
    }

    // A peer drops a message above the maximum size, so a full response must stay below it
    @Test
    void fullResponseFitsTheMaximumMessageSize() {
        GetDaoStateHashesResponse response = sendResponse(
                stateHashesFromGenesis(StateNetworkService.MAX_STATE_HASHES_PER_RESPONSE));

        assertTrue(response.toProtoNetworkEnvelope().getSerializedSize() < Connection.getMaxPermittedMessageSize());
    }

    private static List<DaoStateHash> stateHashesFromGenesis(int numStateHashes) {
        return IntStream.range(0, numStateHashes)
                .mapToObj(i -> new DaoStateHash(GENESIS_HEIGHT + i, new byte[20], true))
                .collect(Collectors.toList());
    }

    private GetDaoStateHashesResponse sendResponse(List<DaoStateHash> stateHashes) {
        service.sendGetStateHashesResponse(mock(Connection.class), 1, stateHashes);

        ArgumentCaptor<NetworkEnvelope> captor = ArgumentCaptor.forClass(NetworkEnvelope.class);
        verify(networkNode).sendMessage(any(Connection.class), captor.capture());
        return (GetDaoStateHashesResponse) captor.getValue();
    }
}
