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

package bisq.core.network.p2p.inventory;

import bisq.core.dao.monitoring.BlindVoteStateMonitoringService;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.monitoring.ProposalStateMonitoringService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.FilterManager;
import bisq.core.network.p2p.inventory.messages.GetInventoryRequest;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.storage.P2PDataStorage;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetInventoryRequestHandlerTest {
    private final P2PDataStorage p2PDataStorage = mock(P2PDataStorage.class);
    private final MutableClock clock = new MutableClock();
    private GetInventoryRequestHandler handler;

    @BeforeEach
    void setUp() {
        when(p2PDataStorage.getMapForDataResponse(any())).thenReturn(Map.of());
        when(p2PDataStorage.getMap()).thenReturn(Map.of());
        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getBlocks()).thenReturn(List.of());
        DaoStateMonitoringService daoStateMonitoringService = mock(DaoStateMonitoringService.class);
        when(daoStateMonitoringService.getDaoStateBlockChain()).thenReturn(new LinkedList<>());
        ProposalStateMonitoringService proposalStateMonitoringService = mock(ProposalStateMonitoringService.class);
        when(proposalStateMonitoringService.getProposalStateBlockChain()).thenReturn(new LinkedList<>());
        BlindVoteStateMonitoringService blindVoteStateMonitoringService = mock(BlindVoteStateMonitoringService.class);
        when(blindVoteStateMonitoringService.getBlindVoteStateBlockChain()).thenReturn(new LinkedList<>());

        handler = new GetInventoryRequestHandler(mock(NetworkNode.class),
                mock(PeerManager.class),
                p2PDataStorage,
                daoStateService,
                daoStateMonitoringService,
                proposalStateMonitoringService,
                blindVoteStateMonitoringService,
                mock(FilterManager.class),
                clock,
                12);
    }

    @Test
    void countsTheDataObjectsAtMostOnceInTheInterval() {
        request("1.10.12");
        clock.advance(GetInventoryRequestHandler.DATA_OBJECTS_COUNT_INTERVAL.toMillis() - 1);
        request("1.0.0");

        verify(p2PDataStorage, times(1)).getMapForDataResponse(any());
    }

    @Test
    void countsTheDataObjectsAgainAfterTheInterval() {
        request("1.10.12");
        clock.advance(GetInventoryRequestHandler.DATA_OBJECTS_COUNT_INTERVAL.toMillis());
        request("1.10.12");

        verify(p2PDataStorage, times(2)).getMapForDataResponse(any());
    }

    @Test
    void countsTheDataObjectsAgainIfTheClockWentBack() {
        request("1.10.12");
        clock.advance(-1);
        request("1.10.12");

        verify(p2PDataStorage, times(2)).getMapForDataResponse(any());
    }

    private void request(String version) {
        handler.onMessage(new GetInventoryRequest(version), mock(Connection.class));
    }

    private static final class MutableClock extends Clock {
        private long millis = 1_000_000;

        void advance(long millis) {
            this.millis += millis;
        }

        @Override
        public long millis() {
            return millis;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
