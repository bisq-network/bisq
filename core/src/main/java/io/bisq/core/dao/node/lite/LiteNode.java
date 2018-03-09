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

package io.bisq.core.dao.node.lite;

import com.google.inject.Inject;
import io.bisq.common.UserThread;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.core.dao.blockchain.BsqBlockChainListener;
import io.bisq.core.dao.blockchain.ReadModel;
import io.bisq.core.dao.blockchain.SnapshotManager;
import io.bisq.core.dao.blockchain.WriteModel;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.dao.node.BsqNode;
import io.bisq.core.dao.node.lite.network.LiteNodeNetworkManager;
import io.bisq.core.dao.node.messages.GetBsqBlocksResponse;
import io.bisq.core.dao.node.messages.NewBsqBlockBroadcastMessage;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.network.Connection;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Main class for lite nodes which receive the BSQ transactions from a full node (e.g. seed nodes).
 * <p>
 * Verification of BSQ transactions is done also by the lite node.
 */
@Slf4j
public class LiteNode extends BsqNode {
    private final LiteNodeExecutor bsqLiteNodeExecutor;
    private final LiteNodeNetworkManager liteNodeNetworkManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public LiteNode(WriteModel writeModel,
                    ReadModel readModel,
                    SnapshotManager snapshotManager,
                    P2PService p2PService,
                    LiteNodeExecutor bsqLiteNodeExecutor,
                    FeeService feeService,
                    LiteNodeNetworkManager liteNodeNetworkManager) {
        super(writeModel,
                readModel,
                snapshotManager,
                p2PService,
                feeService);
        this.bsqLiteNodeExecutor = bsqLiteNodeExecutor;
        this.liteNodeNetworkManager = liteNodeNetworkManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        super.onInitialized();
    }

    public void shutDown() {
        liteNodeNetworkManager.shutDown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onP2PNetworkReady() {
        super.onP2PNetworkReady();

        liteNodeNetworkManager.addListener(new LiteNodeNetworkManager.Listener() {
            @Override
            public void onRequestedBlocksReceived(GetBsqBlocksResponse getBsqBlocksResponse) {
                LiteNode.this.onRequestedBlocksReceived(new ArrayList<>(getBsqBlocksResponse.getBsqBlocks()));
            }

            @Override
            public void onNewBlockReceived(NewBsqBlockBroadcastMessage newBsqBlockBroadcastMessage) {
                LiteNode.this.onNewBlockReceived(newBsqBlockBroadcastMessage.getBsqBlock());
            }

            @Override
            public void onNoSeedNodeAvailable() {
            }

            @Override
            public void onFault(String errorMessage, @Nullable Connection connection) {
            }
        });

        // delay a bit to not stress too much at startup
        UserThread.runAfter(this::startParseBlocks, 2);
    }

    // First we request the blocks from a full node
    @Override
    protected void startParseBlocks() {
        liteNodeNetworkManager.requestBlocks(getStartBlockHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We received the missing blocks
    private void onRequestedBlocksReceived(List<BsqBlock> bsqBlockList) {
        log.info("onRequestedBlocksReceived: blocks with {} items", bsqBlockList.size());
        if (bsqBlockList.size() > 0)
            log.info("block height of last item: {}", bsqBlockList.get(bsqBlockList.size() - 1).getHeight());
        // We reset all mutable data in case the provider would not have done it.
        bsqBlockList.forEach(BsqBlock::reset);
        bsqLiteNodeExecutor.parseBlocks(bsqBlockList,
                block -> notifyListenersOnNewBlock(),
                this::onParseBlockchainComplete,
                getErrorHandler());
    }

    // We received a new block
    private void onNewBlockReceived(BsqBlock bsqBlock) {
        // We reset all mutable data in case the provider would not have done it.
        bsqBlock.reset();
        log.info("onNewBlockReceived: bsqBlock={}", bsqBlock.getHeight());
        if (!readModel.containsBlock(bsqBlock)) {
            bsqLiteNodeExecutor.parseBlock(bsqBlock,
                    this::notifyListenersOnNewBlock,
                    getErrorHandler());
        }
    }

    private void onParseBlockchainComplete() {
        parseBlockchainComplete = true;
        bsqBlockChainListeners.forEach(BsqBlockChainListener::onBsqBlockChainChanged);
    }

    @NotNull
    private Consumer<Throwable> getErrorHandler() {
        return throwable -> {
            if (throwable instanceof BlockNotConnectingException) {
                startReOrgFromLastSnapshot();
            } else {
                log.error(throwable.toString());
                throwable.printStackTrace();
            }
        };
    }
}
