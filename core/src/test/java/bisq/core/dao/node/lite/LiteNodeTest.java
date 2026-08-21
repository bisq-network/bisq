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

package bisq.core.dao.node.lite;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.node.explorer.ExportJsonFilesService;
import bisq.core.dao.node.full.RawBlock;
import bisq.core.dao.node.lite.network.LiteNodeNetworkService;
import bisq.core.dao.node.messages.DaoBlockSignature;
import bisq.core.dao.node.messages.GetBlocksResponse;
import bisq.core.dao.node.messages.NewBlockBroadcastMessage;
import bisq.core.dao.node.messages.SignedRawBlock;
import bisq.core.dao.node.parser.BlockParser;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.DaoStateSnapshotService;
import bisq.core.dao.state.model.blockchain.Block;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class LiteNodeTest {
    private static final NodeAddress SENDER_NODE_ADDRESS = new NodeAddress("peer.onion:8000");
    private static final NodeAddress SIGNER_NODE_ADDRESS = new NodeAddress("signer.onion:8000");

    @Mock
    private BlockParser blockParser;
    @Mock
    private DaoStateService daoStateService;
    @Mock
    private DaoStateSnapshotService daoStateSnapshotService;
    @Mock
    private P2PService p2PService;
    @Mock
    private LiteNodeNetworkService liteNodeNetworkService;
    @Mock
    private DaoBlockSignatureVerifier daoBlockSignatureVerifier;
    @Mock
    private BsqWalletService bsqWalletService;
    @Mock
    private WalletsSetup walletsSetup;
    @Mock
    private ExportJsonFilesService exportJsonFilesService;

    @Test
    void getsSignedBlocksForParsingWhenSkipSignatureVerificationIsEnabled() {
        RawBlock rawBlock = createRawBlock(100, "block-hash");
        GetBlocksResponse response = GetBlocksResponse.forSignedBlocks(List.of(createSignedRawBlock(rawBlock)), 42);

        assertEquals(List.of(rawBlock), LiteNode.getBlocksForParsing(response, true));
    }

    @Test
    void prefersUnsignedBlocksForParsingWhenSkipSignatureVerificationIsEnabled() {
        RawBlock unsignedBlock = createRawBlock(100, "unsigned-block-hash");
        RawBlock signedBlock = createRawBlock(100, "signed-block-hash");
        GetBlocksResponse response = (GetBlocksResponse) GetBlocksResponse.fromProto(protobuf.GetBlocksResponse.newBuilder()
                        .addRawBlocks(unsignedBlock.toProtoMessage())
                        .addSignedRawBlocks(createSignedRawBlock(signedBlock).toProtoMessage())
                        .setRequestNonce(42)
                        .build(),
                1);

        assertEquals(List.of(unsignedBlock), LiteNode.getBlocksForParsing(response, true));
    }

    @Test
    void parsesSignedGetBlocksResponseWhenSkipSignatureVerificationIsEnabled() throws Exception {
        RawBlock rawBlock = createRawBlock(100, "block-hash");
        LiteNode liteNode = createLiteNode(true);
        when(daoStateService.getBlockAtHeight(rawBlock.getHeight())).thenReturn(Optional.empty());
        when(daoBlockSignatureVerifier.isValid(any(SignedRawBlock.class))).thenReturn(true);
        when(blockParser.parseBlock(rawBlock)).thenReturn(new Block(rawBlock.getHeight(),
                rawBlock.getTime(),
                rawBlock.getHash(),
                rawBlock.getPreviousBlockHash()));
        AtomicBoolean parsingComplete = new AtomicBoolean();

        liteNode.processGetBlocksResponse(GetBlocksResponse.forSignedBlocks(List.of(createSignedRawBlock(rawBlock)), 42),
                SENDER_NODE_ADDRESS,
                () -> parsingComplete.set(true));

        verify(blockParser).parseBlock(rawBlock);
        assertTrue(parsingComplete.get());
    }

    @Test
    void acceptsUnsignedNewBlockBroadcastMessageWhenSkipSignatureVerificationIsEnabled() throws Exception {
        RawBlock rawBlock = createRawBlock(100, "block-hash");
        LiteNode liteNode = createLiteNode(true);
        when(daoStateService.getBlockAtHeight(rawBlock.getHeight())).thenReturn(Optional.empty());
        when(blockParser.parseBlock(rawBlock)).thenReturn(new Block(rawBlock.getHeight(),
                rawBlock.getTime(),
                rawBlock.getHash(),
                rawBlock.getPreviousBlockHash()));

        liteNode.processNewBlockBroadcastMessage(new NewBlockBroadcastMessage(rawBlock), SENDER_NODE_ADDRESS);

        verify(blockParser).parseBlock(rawBlock);
    }

    @Test
    void ignoresUnsignedNewBlockBroadcastMessageWhenSkipSignatureVerificationIsDisabled() throws Exception {
        RawBlock rawBlock = createRawBlock(100, "block-hash");
        LiteNode liteNode = createLiteNode(false);

        liteNode.processNewBlockBroadcastMessage(new NewBlockBroadcastMessage(rawBlock), SENDER_NODE_ADDRESS);

        verify(blockParser, never()).parseBlock(any());
    }

    private LiteNode createLiteNode(boolean skipSignatureVerification) {
        when(daoStateService.getGenesisBlockHeight()).thenReturn(0);
        when(daoBlockSignatureVerifier.isSkipSignatureVerification()).thenReturn(skipSignatureVerification);
        return new LiteNode(blockParser,
                daoStateService,
                daoStateSnapshotService,
                p2PService,
                liteNodeNetworkService,
                daoBlockSignatureVerifier,
                bsqWalletService,
                walletsSetup,
                exportJsonFilesService);
    }

    private static SignedRawBlock createSignedRawBlock(RawBlock rawBlock) {
        return new SignedRawBlock(rawBlock,
                new DaoBlockSignature(SIGNER_NODE_ADDRESS, new byte[]{0x01}));
    }

    private static RawBlock createRawBlock(int height, String hash) {
        protobuf.BaseBlock proto = protobuf.BaseBlock.newBuilder()
                .setHeight(height)
                .setTime(1234)
                .setHash(hash)
                .setPreviousBlockHash("previous-block-hash")
                .setRawBlock(protobuf.RawBlock.newBuilder())
                .build();
        return RawBlock.fromProto(proto);
    }
}
