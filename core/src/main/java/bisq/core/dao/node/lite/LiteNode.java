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

import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.full.RawBlock;
import bisq.core.dao.node.lite.network.LiteNodeNetworkService;
import bisq.core.dao.node.messages.GetBlocksResponse;
import bisq.core.dao.node.messages.NewBlockBroadcastMessage;
import bisq.core.dao.node.parser.BlockParser;
import bisq.core.dao.node.parser.exceptions.RequiredReorgFromSnapshotException;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.DaoStateSnapshotService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.Connection;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.Nullable;

/**
 * Main class for lite nodes which receive the BSQ transactions from a full node (e.g. seed nodes).
 * Verification of BSQ transactions is done also by the lite node.
 */
@Slf4j
public class LiteNode extends BsqNode {
    private final LiteNodeNetworkService liteNodeNetworkService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public LiteNode(BlockParser blockParser,
                    DaoStateService daoStateService,
                    DaoStateSnapshotService daoStateSnapshotService,
                    P2PService p2PService,
                    LiteNodeNetworkService liteNodeNetworkService) {
        super(blockParser, daoStateService, daoStateSnapshotService, p2PService);

        this.liteNodeNetworkService = liteNodeNetworkService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start() {
        super.onInitialized();

        liteNodeNetworkService.start();
    }

    @Override
    public void shutDown() {
        liteNodeNetworkService.shutDown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onP2PNetworkReady() {
        super.onP2PNetworkReady();

        liteNodeNetworkService.addListener(new LiteNodeNetworkService.Listener() {
            @Override
            public void onRequestedBlocksReceived(GetBlocksResponse getBlocksResponse) {
                LiteNode.this.onRequestedBlocksReceived(new ArrayList<>(getBlocksResponse.getBlocks()));
            }

            @Override
            public void onNewBlockReceived(NewBlockBroadcastMessage newBlockBroadcastMessage) {
                LiteNode.this.onNewBlockReceived(newBlockBroadcastMessage.getBlock());
            }

            @Override
            public void onNoSeedNodeAvailable() {
            }

            @Override
            public void onFault(String errorMessage, @Nullable Connection connection) {
            }
        });

        if (!parseBlockchainComplete)
            startParseBlocks();
    }

    // First we request the blocks from a full node
    @Override
    protected void startParseBlocks() {
        log.info("startParseBlocks");
        liteNodeNetworkService.requestBlocks(getStartBlockHeight());
    }

    @Override
    protected void startReOrgFromLastSnapshot() {
        super.startReOrgFromLastSnapshot();

        int startBlockHeight = getStartBlockHeight();
        liteNodeNetworkService.reset();
        liteNodeNetworkService.requestBlocks(startBlockHeight);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We received the missing blocks
    private void onRequestedBlocksReceived(List<RawBlock> blockList) {
        if (!blockList.isEmpty()) {
            chainTipHeight = blockList.get(blockList.size() - 1).getHeight();
            log.info("We received blocks from height {} to {}", blockList.get(0).getHeight(), chainTipHeight);
        }

        // 4000 blocks take about 3 seconds if DAO UI is not displayed or 7 sec. if it is displayed.
        // The updates at block height change are not much optimized yet, so that can be for sure improved
        // 144 blocks a day would result in about 4000 in a month, so if a user downloads the app after 1 months latest
        // release it will be a bit of a performance hit. It is a one time event as the snapshots gets created and be
        // used at next startup.
        long ts = System.currentTimeMillis();
        for (RawBlock block : blockList) {
            try {
                doParseBlock(block);
            } catch (RequiredReorgFromSnapshotException e1) {
                // In case we got a reorg we break the iteration
                break;
            }
        }
        log.info("Parsing {} blocks took {} seconds.", blockList.size(), (System.currentTimeMillis() - ts) / 1000d);

        onParseBlockChainComplete();
    }

    // We received a new block
    private void onNewBlockReceived(RawBlock block) {
        int blockHeight = block.getHeight();
        log.info("onNewBlockReceived: block at height {}, hash={}", blockHeight, block.getHash());

        // We only update chainTipHeight if we get a newer block
        if (blockHeight > chainTipHeight)
            chainTipHeight = blockHeight;

        try {
            doParseBlock(block);
        } catch (RequiredReorgFromSnapshotException ignore) {
        }
    }
}
