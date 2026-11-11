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
import bisq.core.testutil.ManualTimer;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.FrameRateTimer;
import bisq.common.UserThread;

import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class LiteNodeNetworkServiceTest {

    @BeforeEach
    void setUp() {
        ManualTimer.clear();
        UserThread.setTimerClass(ManualTimer.class);
    }

    @AfterEach
    void tearDown() {
        ManualTimer.clear();
        UserThread.setTimerClass(FrameRateTimer.class);
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
                daoBlockSignatureVerifier);
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
                daoBlockSignatureVerifier);
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
                daoBlockSignatureVerifier);
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
                daoBlockSignatureVerifier);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        service.onMessage(message, connection);

        assertEquals(0, listener.newBlockCount.get());
        verify(broadcaster, never()).broadcast(any(NewBlockBroadcastMessage.class), any());
        verify(daoBlockSignatureVerifier, times(1)).isValid(signedRawBlock);
    }

    @Test
    void relaysUnsignedNewBlockBroadcastMessageAndNotifiesListener(
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
                daoBlockSignatureVerifier);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        RawBlock rawBlock = createRawBlock(100, "block-hash");
        NewBlockBroadcastMessage message = new NewBlockBroadcastMessage(rawBlock);

        service.onMessage(message, connection);

        assertEquals(1, listener.newBlockCount.get());
        assertEquals(peerAddress, listener.lastSenderNodeAddress.get());
        assertEquals(message, listener.lastNewBlockMessage.get());
        verify(broadcaster, times(1)).broadcast(eq(message), eq(peerAddress));
    }

    @Test
    void deduplicatesRepeatedUnsignedNewBlockBroadcastMessage(
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
                daoBlockSignatureVerifier);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        RawBlock rawBlock = createRawBlock(100, "block-hash");
        NewBlockBroadcastMessage message = new NewBlockBroadcastMessage(rawBlock);

        service.onMessage(message, connection);
        service.onMessage(message, connection);

        assertEquals(1, listener.newBlockCount.get());
        verify(broadcaster, times(1)).broadcast(eq(message), eq(peerAddress));
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
                daoBlockSignatureVerifier);
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
                daoBlockSignatureVerifier);
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

    @Test
    void requestsBlocksFromSeedNodeAfterEarlierRequestsWereAnswered(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        Set<NodeAddress> seedNodes = Set.of(new NodeAddress("seed1.onion:8000"), new NodeAddress("seed2.onion:8000"));
        BlockRequests blockRequests = new BlockRequests(networkNode, peerManager, seedNodeRepository, connection,
                seedNodes, false);

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        // Without a confirmed seed node connection each request goes through the retry timer. There are more requests
        // than seed nodes and than retries (MAX_RETRY is 12).
        for (int i = 0; i < 15; i++) {
            service.requestBlocks(100 + i);
            ManualTimer.latest().fire();

            assertEquals(i + 1, blockRequests.receivers.size());
            blockRequests.answerLatest(connection);
            assertEquals(i + 1, listener.requestedBlocksCount.get());
        }
    }

    @Test
    void asksEachSeedNodeOncePerRequestWhenSendingFails(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        Set<NodeAddress> seedNodes = Set.of(new NodeAddress("seed1.onion:8000"), new NodeAddress("seed2.onion:8000"));
        BlockRequests blockRequests = new BlockRequests(networkNode, peerManager, seedNodeRepository, connection,
                seedNodes, true);

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        service.requestBlocks(100);
        // Each failed send schedules the next retry. The third retry finds no seed node left for this request.
        ManualTimer.latest().fire();
        ManualTimer.latest().fire();
        ManualTimer.latest().fire();

        assertEquals(2, blockRequests.receivers.size());
        assertEquals(seedNodes, Set.copyOf(blockRequests.receivers));
        assertEquals(1, listener.noSeedNodeAvailableCount.get());

        service.requestBlocks(101);
        ManualTimer.latest().fire();

        assertEquals(3, blockRequests.receivers.size());
    }

    @Test
    void ignoresLateFailureOfReplacedRequest(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        Set<NodeAddress> seedNodes = Set.of(new NodeAddress("seed1.onion:8000"), new NodeAddress("seed2.onion:8000"));
        BlockRequests blockRequests = new BlockRequests(networkNode, peerManager, seedNodeRepository, connection,
                seedNodes, false);

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        service.requestBlocks(100);
        ManualTimer.latest().fire();
        // The next block was not broadcast in time either, before the first request got an answer.
        service.requestBlocks(101);
        ManualTimer.latest().fire();
        blockRequests.answerLatest(connection);
        ManualTimer timerAfterAnswer = ManualTimer.latest();

        // The send of the replaced request fails late. That must not start a retry for its old block height.
        blockRequests.futures.get(0).setException(new RuntimeException("Seed node is offline"));

        assertEquals(2, blockRequests.requests.size());
        assertEquals(1, listener.requestedBlocksCount.get());
        assertEquals(0, listener.faultCount.get());
        assertSame(timerAfterAnswer, ManualTimer.latest());
    }

    @Test
    void stopsWaitingRetryOfEarlierRequest(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        Set<NodeAddress> seedNodes = Set.of(new NodeAddress("seed1.onion:8000"), new NodeAddress("seed2.onion:8000"));
        BlockRequests blockRequests = new BlockRequests(networkNode, peerManager, seedNodeRepository, connection,
                seedNodes, true);

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier);

        // The first send fails, so the first request waits for its retry when the next request starts.
        service.requestBlocks(100);
        ManualTimer.latest().fire();
        blockRequests.failSending = false;
        service.requestBlocks(101);
        ManualTimer.latest().fire();

        assertEquals(2, blockRequests.requests.size());
        assertEquals(101, blockRequests.requests.get(1).getFromBlockHeight());
    }

    @Test
    void ignoresLateAnswerOfReplacedRequest(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        Set<NodeAddress> seedNodes = Set.of(new NodeAddress("seed1.onion:8000"), new NodeAddress("seed2.onion:8000"));
        BlockRequests blockRequests = new BlockRequests(networkNode, peerManager, seedNodeRepository, connection,
                seedNodes, false);

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        service.requestBlocks(100);
        ManualTimer.latest().fire();
        service.requestBlocks(101);
        ManualTimer.latest().fire();

        blockRequests.answer(0, connection);
        assertEquals(0, listener.requestedBlocksCount.get());

        blockRequests.answer(1, connection);
        assertEquals(1, listener.requestedBlocksCount.get());
    }

    @Test
    void requestsLowerBlockHeightAfterHigherRequestWasAnswered(
            @Mock NetworkNode networkNode,
            @Mock PeerManager peerManager,
            @Mock Broadcaster broadcaster,
            @Mock SeedNodeRepository seedNodeRepository,
            @Mock DaoBlockSignatureVerifier daoBlockSignatureVerifier,
            @Mock Connection connection) {
        Set<NodeAddress> seedNodes = Set.of(new NodeAddress("seed1.onion:8000"), new NodeAddress("seed2.onion:8000"));
        BlockRequests blockRequests = new BlockRequests(networkNode, peerManager, seedNodeRepository, connection,
                seedNodes, false);

        LiteNodeNetworkService service = new LiteNodeNetworkService(networkNode, peerManager, broadcaster, seedNodeRepository,
                daoBlockSignatureVerifier);
        CountingListener listener = new CountingListener();
        service.addListener(listener);

        service.requestBlocks(200);
        ManualTimer.latest().fire();
        blockRequests.answerLatest(connection);

        // After a reorg the DAO state is reverted to a snapshot, and we request the blocks after it again.
        service.requestBlocks(181);
        ManualTimer.latest().fire();

        assertEquals(2, blockRequests.requests.size());
        assertEquals(181, blockRequests.requests.get(1).getFromBlockHeight());
        blockRequests.answerLatest(connection);
        assertEquals(2, listener.requestedBlocksCount.get());
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

    // Stands in for the network. It records each GetBlocksRequest that is sent to a seed node. The test answers a
    // request, or the sends fail as for an offline seed node while failSending is set. A test can also fail a pending
    // send later through futures.
    private static class BlockRequests {
        final List<NodeAddress> receivers = new ArrayList<>();
        final List<GetBlocksRequest> requests = new ArrayList<>();
        final List<SettableFuture<Connection>> futures = new ArrayList<>();
        final List<MessageListener> messageListeners = new ArrayList<>();
        boolean failSending;

        BlockRequests(NetworkNode networkNode,
                      PeerManager peerManager,
                      SeedNodeRepository seedNodeRepository,
                      Connection connection,
                      Set<NodeAddress> seedNodes,
                      boolean failSending) {
            this.failSending = failSending;
            doReturn(seedNodes).when(seedNodeRepository).getSeedNodeAddresses();
            doReturn(true).when(peerManager).isSeedNode(any(NodeAddress.class));
            doReturn(Set.of()).when(networkNode).getConfirmedConnections();
            doReturn(Set.of(connection)).when(networkNode).getAllConnections();
            doAnswer(invocation -> messageListeners.add(invocation.getArgument(0)))
                    .when(networkNode).addMessageListener(any());
            doAnswer(invocation -> {
                receivers.add(invocation.getArgument(0));
                requests.add(invocation.getArgument(1));
                SettableFuture<Connection> future = SettableFuture.create();
                futures.add(future);
                if (this.failSending) {
                    future.setException(new RuntimeException("Seed node is offline"));
                }
                return future;
            }).when(networkNode).sendMessage(any(NodeAddress.class), any(GetBlocksRequest.class));
        }

        void answerLatest(Connection connection) {
            answer(requests.size() - 1, connection);
        }

        void answer(int index, Connection connection) {
            // Lenient as the handler of a replaced request ignores the answer before it reads the address.
            lenient().doReturn(Optional.of(receivers.get(index))).when(connection).getPeersNodeAddressOptional();
            GetBlocksResponse response = GetBlocksResponse.forSignedBlocks(List.of(), requests.get(index).getNonce());
            messageListeners.get(index).onMessage(response, connection);
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
