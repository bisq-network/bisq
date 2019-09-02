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

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.explorer.ExportJsonFilesService;
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

import bisq.common.Timer;
import bisq.common.UserThread;

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
    private static final int CHECK_FOR_BLOCK_RECEIVED_DELAY_SEC = 10;

    private final LiteNodeNetworkService liteNodeNetworkService;
    private final BsqWalletService bsqWalletService;
    private Timer checkForBlockReceivedTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public LiteNode(BlockParser blockParser,
                    DaoStateService daoStateService,
                    DaoStateSnapshotService daoStateSnapshotService,
                    P2PService p2PService,
                    LiteNodeNetworkService liteNodeNetworkService,
                    BsqWalletService bsqWalletService,
                    ExportJsonFilesService exportJsonFilesService) {
        super(blockParser, daoStateService, daoStateSnapshotService, p2PService, exportJsonFilesService);

        this.liteNodeNetworkService = liteNodeNetworkService;
        this.bsqWalletService = bsqWalletService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start() {
        super.onInitialized();

        liteNodeNetworkService.start();

        bsqWalletService.addNewBestBlockListener(block -> {
            // Check if we are done with parsing
            if (!daoStateService.isParseBlockChainComplete())
                return;

            if (checkForBlockReceivedTimer != null) {
                // In case we received a new block before out timer gets called we stop the old timer
                checkForBlockReceivedTimer.stop();
            }

            int height = block.getHeight();
            log.info("New block at height {} from bsqWalletService", height);

            // We expect to receive the new BSQ block from the network shortly after BitcoinJ has been aware of it.
            // If we don't receive it we request it manually from seed nodes
            checkForBlockReceivedTimer = UserThread.runAfter(() -> {
                int chainHeight = daoStateService.getChainHeight();
                if (chainHeight < height) {
                    log.warn("We did not receive a block from the network {} seconds after we saw the new block in BicoinJ. " +
                                    "We request from our seed nodes missing blocks from block height {}.",
                            CHECK_FOR_BLOCK_RECEIVED_DELAY_SEC, chainHeight + 1);
                    liteNodeNetworkService.requestBlocks(chainHeight + 1);
                }
            }, CHECK_FOR_BLOCK_RECEIVED_DELAY_SEC);
        });
    }

    @Override
    public void shutDown() {
        super.shutDown();
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
            public void onRequestedBlocksReceived(GetBlocksResponse getBlocksResponse, Runnable onParsingComplete) {
                LiteNode.this.onRequestedBlocksReceived(new ArrayList<>(getBlocksResponse.getBlocks()),
                        onParsingComplete);
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
    private void onRequestedBlocksReceived(List<RawBlock> blockList, Runnable onParsingComplete) {
        if (!blockList.isEmpty()) {
            chainTipHeight = blockList.get(blockList.size() - 1).getHeight();
            log.info("We received blocks from height {} to {}", blockList.get(0).getHeight(), chainTipHeight);
        }

        // We delay the parsing to next render frame to avoid that the UI get blocked in case we parse a lot of blocks.
        // Parsing itself is very fast (3 sec. for 7000 blocks) but creating the hash chain slows down batch processing a lot
        // (30 sec for 7000 blocks).
        // The updates at block height change are not much optimized yet, so that can be for sure improved
        // 144 blocks a day would result in about 4000 in a month, so if a user downloads the app after 1 months latest
        // release it will be a bit of a performance hit. It is a one time event as the snapshots gets created and be
        // used at next startup. New users will get the shipped snapshot. Users who have not used Bisq for longer might
        // experience longer durations for batch processing.
        long ts = System.currentTimeMillis();

        if (blockList.isEmpty()) {
            onParseBlockChainComplete();
            return;
        }

        runDelayedBatchProcessing(new ArrayList<>(blockList),
                () -> {
                    log.debug("Parsing {} blocks took {} seconds.", blockList.size(), (System.currentTimeMillis() - ts) / 1000d);
                    if (daoStateService.getChainHeight() < bsqWalletService.getBestChainHeight()) {
                        liteNodeNetworkService.requestBlocks(getStartBlockHeight());
                    } else {
                        onParsingComplete.run();
                        onParseBlockChainComplete();
                    }
                });
    }

    private void runDelayedBatchProcessing(List<RawBlock> blocks, Runnable resultHandler) {
        UserThread.execute(() -> {
            if (blocks.isEmpty()) {
                resultHandler.run();
                return;
            }

            RawBlock block = blocks.remove(0);
            try {
                doParseBlock(block);
                runDelayedBatchProcessing(blocks, resultHandler);
            } catch (RequiredReorgFromSnapshotException e) {
                resultHandler.run();
            }
        });
    }

    // We received a new block
    private void onNewBlockReceived(RawBlock block) {
        int blockHeight = block.getHeight();
        log.debug("onNewBlockReceived: block at height {}, hash={}", blockHeight, block.getHash());

        // We only update chainTipHeight if we get a newer block
        if (blockHeight > chainTipHeight)
            chainTipHeight = blockHeight;

        try {
            doParseBlock(block);
        } catch (RequiredReorgFromSnapshotException ignore) {
        }

        maybeExportToJson();
    }
}
