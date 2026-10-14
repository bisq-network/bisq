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

package bisq.network.p2p.peers.peerexchange;

import bisq.network.p2p.MockNode;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.peerexchange.messages.GetPeersRequest;
import bisq.network.p2p.peers.peerexchange.messages.GetPeersResponse;

import bisq.common.app.Capabilities;
import bisq.common.app.Version;

import java.io.IOException;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * A peer reported by another node gets the local time of reception as its date. Dates read from the persisted peer list
 * are limited to the range from 0 to the local time.
 */
class PeerDateTest {
    private static final long NOW = System.currentTimeMillis();
    private static final long IN_TEN_YEARS = NOW + TimeUnit.DAYS.toMillis(3650);
    private static final long FIVE_DAYS_AGO = NOW - TimeUnit.DAYS.toMillis(5);
    private static final long FIFTEEN_DAYS_AGO = NOW - TimeUnit.DAYS.toMillis(15);

    @Test
    void peersReportedInARequestGetTheReceptionTime() {
        long before = System.currentTimeMillis();

        GetPeersRequest request = GetPeersRequest.fromProto(protobuf.GetPeersRequest.newBuilder()
                        .setSenderNodeAddress(new NodeAddress("sender.onion", 9999).toProtoMessage())
                        .setNonce(1)
                        .addAllReportedPeers(peerProtos())
                        .build(),
                Version.getP2PMessageVersion());

        assertDatesBetween(before, System.currentTimeMillis(), request.getReportedPeers());
    }

    // The response path has used the reception time before; the request path now follows it
    @Test
    void peersReportedInAResponseGetTheReceptionTime() {
        long before = System.currentTimeMillis();

        GetPeersResponse response = GetPeersResponse.fromProto(protobuf.GetPeersResponse.newBuilder()
                        .setRequestNonce(1)
                        .addAllReportedPeers(peerProtos())
                        .build(),
                Version.getP2PMessageVersion());

        assertDatesBetween(before, System.currentTimeMillis(), response.getReportedPeers());
    }

    // Earlier versions stored the dates sent in requests
    @Test
    void persistedPeerListLimitsDatesToTheRangeFromZeroToTheLocalTime() {
        long before = System.currentTimeMillis();

        Map<String, Long> dates = datesByHostName(PeerList.fromProto(persistedPeerList()).getSet());

        long after = System.currentTimeMillis();
        assertEquals(hostNames(), dates.keySet());
        assertTrue(dates.get("future.onion") >= before && dates.get("future.onion") <= after);
        assertEquals(0, dates.get("extreme.onion"));
        assertEquals(0, dates.get("negative.onion"));
        assertEquals(FIVE_DAYS_AGO, dates.get("recent.onion"));
        assertEquals(FIFTEEN_DAYS_AGO, dates.get("old.onion"));
    }

    // The peers of a request go through the real peer manager into the reported and persisted peers. A connection fault
    // ages the persisted peers. All reported peers are kept, with the reception time as their date.
    @Test
    void peersOfARequestEnterThePeerListsWithTheReceptionTime() throws IOException {
        MockNode node = new MockNode(2);
        try {
            PeerManager peerManager = node.getPeerManager();
            long before = System.currentTimeMillis();
            GetPeersRequest request = GetPeersRequest.fromProto(protobuf.GetPeersRequest.newBuilder()
                            .setSenderNodeAddress(new NodeAddress("sender.onion", 9999).toProtoMessage())
                            .setNonce(1)
                            .addAllReportedPeers(peerProtos())
                            .build(),
                    Version.getP2PMessageVersion());

            peerManager.addToReportedPeers(request.getReportedPeers(), mock(Connection.class), new Capabilities());
            peerManager.handleConnectionFault(new NodeAddress("unrelated.onion", 9999));

            long after = System.currentTimeMillis();
            assertEquals(hostNames(), datesByHostName(peerManager.getReportedPeers()).keySet());
            assertEquals(hostNames(), datesByHostName(peerManager.getPersistedPeers()).keySet());
            assertDatesBetween(before, after, peerManager.getReportedPeers());
            assertDatesBetween(before, after, peerManager.getPersistedPeers());
        } finally {
            node.getPersistenceManager().shutdown();
        }
    }

    // A persisted peer with a negative date ages out at the next check, as one older than 14 days does. The peer dated
    // in the future stays with the local time as its date, and a recent peer stays unchanged.
    @Test
    void persistedPeersWithLimitedDatesAgeOut() throws IOException {
        MockNode node = new MockNode(2);
        try {
            PeerManager peerManager = node.getPeerManager();
            peerManager.getPersistedPeers().addAll(PeerList.fromProto(persistedPeerList()).getSet());

            peerManager.handleConnectionFault(new NodeAddress("unrelated.onion", 9999));

            Map<String, Long> dates = datesByHostName(peerManager.getPersistedPeers());
            assertEquals(Set.of("future.onion", "recent.onion"), dates.keySet());
            assertTrue(dates.get("future.onion") <= System.currentTimeMillis());
            assertEquals(FIVE_DAYS_AGO, dates.get("recent.onion"));
        } finally {
            node.getPersistenceManager().shutdown();
        }
    }

    private static Set<protobuf.Peer> peerProtos() {
        return Set.of(peerProto("future.onion", IN_TEN_YEARS),
                peerProto("extreme.onion", Long.MIN_VALUE),
                peerProto("negative.onion", -1),
                peerProto("recent.onion", FIVE_DAYS_AGO),
                peerProto("old.onion", FIFTEEN_DAYS_AGO));
    }

    private static Set<String> hostNames() {
        return peerProtos().stream().map(proto -> proto.getNodeAddress().getHostName()).collect(Collectors.toSet());
    }

    private static protobuf.PeerList persistedPeerList() {
        return protobuf.PeerList.newBuilder().addAllPeer(peerProtos()).build();
    }

    private static protobuf.Peer peerProto(String hostName, long date) {
        return protobuf.Peer.newBuilder()
                .setNodeAddress(new NodeAddress(hostName, 9999).toProtoMessage())
                .setDate(date)
                .build();
    }

    private static void assertDatesBetween(long from, long to, Collection<Peer> peers) {
        assertEquals(5, peers.size());
        peers.forEach(peer -> assertTrue(peer.getDateAsLong() >= from && peer.getDateAsLong() <= to,
                "date of " + peer.getNodeAddress() + ": " + peer.getDateAsLong()));
    }

    private static Map<String, Long> datesByHostName(Collection<Peer> peers) {
        return peers.stream().collect(Collectors.toMap(peer -> peer.getNodeAddress().getHostName(),
                Peer::getDateAsLong));
    }
}
