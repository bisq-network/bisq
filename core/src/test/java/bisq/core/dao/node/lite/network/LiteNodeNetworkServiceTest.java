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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.lite.network;

import bisq.core.dao.node.full.RawBlock;
import bisq.core.dao.node.lite.DaoBlockSignatureVerifier;
import bisq.core.dao.node.messages.DaoBlockSignature;
import bisq.core.dao.node.messages.GetBlocksRequest;
import bisq.core.dao.node.messages.GetBlocksResponse;
import bisq.core.dao.node.messages.NewBlockBroadcastMessage;
import bisq.core.dao.node.messages.SignedRawBlock;
import bisq.core.dao.state.DaoStateService;
import bisq.core.testutil.ManualTimer;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.UserThread;

import com.google.common.util.concurrent.SettableFuture;

import java.io.IOException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.jetbrains.annotations.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class LiteNodeNetworkServiceTest {
    private static final Set<NodeAddress> SEED_NODES = Set.of(new NodeAddress("seed1.onion:8000"),
            new NodeAddress("seed2.onion:8000"));

    @Mock
    private DaoStateService daoStateService;

    @BeforeEach
    void setUp() {
        // The request handler defers a step with UserThread.execute. The tests expect the direct executor, also if an
        // earlier test left another one.
        UserThread.resetForTests();
        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
    }

    @AfterEach
    void tearDown() {
        ManualTimer.clear();
        UserThread.resetForTests();
    }

    @Test
    void ignoresNewBlockBroadcastMessageWhenSignedAndUnsignedBlockHashesDiffer(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        doReturn(Set.of()).when(seedNodeRepository).getSeedNodeAddresses();

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier, daoStateService);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        RawBlock unsignedBlock = createRawBlock(100, "unsigned-hash");
        RawBlock differentBlock = createRawBlock(100, "tampered-hash");
        SignedRawBlock signedRawBlock = new SignedRawBlock(differentBlock,
                new DaoBlockSignature(new NodeAddress("signer.onion:8000"), new byte[]{0x01}));

        service.onMessage(new NewBlockBroadcastMessage(unsignedBlock, signedRawBlock), connection);

        assertEquals(0, listener.newBlockCount.get());
        verify(broadcaster, never()).broadcast(any(NewBlockBroadcastMessage.class), any());
    }

    @Test
    void ignoresNewBlockBroadcastMessageWhenSignedAndUnsignedBlockContentDiffers(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        doReturn(Set.of()).when(seedNodeRepository).getSeedNodeAddresses();

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier, daoStateService);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        // Both blocks report the same Bitcoin block hash but have different DAO tx content.
        RawBlock unsignedBlock = createRawBlock(100, "block-hash", null);
        RawBlock signedBlockContent = createRawBlock(100, "block-hash", "attacker-tx");
        SignedRawBlock signedRawBlock = new SignedRawBlock(signedBlockContent,
                new DaoBlockSignature(new NodeAddress("signer.onion:8000"), new byte[]{0x01}));

        service.onMessage(new NewBlockBroadcastMessage(unsignedBlock, signedRawBlock), connection);

        assertEquals(0, listener.newBlockCount.get());
        verify(broadcaster, never()).broadcast(any(NewBlockBroadcastMessage.class), any());
    }

    @Test
    void relaysNewBlockBroadcastMessageWhenSignedAndUnsignedBlockMatch(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        doReturn(Set.of()).when(seedNodeRepository).getSeedNodeAddresses();
        NodeAddress peerAddress = new NodeAddress("peer.onion:8000");
        doReturn(Optional.of(peerAddress)).when(connection).getPeersNodeAddressOptional();

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier, daoStateService);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        RawBlock rawBlock = createRawBlock(100, "block-hash");
        SignedRawBlock signedRawBlock = new SignedRawBlock(rawBlock,
                new DaoBlockSignature(new NodeAddress("signer.onion:8000"), new byte[]{0x01}));
        NewBlockBroadcastMessage message = new NewBlockBroadcastMessage(rawBlock, signedRawBlock);
        doReturn(true).when(daoBlockSignatureVerifier).isValid(signedRawBlock);

        service.onMessage(message, connection);

        assertEquals(1, listener.newBlockCount.get());
        assertEquals(peerAddress, listener.lastSenderNodeAddress.get());
        assertNotNull(listener.lastNewBlockMessage.get());
        verify(broadcaster, times(1)).broadcast(eq(message), eq(peerAddress));
        verify(daoBlockSignatureVerifier, times(1)).isValid(signedRawBlock);
    }

    @Test
    void ignoresNewBlockBroadcastMessageWhenSignatureIsInvalid(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        doReturn(Set.of()).when(seedNodeRepository).getSeedNodeAddresses();
        RawBlock rawBlock = createRawBlock(100, "block-hash");
        SignedRawBlock signedRawBlock = new SignedRawBlock(rawBlock,
                new DaoBlockSignature(new NodeAddress("signer.onion:8000"), new byte[]{0x01}));
        NewBlockBroadcastMessage message = new NewBlockBroadcastMessage(rawBlock, signedRawBlock);
        doReturn(false).when(daoBlockSignatureVerifier).isValid(signedRawBlock);

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier, daoStateService);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        service.onMessage(message, connection);

        assertEquals(0, listener.newBlockCount.get());
        verify(broadcaster, never()).broadcast(any(NewBlockBroadcastMessage.class), any());
        verify(daoBlockSignatureVerifier, times(1)).isValid(signedRawBlock);
    }

    @Test
    void ignoresUnsignedNewBlockBroadcastMessageAndDoesNotRelayIt(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        doReturn(Set.of()).when(seedNodeRepository).getSeedNodeAddresses();
        NodeAddress peerAddress = new NodeAddress("peer.onion:8000");
        doReturn(Optional.of(peerAddress)).when(connection).getPeersNodeAddressOptional();

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier, daoStateService);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        RawBlock rawBlock = createRawBlock(100, "block-hash");
        NewBlockBroadcastMessage message = new NewBlockBroadcastMessage(rawBlock, null);

        service.onMessage(message, connection);
        service.onMessage(message, connection);

        assertEquals(0, listener.newBlockCount.get());
        verify(broadcaster, never()).broadcast(any(NewBlockBroadcastMessage.class), any());
        verify(daoBlockSignatureVerifier, never()).isValid(any());
    }

    @Test
    void deduplicatesRepeatedNewBlockBroadcastMessage(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        doReturn(Set.of()).when(seedNodeRepository).getSeedNodeAddresses();
        NodeAddress peerAddress = new NodeAddress("peer.onion:8000");
        doReturn(Optional.of(peerAddress)).when(connection).getPeersNodeAddressOptional();

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier, daoStateService);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        RawBlock rawBlock = createRawBlock(100, "block-hash");
        SignedRawBlock signedRawBlock = new SignedRawBlock(rawBlock,
                new DaoBlockSignature(new NodeAddress("signer.onion:8000"), new byte[]{0x01}));
        NewBlockBroadcastMessage message = new NewBlockBroadcastMessage(rawBlock, signedRawBlock);
        doReturn(true).when(daoBlockSignatureVerifier).isValid(signedRawBlock);

        service.onMessage(message, connection);
        service.onMessage(message, connection);

        // Second delivery must be dropped as duplicate before triggering listeners or rebroadcast.
        assertEquals(1, listener.newBlockCount.get());
        verify(broadcaster, times(1)).broadcast(eq(message), eq(peerAddress));
        verify(daoBlockSignatureVerifier, times(1)).isValid(signedRawBlock);
    }

    // A node behind the announced heights accepts more announcements than the window has heights. All of them
    // are in its window, so it must still know each of them.
    @Test
    void acceptsEachAnnouncementInTheWindowOnlyOnce(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier verifier,
            @Mock Connection connection) {
        doReturn(Set.of()).when(seedNodeRepository).getSeedNodeAddresses();
        doReturn(true).when(verifier).isValid(any());
        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster,
                seedNodeRepository, verifier, daoStateService);
        CountingListener listener = new CountingListener();
        service.addListener(listener);
        int numBlocks = LiteNodeNetworkService.MAX_RECENT_BLOCKS + 50;

        for (int round = 0; round < 2; round++) {
            for (int height = 0; height < numBlocks; height++) {
                service.onMessage(newBlockBroadcastMessage(height), connection);
            }
        }

        assertEquals(numBlocks, listener.newBlockCount.get());
        assertEquals(numBlocks, service.getNumReceivedBlocks());
    }

    @Test
    void acceptsTwoBlocksOfTheSameHeightOnceEach(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier verifier,
            @Mock Connection connection) {
        doReturn(Set.of()).when(seedNodeRepository).getSeedNodeAddresses();
        doReturn(true).when(verifier).isValid(any());
        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster,
                seedNodeRepository, verifier, daoStateService);
        CountingListener listener = new CountingListener();
        service.addListener(listener);
        DaoBlockSignature signature = new DaoBlockSignature(new NodeAddress("signer.onion:8000"), new byte[]{1});
        RawBlock block = createRawBlock(7, "block");
        RawBlock otherBlock = createRawBlock(7, "other-block");

        for (int round = 0; round < 2; round++) {
            service.onMessage(new NewBlockBroadcastMessage(block, new SignedRawBlock(block, signature)), connection);
            service.onMessage(new NewBlockBroadcastMessage(otherBlock, new SignedRawBlock(otherBlock, signature)),
                    connection);
        }

        assertEquals(2, listener.newBlockCount.get());
    }

    @Test
    void ignoresAndForgetsAnnouncementsWhichLeftTheWindow(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier verifier,
            @Mock Connection connection) {
        doReturn(Set.of()).when(seedNodeRepository).getSeedNodeAddresses();
        doReturn(true).when(verifier).isValid(any());
        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster,
                seedNodeRepository, verifier, daoStateService);
        CountingListener listener = new CountingListener();
        service.addListener(listener);
        for (int height = 0; height < 10; height++) {
            service.onMessage(newBlockBroadcastMessage(height), connection);
        }
        assertEquals(10, listener.newBlockCount.get());
        assertEquals(10, service.getNumReceivedBlocks());

        // The blocks up to height 4 are now MAX_RECENT_BLOCKS or more behind. Height 5 is the last one in the window.
        int chainHeight = LiteNodeNetworkService.MAX_RECENT_BLOCKS + 4;
        doReturn(chainHeight).when(daoStateService).getChainHeight();
        service.onMessage(newBlockBroadcastMessage(4), connection);
        service.onMessage(newBlockBroadcastMessage(5), connection);
        assertEquals(10, listener.newBlockCount.get());

        DaoBlockSignature signature = new DaoBlockSignature(new NodeAddress("signer.onion:8000"), new byte[]{1});
        RawBlock otherBlock = createRawBlock(5, "other-block");
        service.onMessage(new NewBlockBroadcastMessage(otherBlock, new SignedRawBlock(otherBlock, signature)),
                connection);
        assertEquals(11, listener.newBlockCount.get());
        assertEquals(6, service.getNumReceivedBlocks());
    }

    @Test
    void resetClearsTheHistoryOfAcceptedAnnouncements(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier verifier,
            @Mock Connection connection) {
        doReturn(Set.of()).when(seedNodeRepository).getSeedNodeAddresses();
        doReturn(true).when(verifier).isValid(any());
        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster,
                seedNodeRepository, verifier, daoStateService);
        CountingListener listener = new CountingListener();
        service.addListener(listener);
        NewBlockBroadcastMessage message = newBlockBroadcastMessage(0);
        service.onMessage(message, connection);
        service.onMessage(message, connection);
        assertEquals(1, listener.newBlockCount.get());

        service.reset();

        assertEquals(0, service.getNumReceivedBlocks());
        service.onMessage(message, connection);
        assertEquals(2, listener.newBlockCount.get());
    }

    @Test
    void doesNotRememberNewBlockBroadcastMessageWhenSignatureIsInvalid(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        doReturn(Set.of()).when(seedNodeRepository).getSeedNodeAddresses();
        NodeAddress peerAddress = new NodeAddress("peer.onion:8000");
        doReturn(Optional.of(peerAddress)).when(connection).getPeersNodeAddressOptional();

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier, daoStateService);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        RawBlock rawBlock = createRawBlock(100, "block-hash");
        SignedRawBlock signedRawBlock = new SignedRawBlock(rawBlock,
                new DaoBlockSignature(new NodeAddress("signer.onion:8000"), new byte[]{0x01}));
        NewBlockBroadcastMessage message = new NewBlockBroadcastMessage(rawBlock, signedRawBlock);
        doReturn(false, true).when(daoBlockSignatureVerifier).isValid(signedRawBlock);

        service.onMessage(message, connection);
        service.onMessage(message, connection);

        assertEquals(1, listener.newBlockCount.get());
        verify(broadcaster, times(1)).broadcast(eq(message), eq(peerAddress));
        verify(daoBlockSignatureVerifier, times(2)).isValid(signedRawBlock);
    }

    // A request without a confirmed seed node connection starts with a retry. Normal operation sends more such
    // requests than there are seed nodes or retries of one request.
    @Test
    void startsEachRequestWithAllSeedNodesAndRetries() {
        BlockRequests blockRequests = new BlockRequests();

        for (int i = 0; i < 15; i++) {
            blockRequests.service.requestBlocks(100 + i);
            ManualTimer.latest().fire();
            blockRequests.answerLast();
        }

        assertEquals(15, blockRequests.requests.size());
        assertEquals(15, blockRequests.listener.requestedBlocksCount.get());
        assertEquals(0, blockRequests.listener.noSeedNodeAvailableCount.get());
    }

    @Test
    void asksEachSeedNodeOncePerRequest() {
        BlockRequests blockRequests = new BlockRequests();

        blockRequests.failAllAttempts(100);

        assertEquals(SEED_NODES.size(), blockRequests.receivers.size());
        assertEquals(SEED_NODES, Set.copyOf(blockRequests.receivers));
        assertEquals(1, blockRequests.listener.noSeedNodeAvailableCount.get());

        blockRequests.service.requestBlocks(101);
        ManualTimer.latest().fire();

        assertEquals(SEED_NODES.size() + 1, blockRequests.requests.size());
        assertEquals(101, blockRequests.requests.get(SEED_NODES.size()).getFromBlockHeight());
    }

    // A request which gave up leaves no handler and no retry timer, so a trigger with the same start height must
    // start a new request. Else the DAO state would stay behind until a trigger with another start height.
    @Test
    void startsNewRequestForTheSameHeightAfterRequestGaveUp() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.failAllAttempts(100);

        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();

        assertEquals(List.of(100, 100, 100), blockRequests.requestedHeights());
        assertEquals(1, blockRequests.listener.noSeedNodeAvailableCount.get());
    }

    @Test
    void doesNotRetryTheSeedNodeOfTheConfirmedConnection() {
        BlockRequests blockRequests = new BlockRequests();
        NodeAddress seedNode = new NodeAddress("seed1.onion:8000");
        Connection seedNodeConnection = mock(Connection.class);
        doReturn(Set.of(seedNodeConnection)).when(blockRequests.networkNode).getConfirmedConnections();
        doReturn(true).when(blockRequests.peerManager).isSeedNode(seedNodeConnection);
        doReturn(Optional.of(seedNode)).when(seedNodeConnection).getPeersNodeAddressOptional();
        blockRequests.failSending = true;

        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();
        ManualTimer.latest().fire();

        assertEquals(seedNode, blockRequests.receivers.get(0));
        assertEquals(SEED_NODES, Set.copyOf(blockRequests.receivers));
        assertEquals(SEED_NODES.size(), blockRequests.receivers.size());
        assertEquals(1, blockRequests.listener.noSeedNodeAvailableCount.get());
    }

    @Test
    void doesNotAskBannedSeedNodeAgain() {
        BlockRequests blockRequests = new BlockRequests();
        Connection bannedSeedNodeConnection = mock(Connection.class);
        doReturn(Optional.of(new NodeAddress("seed1.onion:8000")))
                .when(bannedSeedNodeConnection).getPeersNodeAddressOptional();
        doReturn(true).when(blockRequests.peerManager).isPeerBanned(CloseConnectionReason.PEER_BANNED,
                bannedSeedNodeConnection);
        blockRequests.service.onDisconnect(CloseConnectionReason.PEER_BANNED, bannedSeedNodeConnection);

        blockRequests.failAllAttempts(100);

        assertEquals(List.of(new NodeAddress("seed2.onion:8000")), blockRequests.receivers);
    }

    @Test
    void keepsRunningRequestForTheSameHeight() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();

        blockRequests.service.requestBlocks(100);
        blockRequests.answer(0);
        ManualTimer.firePendingTimers();

        assertEquals(1, blockRequests.requests.size());
        assertEquals(1, blockRequests.listener.requestedBlocksCount.get());
    }

    @Test
    void keepsWaitingRetryForTheSameHeight() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.failSending = true;
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();
        ManualTimer waitingRetry = ManualTimer.latest();

        blockRequests.service.requestBlocks(100);

        assertSame(waitingRetry, ManualTimer.latest());
        assertFalse(waitingRetry.isStopped());
    }

    // The waiting request keeps its tried seed nodes, so after the connection it asks only the seed node it has not
    // asked yet.
    @Test
    void keepsRequestWaitingForAConnectionForTheSameHeight() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();
        blockRequests.connections.clear();
        blockRequests.sendResults.getFirst().setException(new IOException("Seed node is offline"));

        blockRequests.service.requestBlocks(100);
        blockRequests.connections.add(blockRequests.connection);
        blockRequests.service.onConnection(blockRequests.connection);
        blockRequests.failSending = true;
        for (int i = 0; i < SEED_NODES.size(); i++) {
            ManualTimer.latest().fire();
        }

        assertEquals(SEED_NODES, Set.copyOf(blockRequests.receivers));
        assertEquals(SEED_NODES.size(), blockRequests.receivers.size());
        assertEquals(1, blockRequests.listener.noSeedNodeAvailableCount.get());
    }

    @Test
    void newRequestStopsWaitingRetryOfEarlierRequest() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.failSending = true;
        blockRequests.service.requestBlocks(100);
        // The send fails, so a retry for block 100 waits when the next request starts.
        ManualTimer.latest().fire();
        blockRequests.failSending = false;

        blockRequests.service.requestBlocks(101);
        ManualTimer.firePendingTimers();

        assertEquals(List.of(100, 101), blockRequests.requestedHeights());
    }

    @Test
    void ignoresLateAnswerOfReplacedRequest() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();
        blockRequests.service.requestBlocks(101);
        ManualTimer.latest().fire();

        blockRequests.answer(0);
        assertEquals(0, blockRequests.listener.requestedBlocksCount.get());

        blockRequests.answer(1);
        assertEquals(1, blockRequests.listener.requestedBlocksCount.get());
    }

    @Test
    void ignoresLateFailureOfReplacedRequest() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();
        blockRequests.service.requestBlocks(101);
        ManualTimer.latest().fire();
        blockRequests.answerLast();

        blockRequests.sendResults.get(0).setException(new IOException("Seed node is offline"));
        ManualTimer.firePendingTimers();

        assertEquals(List.of(100, 101), blockRequests.requestedHeights());
        assertEquals(0, blockRequests.listener.faultCount.get());
    }

    // After a reorg the node reverts its DAO state to the last snapshot and requests the blocks after it.
    @Test
    void requestsBlocksBelowAnAnsweredRequest() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(200);
        ManualTimer.latest().fire();
        blockRequests.answerLast();

        blockRequests.service.requestBlocks(181);
        ManualTimer.latest().fire();
        blockRequests.answerLast();

        assertEquals(List.of(200, 181), blockRequests.requestedHeights());
        assertEquals(2, blockRequests.listener.requestedBlocksCount.get());
    }

    @Test
    void resumesPendingRequestOnFirstConnectionAfterOfflineStartup() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.connections.clear();
        blockRequests.service.requestBlocks(100);
        ManualTimer.firePendingTimers();
        assertEquals(List.of(), blockRequests.requestedHeights());

        blockRequests.connections.add(blockRequests.connection);
        blockRequests.service.onConnection(blockRequests.connection);
        ManualTimer.firePendingTimers();

        assertEquals(List.of(100), blockRequests.requestedHeights());
    }

    @Test
    void newConnectionDoesNotRestartARequestThatGaveUp() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.failAllAttempts(100);

        blockRequests.service.onConnection(blockRequests.connection);
        ManualTimer.firePendingTimers();

        assertEquals(SEED_NODES.size(), blockRequests.requests.size());
        assertEquals(1, blockRequests.listener.noSeedNodeAvailableCount.get());
    }

    @Test
    void requestsBlocksAgainWithNewRetriesAfterAllConnectionsLost() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.failAllAttempts(100);

        blockRequests.service.onAllConnectionsLost();
        blockRequests.service.onNewConnectionAfterAllConnectionsLost();
        ManualTimer.latest().fire();

        assertEquals(SEED_NODES.size() + 1, blockRequests.requests.size());
        assertEquals(100, blockRequests.requests.get(SEED_NODES.size()).getFromBlockHeight());
    }

    // The request connection was the last one. The peer manager reports that all connections were lost before the
    // service sees the disconnect, so the attempt in flight ends there, and the next connection starts the request again.
    @Test
    void requestsBlocksAgainWhenTheLastConnectionCloses() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();
        blockRequests.sendResults.getFirst().set(blockRequests.connection);
        blockRequests.connections.clear();
        blockRequests.service.onAllConnectionsLost();
        blockRequests.disconnect(blockRequests.connection, blockRequests.receivers.getFirst());
        ManualTimer.firePendingTimers();
        assertEquals(List.of(100), blockRequests.requestedHeights());

        blockRequests.connections.add(blockRequests.connection);
        blockRequests.service.onNewConnectionAfterAllConnectionsLost();
        blockRequests.service.onConnection(blockRequests.connection);
        ManualTimer.firePendingTimers();

        assertEquals(List.of(100, 100), blockRequests.requestedHeights());
    }

    @Test
    void asksAnotherSeedNodeWhenAskedSeedNodeDisconnects() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();
        NodeAddress askedSeedNode = blockRequests.receivers.get(0);
        blockRequests.sendResults.getFirst().set(blockRequests.connection);

        blockRequests.disconnect(blockRequests.connection, askedSeedNode);
        ManualTimer.latest().fire();

        assertEquals(List.of(100, 100), blockRequests.requestedHeights());
        assertNotEquals(askedSeedNode, blockRequests.receivers.get(1));
    }

    // The second seed node is the last one the request can ask, so dropping its attempt would make the request give up.
    @Test
    void keepsAttemptWhenAnotherConnectionToTheAskedSeedNodeCloses() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();
        blockRequests.sendResults.getFirst().setException(new IOException("First seed is offline"));
        ManualTimer.latest().fire();
        blockRequests.sendResults.getLast().set(blockRequests.connection);

        blockRequests.disconnect(mock(Connection.class), blockRequests.receivers.getLast());
        blockRequests.answerLast();
        ManualTimer.firePendingTimers();

        assertEquals(1, blockRequests.listener.requestedBlocksCount.get());
        assertEquals(0, blockRequests.listener.noSeedNodeAvailableCount.get());
    }

    @Test
    void retriesWhenTheRequestConnectionClosesBeforeTheSendCompletes() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();
        NodeAddress askedSeedNode = blockRequests.receivers.getFirst();
        blockRequests.disconnect(blockRequests.connection, askedSeedNode);
        blockRequests.sendResults.getFirst().set(blockRequests.connection);
        ManualTimer.latest().fire();

        assertEquals(List.of(100, 100), blockRequests.requestedHeights());
        assertNotEquals(askedSeedNode, blockRequests.receivers.getLast());
    }

    @Test
    void deliversAnAlreadyQueuedAnswerWhenTheSendCompletesOnAClosedConnection() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();
        doReturn(true).when(blockRequests.connection).isStopped();

        ArrayDeque<Runnable> events = new ArrayDeque<>();
        Executor previousExecutor = UserThread.getExecutor();
        UserThread.setExecutor(events::add);
        try {
            UserThread.execute(blockRequests::answerLast);
            blockRequests.sendResults.getFirst().set(blockRequests.connection);
            while (!events.isEmpty()) {
                events.remove().run();
            }
        } finally {
            UserThread.setExecutor(previousExecutor);
        }
        ManualTimer.firePendingTimers();

        assertEquals(1, blockRequests.listener.requestedBlocksCount.get());
        assertEquals(1, blockRequests.requests.size());
    }

    @Test
    void keepsRequestWhenAnotherPeerDisconnects() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();
        blockRequests.disconnect(mock(Connection.class), new NodeAddress("peer.onion:8000"));
        blockRequests.answerLast();

        assertEquals(1, blockRequests.requests.size());
        assertEquals(1, blockRequests.listener.requestedBlocksCount.get());
    }

    @Test
    void doesNotRequestAnsweredBlocksAgainAfterStandby() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();
        blockRequests.answerLast();

        blockRequests.service.onAwakeFromStandby();
        ManualTimer.firePendingTimers();

        assertEquals(1, blockRequests.requests.size());
    }

    @Test
    void requestsBlocksAgainWithNewRetriesAfterStandby() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.failAllAttempts(100);

        blockRequests.service.onAwakeFromStandby();
        ManualTimer.latest().fire();

        assertEquals(SEED_NODES.size() + 1, blockRequests.requests.size());
        assertEquals(100, blockRequests.requests.get(SEED_NODES.size()).getFromBlockHeight());
    }

    @Test
    void sendsNoRetryAfterShutDown() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.failSending = true;
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();

        blockRequests.service.shutDown();
        ManualTimer.firePendingTimers();

        assertEquals(1, blockRequests.requests.size());
    }

    @Test
    void ignoresAnswerAfterShutDown() {
        BlockRequests blockRequests = new BlockRequests();
        blockRequests.service.requestBlocks(100);
        ManualTimer.latest().fire();

        blockRequests.service.shutDown();
        blockRequests.answerLast();

        assertEquals(0, blockRequests.listener.requestedBlocksCount.get());
    }

    private static NewBlockBroadcastMessage newBlockBroadcastMessage(int height) {
        DaoBlockSignature signature = new DaoBlockSignature(new NodeAddress("signer.onion:8000"), new byte[]{1});
        RawBlock block = createRawBlock(height, Integer.toString(height));
        return new NewBlockBroadcastMessage(block, new SignedRawBlock(block, signature));
    }

    private static RawBlock createRawBlock(int height, String hash) {
        return createRawBlock(height, hash, null);
    }

    private static RawBlock createRawBlock(int height, String hash, @Nullable String txId) {
        protobuf.RawBlock.Builder rawBlockBuilder = protobuf.RawBlock.newBuilder();
        if (txId != null) {
            rawBlockBuilder.addRawTxs(protobuf.BaseTx.newBuilder()
                    .setTxVersion("1")
                    .setId(txId)
                    .setBlockHeight(height)
                    .setBlockHash(hash)
                    .setTime(1234)
                    .setRawTx(protobuf.RawTx.newBuilder())
                    .build());
        }
        protobuf.BaseBlock proto = protobuf.BaseBlock.newBuilder()
                .setHeight(height)
                .setTime(1234)
                .setHash(hash)
                .setPreviousBlockHash("previous-block-hash")
                .setRawBlock(rawBlockBuilder)
                .build();
        return RawBlock.fromProto(proto);
    }

    // A lite node without a confirmed seed node connection, so that each request starts with a retry. It records each
    // GetBlocksRequest that is sent to a seed node. The test answers a request, or lets the sends fail.
    private class BlockRequests {
        final NetworkNode networkNode = mock(NetworkNode.class);
        final PeerManager peerManager = mock(PeerManager.class);
        final Connection connection = mock(Connection.class);
        final Set<Connection> connections = new HashSet<>(Set.of(connection));
        final CountingListener listener = new CountingListener();
        final LiteNodeNetworkService service;
        final List<NodeAddress> receivers = new ArrayList<>();
        final List<GetBlocksRequest> requests = new ArrayList<>();
        final List<SettableFuture<Connection>> sendResults = new ArrayList<>();
        // The handler of each request registers as message listener before it sends the request.
        final List<MessageListener> handlers = new ArrayList<>();
        boolean failSending;

        BlockRequests() {
            SeedNodeRepository seedNodeRepository = mock(SeedNodeRepository.class);
            doReturn(SEED_NODES).when(seedNodeRepository).getSeedNodeAddresses();
            doReturn(true).when(peerManager).isSeedNode(any(NodeAddress.class));
            doReturn(connections).when(networkNode).getAllConnections();
            doAnswer(invocation -> handlers.add(invocation.getArgument(0)))
                    .when(networkNode).addMessageListener(any());
            doAnswer(invocation -> {
                receivers.add(invocation.getArgument(0));
                requests.add(invocation.getArgument(1));
                SettableFuture<Connection> sendResult = SettableFuture.create();
                sendResults.add(sendResult);
                if (failSending) {
                    sendResult.setException(new IOException("Seed node is offline"));
                }
                return sendResult;
            }).when(networkNode).sendMessage(any(NodeAddress.class), any(GetBlocksRequest.class));

            service = new LiteNodeNetworkService(networkNode, peerManager, mock(Broadcaster.class),
                    seedNodeRepository, mock(DaoBlockSignatureVerifier.class), daoStateService);
            service.addListener(listener);
        }

        // Each failed send schedules the next retry, until no seed node is left for the request.
        void failAllAttempts(int startBlockHeight) {
            failSending = true;
            service.requestBlocks(startBlockHeight);
            for (int i = 0; i <= SEED_NODES.size(); i++) {
                ManualTimer.latest().fire();
            }
            failSending = false;
        }

        void answerLast() {
            answer(requests.size() - 1);
        }

        void disconnect(Connection closedConnection, NodeAddress peer) {
            lenient().doReturn(Optional.of(peer)).when(closedConnection).getPeersNodeAddressOptional();
            lenient().doReturn(true).when(closedConnection).isStopped();
            service.onDisconnect(CloseConnectionReason.CLOSE_REQUESTED_BY_PEER, closedConnection);
        }

        void answer(int index) {
            // Lenient, as the handler of a replaced request ignores the answer before it reads the address.
            lenient().doReturn(Optional.of(receivers.get(index))).when(connection).getPeersNodeAddressOptional();
            GetBlocksResponse response = GetBlocksResponse.forSignedBlocks(List.of(), requests.get(index).getNonce());
            handlers.get(index).onMessage(response, connection);
        }

        List<Integer> requestedHeights() {
            return requests.stream().map(GetBlocksRequest::getFromBlockHeight).toList();
        }
    }

    private static class CountingListener implements LiteNodeNetworkService.Listener {
        final AtomicInteger newBlockCount = new AtomicInteger();
        final AtomicInteger requestedBlocksCount = new AtomicInteger();
        final AtomicInteger noSeedNodeAvailableCount = new AtomicInteger();
        final AtomicInteger faultCount = new AtomicInteger();
        final AtomicReference<NodeAddress> lastSenderNodeAddress = new AtomicReference<>();
        final AtomicReference<NewBlockBroadcastMessage> lastNewBlockMessage = new AtomicReference<>();

        @Override
        public void onNoSeedNodeAvailable() {
            noSeedNodeAvailableCount.incrementAndGet();
        }

        @Override
        public void onRequestedBlocksReceived(GetBlocksResponse getBlocksResponse,
                                              NodeAddress senderNodeAddress,
                                              Runnable onParsingComplete) {
            requestedBlocksCount.incrementAndGet();
        }

        @Override
        public void onNewBlockReceived(NewBlockBroadcastMessage newBlockBroadcastMessage,
                                       @Nullable NodeAddress senderNodeAddress) {
            newBlockCount.incrementAndGet();
            lastSenderNodeAddress.set(senderNodeAddress);
            lastNewBlockMessage.set(newBlockBroadcastMessage);
        }

        @Override
        public void onFault(String errorMessage,
                            @Nullable Connection connection) {
            faultCount.incrementAndGet();
        }
    }
}
