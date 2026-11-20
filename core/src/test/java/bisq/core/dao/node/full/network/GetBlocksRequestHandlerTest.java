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

package bisq.core.dao.node.full.network;

import bisq.core.dao.node.ResponseSizeLimit;
import bisq.core.dao.node.full.DaoBlockSigningService;
import bisq.core.dao.node.full.RawBlock;
import bisq.core.dao.node.messages.DaoBlockSignature;
import bisq.core.dao.node.messages.GetBlocksRequest;
import bisq.core.dao.node.messages.GetBlocksResponse;
import bisq.core.dao.node.messages.SignedRawBlock;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.proto.network.NetworkEnvelope;

import com.google.common.util.concurrent.SettableFuture;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetBlocksRequestHandlerTest {
    private static final NodeAddress SIGNER_NODE_ADDRESS = new NodeAddress("signer.onion:8000");

    @Test
    void answersRequestWithoutSignedDaoBlockCapabilityWithSignedBlocks(@Mock NetworkNode networkNode,
                                                                       @Mock DaoStateService daoStateService,
                                                                       @Mock DaoBlockSigningService daoBlockSigningService,
                                                                       @Mock GetBlocksRequestHandler.Listener listener,
                                                                       @Mock Connection connection) {
        Block block = new Block(100, 1234, "block-hash", "previous-block-hash");
        SignedRawBlock signedRawBlock = new SignedRawBlock(RawBlock.fromBlock(block),
                new DaoBlockSignature(SIGNER_NODE_ADDRESS, new byte[]{0x01}));
        when(daoStateService.getBlocksFromBlockHeightStream(100, 3000)).thenReturn(Stream.of(block));
        when(networkNode.getNodeAddress()).thenReturn(SIGNER_NODE_ADDRESS);
        when(daoBlockSigningService.sign(any(RawBlock.class), eq(SIGNER_NODE_ADDRESS))).thenReturn(signedRawBlock);
        when(networkNode.sendMessage(eq(connection), any(NetworkEnvelope.class))).thenReturn(SettableFuture.create());
        // A lite node before release 1.10.4 does not send the SIGNED_DAO_BLOCK capability.
        GetBlocksRequest request = (GetBlocksRequest) GetBlocksRequest.fromProto(protobuf.GetBlocksRequest.newBuilder()
                        .setFromBlockHeight(100)
                        .setNonce(42)
                        .build(),
                1);

        GetBlocksRequestHandler handler = new GetBlocksRequestHandler(networkNode,
                daoStateService,
                daoBlockSigningService,
                listener);
        handler.onGetBlocksRequest(request, connection);
        handler.stop();

        ArgumentCaptor<NetworkEnvelope> sent = ArgumentCaptor.forClass(NetworkEnvelope.class);
        verify(networkNode).sendMessage(eq(connection), sent.capture());
        GetBlocksResponse response = (GetBlocksResponse) sent.getValue();
        assertTrue(response.getBlocks().isEmpty());
        assertEquals(List.of(signedRawBlock), response.getSignedRawBlocks());
        assertEquals(42, response.getRequestNonce());
    }

    @Test
    void stopsSigningAfterTheFirstBlockThatDoesNotFit(@Mock NetworkNode networkNode,
                                                     @Mock DaoStateService daoStateService,
                                                     @Mock DaoBlockSigningService signingService,
                                                     @Mock GetBlocksRequestHandler.Listener listener,
                                                     @Mock Connection connection,
                                                     @Mock SignedRawBlock signedBlock,
                                                     @Mock protobuf.SignedRawBlock serialized) {
        Block block = new Block(100, 1234, "block-hash", "previous-block-hash");
        when(daoStateService.getBlocksFromBlockHeightStream(100, 3000)).thenReturn(Stream.of(block, block, block));
        when(networkNode.getNodeAddress()).thenReturn(SIGNER_NODE_ADDRESS);
        when(signingService.sign(any(RawBlock.class), eq(SIGNER_NODE_ADDRESS))).thenReturn(signedBlock);
        when(signedBlock.toProtoMessage()).thenReturn(serialized);
        when(serialized.getSerializedSize()).thenReturn(ResponseSizeLimit.MAX_BLOCKS_SIZE);
        when(networkNode.sendMessage(eq(connection), any(NetworkEnvelope.class))).thenReturn(SettableFuture.create());

        GetBlocksRequestHandler handler = new GetBlocksRequestHandler(networkNode, daoStateService, signingService, listener);
        handler.onGetBlocksRequest(new GetBlocksRequest(100, 42, SIGNER_NODE_ADDRESS), connection);
        handler.stop();

        verify(signingService, times(2)).sign(any(RawBlock.class), eq(SIGNER_NODE_ADDRESS));
        ArgumentCaptor<NetworkEnvelope> sent = ArgumentCaptor.forClass(NetworkEnvelope.class);
        verify(networkNode).sendMessage(eq(connection), sent.capture());
        assertEquals(List.of(signedBlock), ((GetBlocksResponse) sent.getValue()).getSignedRawBlocks());
    }
}
