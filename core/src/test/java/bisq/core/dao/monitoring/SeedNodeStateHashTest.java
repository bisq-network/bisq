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

package bisq.core.dao.monitoring;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.monitoring.model.DaoStateHash;
import bisq.core.dao.monitoring.network.DaoStateNetworkService;
import bisq.core.dao.monitoring.network.messages.GetDaoStateHashesRequest;
import bisq.core.dao.monitoring.network.messages.GetDaoStateHashesResponse;
import bisq.core.dao.monitoring.network.messages.NewDaoStateHashMessage;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.GenesisTxInfo;
import bisq.core.dao.state.model.DaoState;
import bisq.core.dao.state.storage.DaoStateStorageService;
import bisq.core.network.p2p.seed.DefaultSeedNodeRepository;
import bisq.core.user.Preferences;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.InboundConnection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.OutboundConnection;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.ClockWatcher;
import bisq.common.config.Config;
import bisq.common.crypto.Hash;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.util.Utilities;

import com.google.common.util.concurrent.SettableFuture;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A peer can claim any node address on an inbound connection, so a DAO state hash counts as the hash of a seed node
 * only on an outbound connection to that seed node.
 */
public class SeedNodeStateHashTest {
    private static final NodeAddress SEED_NODE = new NodeAddress("seednode.onion", 8000);
    private static final int GENESIS_HEIGHT = 100;
    private static final int SNAPSHOT_HEIGHT = 110;
    private static final int CHAIN_HEIGHT = 115;
    private static final String NETWORK = "network";
    private static final String FORK = "fork";

    private final List<MessageListener> messageListeners = new CopyOnWriteArrayList<>();
    private final List<GetDaoStateHashesRequest> requests = new CopyOnWriteArrayList<>();
    private NetworkNode networkNode;
    private PeerManager peerManager;
    private DaoStateService daoStateService;
    private DaoStateMonitoringService monitor;
    private DaoFacade daoFacade;
    private Runnable createSnapshotHandler;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp(@TempDir Path appDataDir) {
        networkNode = mock(NetworkNode.class);
        doAnswer(invocation -> messageListeners.add(invocation.getArgument(0)))
                .when(networkNode).addMessageListener(any());
        doAnswer(invocation -> messageListeners.remove(invocation.getArgument(0)))
                .when(networkNode).removeMessageListener(any());
        doAnswer(invocation -> send(invocation.getArgument(1)))
                .when(networkNode).sendMessage(any(NodeAddress.class), any(NetworkEnvelope.class));
        doAnswer(invocation -> send(invocation.getArgument(1)))
                .when(networkNode).sendMessage(any(NodeAddress.class), any(NetworkEnvelope.class), anyBoolean());

        SeedNodeRepository seedNodeRepository = new DefaultSeedNodeRepository(new Config(
                "--" + Config.APP_DATA_DIR + "=" + appDataDir,
                "--" + Config.SEED_NODES + "=" + SEED_NODE.getFullAddress()));
        peerManager = new PeerManager(networkNode, seedNodeRepository, new ClockWatcher(),
                mock(PersistenceManager.class), 12);

        GenesisTxInfo genesisTxInfo = mock(GenesisTxInfo.class);
        when(genesisTxInfo.getGenesisBlockHeight()).thenReturn(GENESIS_HEIGHT);
        daoStateService = new DaoStateService(new DaoState(), genesisTxInfo, null);
        DaoStateStorageService storage = mock(DaoStateStorageService.class);
        monitor = new DaoStateMonitoringService(daoStateService, storage,
                new DaoStateNetworkService(networkNode, peerManager, mock(Broadcaster.class), seedNodeRepository),
                genesisTxInfo,
                seedNodeRepository, mock(Preferences.class), null, false, false);
        daoFacade = new DaoFacade(
                null, null, null, null, null, daoStateService, monitor, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, storage);
        monitor.addListeners();

        // As in lite mode after a restart: the hash chain ends at the snapshot, the parsed blocks go further
        monitor.applySnapshot(new LinkedList<>(hashes(GENESIS_HEIGHT, SNAPSHOT_HEIGHT, NETWORK)));
        daoStateService.onNewBlockHeight(CHAIN_HEIGHT);
        createSnapshotHandler = mock(Runnable.class);
        monitor.setCreateSnapshotHandler(createSnapshotHandler);
    }

    @AfterEach
    public void tearDown() {
        peerManager.shutDown();
    }

    @Test
    public void hashOnInboundConnectionClaimingSeedNodeDoesNotMarkSeedConflict() {
        daoStateService.onParseBlockChainComplete();

        deliver(new NewDaoStateHashMessage(hash(SNAPSHOT_HEIGHT, FORK)), connection(InboundConnection.class));

        assertFalse(monitor.isInConflictWithSeedNode());
        assertTrue(daoFacade.isDaoStateReadyAndInSync());
    }

    @Test
    public void hashOnInboundConnectionClaimingBannedSeedNodeDoesNotMarkSeedConflict() {
        // PeerManager forgets a seed node address after a ban, but the monitor still counts it as a seed node
        peerManager.onDisconnect(CloseConnectionReason.PEER_BANNED, connection(OutboundConnection.class));
        assertFalse(peerManager.isSeedNode(SEED_NODE));
        daoStateService.onParseBlockChainComplete();

        deliver(new NewDaoStateHashMessage(hash(SNAPSHOT_HEIGHT, FORK)), connection(InboundConnection.class));

        assertFalse(monitor.isInConflictWithSeedNode());
        assertTrue(daoFacade.isDaoStateReadyAndInSync());
    }

    @Test
    public void hashOnOutboundConnectionToSeedNodeMarksSeedConflict() {
        daoStateService.onParseBlockChainComplete();

        deliver(new NewDaoStateHashMessage(hash(SNAPSHOT_HEIGHT, FORK)), connection(OutboundConnection.class));

        assertTrue(monitor.isInConflictWithSeedNode());
        assertFalse(daoFacade.isDaoStateReadyAndInSync());
    }

    @Test
    public void hashRequestToSeedNodeDoesNotUseInboundConnection() {
        Connection claimant = connection(InboundConnection.class);
        when(networkNode.getConfirmedConnections()).thenReturn(Set.of(claimant));

        daoStateService.onParseBlockChainComplete();

        verify(networkNode).sendMessage(eq(SEED_NODE), any(GetDaoStateHashesRequest.class), eq(false));
    }

    @Test
    public void hashResponseOnInboundConnectionClaimingSeedNodeIsIgnored() {
        Connection seedNode = connection(OutboundConnection.class);
        Connection claimant = connection(InboundConnection.class);
        when(networkNode.getConfirmedConnections()).thenReturn(Set.of(seedNode, claimant));

        daoStateService.onParseBlockChainComplete();
        // One request for each connection with the seed node address
        assertEquals(2, requests.size());
        // Even with the nonce of our request a response on an inbound connection neither counts nor ends the request
        requests.forEach(request -> deliver(response(request, FORK), claimant));
        requests.forEach(request -> deliver(response(request, NETWORK), seedNode));

        assertEquals(describe(hashes(GENESIS_HEIGHT, CHAIN_HEIGHT - 1, NETWORK)),
                describe(monitor.getDaoStateHashChain()));
        verify(createSnapshotHandler).run();
        assertFalse(monitor.isInConflictWithSeedNode());
        assertTrue(daoFacade.isDaoStateReadyAndInSync());
    }

    private SettableFuture<Connection> send(NetworkEnvelope networkEnvelope) {
        if (networkEnvelope instanceof GetDaoStateHashesRequest) {
            requests.add((GetDaoStateHashesRequest) networkEnvelope);
        }
        return SettableFuture.create();
    }

    private void deliver(NetworkEnvelope networkEnvelope, Connection connection) {
        messageListeners.forEach(listener -> listener.onMessage(networkEnvelope, connection));
    }

    private static Connection connection(Class<? extends Connection> connectionClass) {
        Connection connection = mock(connectionClass);
        when(connection.getPeersNodeAddressOptional()).thenReturn(Optional.of(SEED_NODE));
        return connection;
    }

    private static GetDaoStateHashesResponse response(GetDaoStateHashesRequest request, String chain) {
        return new GetDaoStateHashesResponse(hashes(request.getHeight(), CHAIN_HEIGHT - 1, chain), request.getNonce());
    }

    private static List<DaoStateHash> hashes(int fromHeight, int toHeight, String chain) {
        return IntStream.rangeClosed(fromHeight, toHeight)
                .mapToObj(height -> hash(height, chain))
                .collect(Collectors.toList());
    }

    private static DaoStateHash hash(int height, String chain) {
        byte[] hash = Hash.getSha256Ripemd160hash((chain + height).getBytes(StandardCharsets.UTF_8));
        return new DaoStateHash(height, hash, false);
    }

    private static List<String> describe(List<DaoStateHash> daoStateHashes) {
        return daoStateHashes.stream()
                .map(daoStateHash -> daoStateHash.getHeight() + ":" + Utilities.encodeToHex(daoStateHash.getHash()))
                .collect(Collectors.toList());
    }
}
