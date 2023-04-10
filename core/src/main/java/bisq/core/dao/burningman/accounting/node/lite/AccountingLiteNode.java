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

package bisq.core.dao.burningman.accounting.node.lite;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.burningman.accounting.BurningManAccountingService;
import bisq.core.dao.burningman.accounting.blockchain.AccountingBlock;
import bisq.core.dao.burningman.accounting.exceptions.BlockHashNotConnectingException;
import bisq.core.dao.burningman.accounting.exceptions.BlockHeightNotConnectingException;
import bisq.core.dao.burningman.accounting.node.AccountingNode;
import bisq.core.dao.burningman.accounting.node.full.AccountingBlockParser;
import bisq.core.dao.burningman.accounting.node.lite.network.AccountingLiteNodeNetworkService;
import bisq.core.dao.burningman.accounting.node.messages.GetAccountingBlocksResponse;
import bisq.core.dao.burningman.accounting.node.messages.NewAccountingBlockBroadcastMessage;
import bisq.core.dao.state.DaoStateService;
import bisq.core.user.Preferences;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.ConnectionState;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.util.Hex;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import javafx.beans.value.ChangeListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class AccountingLiteNode extends AccountingNode implements AccountingLiteNodeNetworkService.Listener {
    private static final int CHECK_FOR_BLOCK_RECEIVED_DELAY_SEC = 10;

    private final WalletsSetup walletsSetup;
    private final BsqWalletService bsqWalletService;
    private final AccountingLiteNodeNetworkService accountingLiteNodeNetworkService;
    private final boolean useDevPrivilegeKeys;

    private final List<AccountingBlock> pendingAccountingBlocks = new ArrayList<>();
    private final ChangeListener<Number> blockDownloadListener;
    private Timer checkForBlockReceivedTimer;
    private int requestBlocksCounter;

    @Inject
    public AccountingLiteNode(P2PService p2PService,
                              DaoStateService daoStateService,
                              BurningManAccountingService burningManAccountingService,
                              AccountingBlockParser accountingBlockParser,
                              WalletsSetup walletsSetup,
                              BsqWalletService bsqWalletService,
                              AccountingLiteNodeNetworkService accountingLiteNodeNetworkService,
                              Preferences preferences,
                              @Named(Config.USE_DEV_PRIVILEGE_KEYS) boolean useDevPrivilegeKeys) {
        super(p2PService, daoStateService, burningManAccountingService,
                accountingBlockParser, preferences);

        this.walletsSetup = walletsSetup;
        this.bsqWalletService = bsqWalletService;
        this.accountingLiteNodeNetworkService = accountingLiteNodeNetworkService;
        this.useDevPrivilegeKeys = useDevPrivilegeKeys;

        blockDownloadListener = (observable, oldValue, newValue) -> {
            if ((double) newValue == 1) {
                setupWalletBestBlockListener();
            }
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void shutDown() {
        accountingLiteNodeNetworkService.shutDown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AccountingLiteNodeNetworkService.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onRequestedBlocksReceived(GetAccountingBlocksResponse getBlocksResponse) {
        List<AccountingBlock> blocks = getBlocksResponse.getBlocks();
        if (!blocks.isEmpty() && isValidPubKeyAndSignature(AccountingNode.getSha256Hash(blocks),
                getBlocksResponse.getPubKey(),
                getBlocksResponse.getSignature(),
                useDevPrivilegeKeys)) {
            processAccountingBlocks(blocks);
        }
    }

    @Override
    public void onNewBlockReceived(NewAccountingBlockBroadcastMessage message) {
        AccountingBlock accountingBlock = message.getBlock();
        if (isValidPubKeyAndSignature(AccountingNode.getSha256Hash(accountingBlock),
                message.getPubKey(),
                message.getSignature(),
                useDevPrivilegeKeys)) {
            processNewAccountingBlock(accountingBlock);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We start after initial DAO parsing is complete
    @Override
    protected void onInitialDaoBlockParsingComplete() {
        accountingLiteNodeNetworkService.addListeners();

        // We wait until the wallet is synced before using it for triggering requests
        if (walletsSetup.isDownloadComplete()) {
            setupWalletBestBlockListener();
        } else {
            walletsSetup.downloadPercentageProperty().addListener(blockDownloadListener);
        }

        super.onInitialized();
    }

    @Override
    protected void onP2PNetworkReady() {
        super.onP2PNetworkReady();

        accountingLiteNodeNetworkService.addListener(this);

        if (!initialBlockRequestsComplete) {
            startRequestBlocks();
        }
    }

    @Override
    protected void startRequestBlocks() {
        int heightOfLastBlock = burningManAccountingService.getBlockHeightOfLastBlock();
        if (walletsSetup.isDownloadComplete() && heightOfLastBlock == bsqWalletService.getBestChainHeight()) {
            log.info("No block request needed as we have already the most recent block. " +
                            "heightOfLastBlock={}, bsqWalletService.getBestChainHeight()={}",
                    heightOfLastBlock, bsqWalletService.getBestChainHeight());
            onInitialBlockRequestsComplete();
            return;
        }

        ConnectionState.incrementExpectedInitialDataResponses();
        accountingLiteNodeNetworkService.requestBlocks(heightOfLastBlock + 1);
    }

    @Override
    protected void applyReOrg() {
        pendingAccountingBlocks.clear();
        accountingLiteNodeNetworkService.reset();
        super.applyReOrg();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void processAccountingBlocks(List<AccountingBlock> blocks) {
        CompletableFuture.runAsync(() -> {
            long ts = System.currentTimeMillis();
            log.info("We received blocks from height {} to {}",
                    blocks.get(0).getHeight(),
                    blocks.get(blocks.size() - 1).getHeight());

            AtomicBoolean requiresReOrg = new AtomicBoolean(false);
            for (AccountingBlock block : blocks) {
                try {
                    burningManAccountingService.addBlock(block);
                } catch (BlockHeightNotConnectingException e) {
                    log.info("Height not connecting. This could happen if we received multiple responses and had already applied a previous one. {}", e.toString());
                } catch (BlockHashNotConnectingException e) {
                    log.warn("Interrupt loop because a reorg is required. {}", e.toString());
                    requiresReOrg.set(true);
                    break;
                }
            }

            UserThread.execute(() -> {
                if (requiresReOrg.get()) {
                    applyReOrg();
                    return;
                }

                int heightOfLastBlock = burningManAccountingService.getBlockHeightOfLastBlock();
                if (walletsSetup.isDownloadComplete() && heightOfLastBlock < bsqWalletService.getBestChainHeight()) {
                    accountingLiteNodeNetworkService.requestBlocks(heightOfLastBlock + 1);
                } else {
                    if (!initialBlockRequestsComplete) {
                        onInitialBlockRequestsComplete();
                    }
                }

                // 2833 blocks takes about 24 sec
                log.info("processAccountingBlocksAsync for {} blocks took {} ms", blocks.size(), System.currentTimeMillis() - ts);
            });
        });
    }

    private void processNewAccountingBlock(AccountingBlock accountingBlock) {
        int blockHeight = accountingBlock.getHeight();
        log.info("onNewBlockReceived: accountingBlock at height {}", blockHeight);

        pendingAccountingBlocks.remove(accountingBlock);
        try {
            burningManAccountingService.addBlock(accountingBlock);
            burningManAccountingService.onNewBlockReceived(accountingBlock);

            // After parsing we check if we have pending blocks we might have received earlier but which have been
            // not connecting from the latest height we had. The list is sorted by height
            if (!pendingAccountingBlocks.isEmpty()) {
                // We take only first element after sorting (so it is the accountingBlock with the next height) to avoid that
                // we would repeat calls in recursions in case we would iterate the list.
                pendingAccountingBlocks.sort(Comparator.comparing(AccountingBlock::getHeight));
                AccountingBlock nextPending = pendingAccountingBlocks.get(0);
                if (nextPending.getHeight() == burningManAccountingService.getBlockHeightOfLastBlock() + 1) {
                    processNewAccountingBlock(nextPending);
                }
            }
        } catch (BlockHeightNotConnectingException e) {
            // If height of rawDtoBlock is not at expected heightForNextBlock but further in the future we add it to pendingRawDtoBlocks
            int heightForNextBlock = burningManAccountingService.getBlockHeightOfLastBlock() + 1;
            if (accountingBlock.getHeight() > heightForNextBlock && !pendingAccountingBlocks.contains(accountingBlock)) {
                pendingAccountingBlocks.add(accountingBlock);
                log.info("We received a accountingBlock with a future accountingBlock height. We store it as pending and try to apply it at the next accountingBlock. " +
                        "heightForNextBlock={}, accountingBlock: height/truncatedHash={}/{}", heightForNextBlock, accountingBlock.getHeight(), accountingBlock.getTruncatedHash());

                requestBlocksCounter++;
                log.warn("We are trying to call requestBlocks with heightForNextBlock {} after a delay of {} min.",
                        heightForNextBlock, requestBlocksCounter * requestBlocksCounter);
                if (requestBlocksCounter <= 5) {
                    UserThread.runAfter(() -> {
                                pendingAccountingBlocks.clear();
                                accountingLiteNodeNetworkService.requestBlocks(heightForNextBlock);
                            },
                            requestBlocksCounter * requestBlocksCounter * 60L);
                } else {
                    log.warn("We tried {} times to call requestBlocks with heightForNextBlock {}.",
                            requestBlocksCounter, heightForNextBlock);
                }
            }
        } catch (BlockHashNotConnectingException e) {
            Optional<AccountingBlock> lastBlock = burningManAccountingService.getLastBlock();
            log.warn("Block not connecting:\n" +
                            "New block height/hash/previousBlockHash={}/{}/{}, latest block height/hash={}/{}",
                    accountingBlock.getHeight(),
                    Hex.encode(accountingBlock.getTruncatedHash()),
                    Hex.encode(accountingBlock.getTruncatedPreviousBlockHash()),
                    lastBlock.isPresent() ? lastBlock.get().getHeight() : "lastBlock not present",
                    lastBlock.isPresent() ? Hex.encode(lastBlock.get().getTruncatedHash()) : "lastBlock not present");

            applyReOrg();
        }
    }

    private void setupWalletBestBlockListener() {
        walletsSetup.downloadPercentageProperty().removeListener(blockDownloadListener);

        bsqWalletService.addNewBestBlockListener(blockFromWallet -> {
            // If we are not completed with initial block requests we return
            if (!initialBlockRequestsComplete)
                return;

            if (checkForBlockReceivedTimer != null) {
                // In case we received a new block before out timer gets called we stop the old timer
                checkForBlockReceivedTimer.stop();
            }

            int walletBlockHeight = blockFromWallet.getHeight();
            log.info("New block at height {} from bsqWalletService", walletBlockHeight);

            // We expect to receive the new BSQ block from the network shortly after BitcoinJ has been aware of it.
            // If we don't receive it we request it manually from seed nodes
            checkForBlockReceivedTimer = UserThread.runAfter(() -> {
                int heightOfLastBlock = burningManAccountingService.getBlockHeightOfLastBlock();
                if (heightOfLastBlock < walletBlockHeight) {
                    log.warn("We did not receive a block from the network {} seconds after we saw the new block in BitcoinJ. " +
                                    "We request from our seed nodes missing blocks from block height {}.",
                            CHECK_FOR_BLOCK_RECEIVED_DELAY_SEC, heightOfLastBlock + 1);
                    accountingLiteNodeNetworkService.requestBlocks(heightOfLastBlock + 1);
                }
            }, CHECK_FOR_BLOCK_RECEIVED_DELAY_SEC);
        });
    }
}
