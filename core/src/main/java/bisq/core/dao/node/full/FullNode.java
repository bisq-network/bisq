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

package bisq.core.dao.node.full;

import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.full.network.FullNodeNetworkService;
import bisq.core.dao.node.json.ExportJsonFilesService;
import bisq.core.dao.node.parser.BlockParser;
import bisq.core.dao.node.parser.exceptions.BlockNotConnectingException;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.SnapshotManager;
import bisq.core.dao.state.blockchain.Block;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.handlers.ResultHandler;

import javax.inject.Inject;

import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Main class for a full node which have Bitcoin Core with rpc running and does the blockchain lookup itself.
 * It also provides the BSQ transactions to lite nodes on request and broadcasts new BSQ blocks.
 * <p>
 * TODO request p2p network data again after parsing is complete to be sure that in case we missed data during parsing
 * we get it added.
 */
@Slf4j
public class FullNode extends BsqNode {

    private final RpcService rpcService;
    private final FullNodeNetworkService fullNodeNetworkService;
    private final ExportJsonFilesService exportJsonFilesService;
    private boolean addBlockHandlerAdded;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public FullNode(BlockParser blockParser,
                    BsqStateService bsqStateService,
                    SnapshotManager snapshotManager,
                    P2PService p2PService,
                    RpcService rpcService,
                    ExportJsonFilesService exportJsonFilesService,
                    FullNodeNetworkService fullNodeNetworkService) {
        super(blockParser, bsqStateService, snapshotManager, p2PService);
        this.rpcService = rpcService;

        this.exportJsonFilesService = exportJsonFilesService;
        this.fullNodeNetworkService = fullNodeNetworkService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start() {
        rpcService.setup(() -> {
                    super.onInitialized();
                    startParseBlocks();
                },
                this::handleError);
    }

    public void shutDown() {
        exportJsonFilesService.shutDown();
        fullNodeNetworkService.shutDown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void startParseBlocks() {
        requestChainHeadHeightAndParseBlocks(getStartBlockHeight());
    }

    @Override
    protected void onP2PNetworkReady() {
        super.onP2PNetworkReady();

        if (parseBlockchainComplete) {
            addBlockHandler();
            int blockHeightOfLastBlock = bsqStateService.getBlockHeightOfLastBlock();
            log.info("onP2PNetworkReady: We run parseBlocksIfNewBlockAvailable with latest block height {}.", blockHeightOfLastBlock);
            parseBlocksIfNewBlockAvailable(blockHeightOfLastBlock);
        }
    }

    @Override
    protected void onParseBlockChainComplete() {
        super.onParseBlockChainComplete();

        if (p2pNetworkReady)
            addBlockHandler();
        else
            log.info("onParseBlockChainComplete but P2P network is not ready yet.");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBlockHandler() {
        if (!addBlockHandlerAdded) {
            addBlockHandlerAdded = true;
            rpcService.addNewBtcBlockHandler(rawBlock -> {
                        if (!isBlockAlreadyAdded(rawBlock)) {
                            try {
                                Block block = blockParser.parseBlock(rawBlock);
                                onNewBlock(block);
                            } catch (BlockNotConnectingException throwable) {
                                handleError(throwable);
                            }
                        }
                    },
                    this::handleError);
        }
    }

    private void onNewBlock(Block block) {
        exportJsonFilesService.exportToJson();

        if (p2pNetworkReady && parseBlockchainComplete)
            fullNodeNetworkService.publishNewBlock(block);
    }

    private void parseBlocksIfNewBlockAvailable(int chainHeight) {
        rpcService.requestChainHeadHeight(newChainHeight -> {
                    if (newChainHeight > chainHeight) {
                        log.info("During parsing new blocks have arrived. We parse again with those missing blocks." +
                                "ChainHeadHeight={}, newChainHeadHeight={}", chainHeight, newChainHeight);
                        parseBlocksOnHeadHeight(chainHeight + 1, newChainHeight);
                    } else {
                        log.info("parseBlocksIfNewBlockAvailable did not result in a new block, so we complete.");
                        onParseBlockChainComplete();
                    }
                },
                this::handleError);
    }

    private void requestChainHeadHeightAndParseBlocks(int startBlockHeight) {
        log.info("requestChainHeadHeightAndParseBlocks with startBlockHeight={}", startBlockHeight);
        rpcService.requestChainHeadHeight(chainHeight -> parseBlocksOnHeadHeight(startBlockHeight, chainHeight),
                this::handleError);
    }

    private void parseBlocksOnHeadHeight(int startBlockHeight, int chainHeight) {
        if (startBlockHeight <= chainHeight) {
            log.info("parseBlocks with startBlockHeight={} and chainHeight={}", startBlockHeight, chainHeight);
            parseBlocks(startBlockHeight,
                    chainHeight,
                    this::onNewBlock,
                    () -> {
                        // We are done but it might be that new blocks have arrived in the meantime,
                        // so we try again with startBlockHeight set to current chainHeight
                        // We also set up the listener in the else main branch where we check
                        // if we are at chainTip, so do not include here another check as it would
                        // not trigger the listener registration.
                        parseBlocksIfNewBlockAvailable(chainHeight);
                    }, throwable -> {
                        if (throwable instanceof BlockNotConnectingException) {
                            startReOrgFromLastSnapshot();
                        } else {
                            handleError(throwable);
                        }
                    });
        } else {
            log.warn("We are trying to start with a block which is above the chain height of bitcoin core. " +
                    "We need probably wait longer until bitcoin core has fully synced. " +
                    "We try again after a delay of 1 min.");
            UserThread.runAfter(() -> requestChainHeadHeightAndParseBlocks(startBlockHeight), 60);
        }
    }

    private void parseBlocks(int startBlockHeight,
                             int chainHeight,
                             Consumer<Block> newBlockHandler,
                             ResultHandler resultHandler,
                             Consumer<Throwable> errorHandler) {
        parseBlock(startBlockHeight, chainHeight, newBlockHandler, resultHandler, errorHandler);
    }

    // Recursively request and parse all blocks
    private void parseBlock(int blockHeight, int chainHeight,
                            Consumer<Block> newBlockHandler, ResultHandler resultHandler,
                            Consumer<Throwable> errorHandler) {
        rpcService.requestBtcBlock(blockHeight,
                rawBlock -> {
                    if (!isBlockAlreadyAdded(rawBlock)) {
                        try {
                            Block block = blockParser.parseBlock(rawBlock);
                            newBlockHandler.accept(block);

                            // Increment blockHeight and recursively call parseBlockAsync until we reach chainHeight
                            if (blockHeight < chainHeight) {
                                final int newBlockHeight = blockHeight + 1;
                                parseBlock(newBlockHeight, chainHeight, newBlockHandler, resultHandler, errorHandler);
                            } else {
                                // We are done
                                resultHandler.handleResult();
                            }
                        } catch (BlockNotConnectingException e) {
                            errorHandler.accept(e);
                        }
                    } else {
                        log.info("Block was already added height=", rawBlock.getHeight());
                    }
                },
                errorHandler);
    }

    private void handleError(Throwable throwable) {
        String errorMessage = "An error occurred: Error=" + throwable.toString();
        log.error(errorMessage);

        if (throwable instanceof BlockNotConnectingException)
            startReOrgFromLastSnapshot();

        if (errorMessageHandler != null)
            errorMessageHandler.handleErrorMessage(errorMessage);
    }
}
