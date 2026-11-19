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
import bisq.core.dao.node.messages.GetBlocksResponse;
import bisq.core.dao.node.messages.NewBlockBroadcastMessage;
import bisq.core.dao.node.messages.SignedRawBlock;
import bisq.core.dao.state.DaoStateService;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.seed.SeedNodeRepository;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.jetbrains.annotations.Nullable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class LiteNodeNetworkServiceTest {
    @Mock
    private DaoStateService daoStateService;

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

    private static class CountingListener implements LiteNodeNetworkService.Listener {
        final AtomicInteger newBlockCount = new AtomicInteger();
        final AtomicReference<NodeAddress> lastSenderNodeAddress = new AtomicReference<>();
        final AtomicReference<NewBlockBroadcastMessage> lastNewBlockMessage = new AtomicReference<>();

        @Override
        public void onNoSeedNodeAvailable() {
        }

        @Override
        public void onRequestedBlocksReceived(GetBlocksResponse getBlocksResponse,
                                              NodeAddress senderNodeAddress,
                                              Runnable onParsingComplete) {
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
        }
    }
}
