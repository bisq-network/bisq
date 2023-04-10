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

package bisq.core.dao.burningman.accounting.node.full;

import bisq.core.dao.burningman.accounting.BurningManAccountingService;
import bisq.core.dao.burningman.accounting.blockchain.AccountingBlock;
import bisq.core.dao.burningman.accounting.exceptions.BlockHashNotConnectingException;
import bisq.core.dao.burningman.accounting.exceptions.BlockHeightNotConnectingException;
import bisq.core.dao.burningman.accounting.node.AccountingNode;
import bisq.core.dao.burningman.accounting.node.full.network.AccountingFullNodeNetworkService;
import bisq.core.dao.node.full.RpcException;
import bisq.core.dao.node.full.RpcService;
import bisq.core.dao.node.full.rpc.NotificationHandlerException;
import bisq.core.dao.node.full.rpc.dto.RawDtoBlock;
import bisq.core.dao.state.DaoStateService;
import bisq.core.user.Preferences;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.handlers.ResultHandler;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.net.ConnectException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AccountingFullNode extends AccountingNode {
    private final RpcService rpcService;
    private final AccountingFullNodeNetworkService accountingFullNodeNetworkService;

    private boolean addBlockHandlerAdded;
    private int batchedBlocks;
    private long batchStartTime;
    private final List<RawDtoBlock> pendingRawDtoBlocks = new ArrayList<>();
    private int requestBlocksUpToHeadHeightCounter;

    @Inject
    public AccountingFullNode(P2PService p2PService,
                              DaoStateService daoStateService,
                              BurningManAccountingService burningManAccountingService,
                              AccountingBlockParser accountingBlockParser,
                              AccountingFullNodeNetworkService accountingFullNodeNetworkService,
                              RpcService rpcService,
                              Preferences preferences) {
        super(p2PService, daoStateService, burningManAccountingService,
                accountingBlockParser, preferences);

        this.rpcService = rpcService;
        this.accountingFullNodeNetworkService = accountingFullNodeNetworkService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void shutDown() {
        accountingFullNodeNetworkService.shutDown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We start after initial DAO parsing is complete
    @Override
    protected void onInitialDaoBlockParsingComplete() {
        log.info("onInitialDaoBlockParsingComplete");
        rpcService.setup(() -> {
                    accountingFullNodeNetworkService.addListeners();
                    super.onInitialized();
                    startRequestBlocks();
                },
                this::handleError);
    }

    @Override
    protected void onP2PNetworkReady() {
        super.onP2PNetworkReady();

        if (initialBlockRequestsComplete) {
            addBlockHandler();
            int heightOfLastBlock = burningManAccountingService.getBlockHeightOfLastBlock();
            log.info("onP2PNetworkReady: We run requestBlocksIfNewBlockAvailable with latest block height {}.", heightOfLastBlock);
            requestBlocksIfNewBlockAvailable(heightOfLastBlock);
        }
    }

    @Override
    protected void startRequestBlocks() {
        int heightOfLastBlock = burningManAccountingService.getBlockHeightOfLastBlock();
        log.info("startRequestBlocks: heightOfLastBlock={}", heightOfLastBlock);
        rpcService.requestChainHeadHeight(headHeight -> {
                    // If our persisted block is equal to the chain height we have heightOfLastBlock 1 block higher,
                    // so we do not call parseBlocksOnHeadHeight
                    log.info("rpcService.requestChainHeadHeight: headHeight={}", headHeight);
                    requestBlocksUpToHeadHeight(heightOfLastBlock, headHeight);
                },
                this::handleError);
    }

    @Override
    protected void onInitialBlockRequestsComplete() {
        super.onInitialBlockRequestsComplete();

        if (p2pNetworkReady) {
            addBlockHandler();
        } else {
            log.info("onParseBlockChainComplete but P2P network is not ready yet.");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBlockHandler() {
        if (!addBlockHandlerAdded) {
            addBlockHandlerAdded = true;
            rpcService.addNewRawDtoBlockHandler(rawDtoBlock -> {
                        parseBlock(rawDtoBlock).ifPresent(accountingBlock -> {
                            maybePublishAccountingBlock(accountingBlock);
                            burningManAccountingService.onNewBlockReceived(accountingBlock);
                        });
                    },
                    this::handleError);
        }
    }

    private void maybePublishAccountingBlock(AccountingBlock accountingBlock) {
        if (p2pNetworkReady && initialBlockRequestsComplete) {
            accountingFullNodeNetworkService.publishAccountingBlock(accountingBlock);
        }
    }

    private void requestBlocksIfNewBlockAvailable(int heightOfLastBlock) {
        rpcService.requestChainHeadHeight(headHeight -> {
                    if (headHeight > heightOfLastBlock) {
                        log.info("During requests new blocks have arrived. We request again to get the missing blocks." +
                                "heightOfLastBlock={}, headHeight={}", heightOfLastBlock, headHeight);
                        requestBlocksUpToHeadHeight(heightOfLastBlock, headHeight);
                    } else {
                        log.info("requestChainHeadHeight did not result in a new block, so we complete.");
                        log.info("Requesting {} blocks took {} seconds", batchedBlocks, (System.currentTimeMillis() - batchStartTime) / 1000d);
                        if (!initialBlockRequestsComplete) {
                            onInitialBlockRequestsComplete();
                        }
                    }
                },
                this::handleError);
    }

    private void requestBlocksUpToHeadHeight(int heightOfLastBlock, int headHeight) {
        if (heightOfLastBlock == headHeight) {
            log.info("Out heightOfLastBlock is same as headHeight of Bitcoin Core.");
            if (!initialBlockRequestsComplete) {
                onInitialBlockRequestsComplete();
            }
        } else if (heightOfLastBlock < headHeight) {
            batchedBlocks = headHeight - heightOfLastBlock;
            batchStartTime = System.currentTimeMillis();
            log.info("Request {} blocks from {} to {}", batchedBlocks, heightOfLastBlock, headHeight);
            requestBlockRecursively(heightOfLastBlock,
                    headHeight,
                    () -> {
                        // We are done, but it might be that new blocks have arrived in the meantime,
                        // so we try again with heightOfLastBlock set to current headHeight
                        requestBlocksIfNewBlockAvailable(headHeight);
                    }, this::handleError);
        } else {
            requestBlocksUpToHeadHeightCounter++;
            log.warn("We are trying to start with a block which is above the chain height of Bitcoin Core. " +
                    "We need probably wait longer until Bitcoin Core has fully synced. " +
                    "We try again after a delay of {} min.", requestBlocksUpToHeadHeightCounter * requestBlocksUpToHeadHeightCounter);
            if (requestBlocksUpToHeadHeightCounter <= 5) {
                UserThread.runAfter(() -> rpcService.requestChainHeadHeight(height ->
                                requestBlocksUpToHeadHeight(heightOfLastBlock, height),
                        this::handleError), requestBlocksUpToHeadHeightCounter * requestBlocksUpToHeadHeightCounter * 60L);
            } else {
                log.warn("We tried {} times to start with startBlockHeight {} which is above the chain height {} of Bitcoin Core. " +
                                "It might be that Bitcoin Core has not fully synced. We give up now.",
                        requestBlocksUpToHeadHeightCounter, heightOfLastBlock, headHeight);
            }
        }
    }

    private void requestBlockRecursively(int heightOfLastBlock,
                                         int headHeight,
                                         ResultHandler completeHandler,
                                         Consumer<Throwable> errorHandler) {
        int requestHeight = heightOfLastBlock + 1;
        rpcService.requestRawDtoBlock(requestHeight,
                rawDtoBlock -> {
                    parseBlock(rawDtoBlock).ifPresent(this::maybePublishAccountingBlock);

                    // Increment heightOfLastBlock and recursively call requestBlockRecursively until we reach headHeight
                    int newHeightOfLastBlock = burningManAccountingService.getBlockHeightOfLastBlock();
                    if (requestHeight < headHeight) {
                        requestBlockRecursively(newHeightOfLastBlock, headHeight, completeHandler, errorHandler);
                    } else {
                        // We are done
                        completeHandler.handleResult();
                    }
                },
                errorHandler);
    }

    private Optional<AccountingBlock> parseBlock(RawDtoBlock rawDtoBlock) {
        // We check if we have a block with that height. If so we return. We do not use the chainHeight as with the earliest
        // height we have no block but chainHeight is initially set to the earliest height (bad design ;-( but a bit tricky
        // to change now as it used in many areas.)
        if (burningManAccountingService.getBlockAtHeight(rawDtoBlock.getHeight()).isPresent()) {
            log.info("We have already a block with the height of the new block. Height of new block={}", rawDtoBlock.getHeight());
            return Optional.empty();
        }

        pendingRawDtoBlocks.remove(rawDtoBlock);

        try {
            AccountingBlock accountingBlock = accountingBlockParser.parse(rawDtoBlock);
            burningManAccountingService.addBlock(accountingBlock);

            // After parsing we check if we have pending future blocks.
            // As we successfully added a new block a pending block might fit as next block.
            if (!pendingRawDtoBlocks.isEmpty()) {
                // We take only first element after sorting (so it is the accountingBlock with the next height) to avoid that
                // we would repeat calls in recursions in case we would iterate the list.
                pendingRawDtoBlocks.sort(Comparator.comparing(RawDtoBlock::getHeight));
                RawDtoBlock nextPending = pendingRawDtoBlocks.get(0);
                if (nextPending.getHeight() == burningManAccountingService.getBlockHeightOfLastBlock() + 1) {
                    parseBlock(nextPending);
                }
            }
            return Optional.of(accountingBlock);
        } catch (BlockHeightNotConnectingException e) {
            // If height of rawDtoBlock is not at expected heightForNextBlock but further in the future we add it to pendingRawDtoBlocks
            int heightForNextBlock = burningManAccountingService.getBlockHeightOfLastBlock() + 1;
            if (rawDtoBlock.getHeight() > heightForNextBlock && !pendingRawDtoBlocks.contains(rawDtoBlock)) {
                pendingRawDtoBlocks.add(rawDtoBlock);
                log.info("We received a block with a future block height. We store it as pending and try to apply it at the next block. " +
                        "heightForNextBlock={}, rawDtoBlock: height/hash={}/{}", heightForNextBlock, rawDtoBlock.getHeight(), rawDtoBlock.getHash());
            }
        } catch (BlockHashNotConnectingException throwable) {
            Optional<AccountingBlock> lastBlock = burningManAccountingService.getLastBlock();
            log.warn("Block not connecting:\n" +
                            "New block height={}; hash={}, previousBlockHash={}, latest block height={}; hash={}",
                    rawDtoBlock.getHeight(),
                    rawDtoBlock.getHash(),
                    rawDtoBlock.getPreviousBlockHash(),
                    lastBlock.isPresent() ? lastBlock.get().getHeight() : "lastBlock not present",
                    lastBlock.isPresent() ? lastBlock.get().getTruncatedHash() : "lastBlock not present");

            pendingRawDtoBlocks.clear();
            applyReOrg();
        }
        return Optional.empty();
    }

    private void handleError(Throwable throwable) {
        if (throwable instanceof BlockHashNotConnectingException || throwable instanceof BlockHeightNotConnectingException) {
            // We do not escalate that exception as it is handled with the snapshot manager to recover its state.
            log.warn(throwable.toString());
        } else {
            String errorMessage = "An error occurred: Error=" + throwable.toString();
            log.error(errorMessage);
            throwable.printStackTrace();

            if (throwable instanceof RpcException) {
                Throwable cause = throwable.getCause();
                if (cause != null) {
                    if (cause instanceof ConnectException) {
                        if (warnMessageHandler != null)
                            warnMessageHandler.accept("You have configured Bisq to run as BM full node but there is no " +
                                    "localhost Bitcoin Core node detected. You need to have Bitcoin Core started and synced before " +
                                    "starting Bisq. Please restart Bisq with proper BM full node setup or switch to lite node mode.");
                        return;
                    } else if (cause instanceof NotificationHandlerException) {
                        log.error("Error from within block notification daemon: {}", cause.getCause().toString());
                        applyReOrg();
                        return;
                    } else if (cause instanceof Error) {
                        throw (Error) cause;
                    }
                }
            }

            if (errorMessageHandler != null)
                errorMessageHandler.accept(errorMessage);
        }
    }
}
