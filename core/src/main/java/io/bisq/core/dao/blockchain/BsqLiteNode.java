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

package io.bisq.core.dao.blockchain;

import com.google.inject.Inject;
import io.bisq.common.UserThread;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.p2p.RequestManager;
import io.bisq.core.dao.blockchain.p2p.messages.GetBsqBlocksResponse;
import io.bisq.core.dao.blockchain.p2p.messages.NewBsqBlockBroadcastMessage;
import io.bisq.core.dao.blockchain.parse.BsqChainState;
import io.bisq.core.dao.blockchain.parse.BsqLiteNodeExecutor;
import io.bisq.core.dao.blockchain.parse.BsqParser;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.seed.SeedNodesRepository;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// We are in UserThread context. We get callbacks from threaded classes which are already mapped to the UserThread.
@Slf4j
public class BsqLiteNode extends BsqNode {
    private final BsqLiteNodeExecutor bsqLiteNodeExecutor;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqLiteNode(P2PService p2PService,
                       BsqParser bsqParser,
                       BsqLiteNodeExecutor bsqLiteNodeExecutor,
                       BsqChainState bsqChainState,
                       FeeService feeService,
                       SeedNodesRepository seedNodesRepository) {
        super(p2PService,
                bsqParser,
                bsqChainState,
                feeService,
                seedNodesRepository);
        this.bsqLiteNodeExecutor = bsqLiteNodeExecutor;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        super.onAllServicesInitialized(errorMessageHandler);
    }

    @Override
    protected void onP2PNetworkReady() {
        super.onP2PNetworkReady();

        requestManager = new RequestManager(p2PService.getNetworkNode(),
                p2PService.getPeerManager(),
                p2PService.getBroadcaster(),
                seedNodesRepository.getSeedNodeAddresses(),
                bsqChainState,
                new RequestManager.Listener() {
                    @Override
                    public void onBlockReceived(GetBsqBlocksResponse getBsqBlocksResponse) {
                        List<BsqBlock> bsqBlockList = new ArrayList<>(getBsqBlocksResponse.getBsqBlocks());
                        log.info("received msg with {} items", bsqBlockList.size());
                        if (bsqBlockList.size() > 0)
                            log.info("block height of last item: {}", bsqBlockList.get(bsqBlockList.size() - 1).getHeight());
                        // Be safe and reset all mutable data in case the provider would not have done it
                        bsqBlockList.stream().forEach(BsqBlock::reset);
                        bsqLiteNodeExecutor.parseBsqBlocksForLiteNode(bsqBlockList,
                                genesisBlockHeight,
                                genesisTxId,
                                BsqLiteNode.this::onNewBsqBlock,
                                () -> onParseBlockchainComplete(genesisBlockHeight, genesisTxId), throwable -> {
                                    if (throwable instanceof BlockNotConnectingException) {
                                        startReOrgFromLastSnapshot();
                                    } else {
                                        log.error(throwable.toString());
                                        throwable.printStackTrace();
                                    }
                                });
                    }

                    @Override
                    public void onNewBsqBlockBroadcastMessage(NewBsqBlockBroadcastMessage newBsqBlockBroadcastMessage) {
                        BsqBlock bsqBlock = newBsqBlockBroadcastMessage.getBsqBlock();
                        // Be safe and reset all mutable data in case the provider would not have done it
                        bsqBlock.reset();
                        log.info("received broadcastNewBsqBlock bsqBlock {}", bsqBlock.getHeight());
                        if (!bsqChainState.containsBlock(bsqBlock)) {
                            bsqLiteNodeExecutor.parseBsqBlockForLiteNode(bsqBlock,
                                    genesisBlockHeight,
                                    genesisTxId,
                                    () -> onNewBsqBlock(bsqBlock), throwable -> {
                                        if (throwable instanceof BlockNotConnectingException) {
                                            startReOrgFromLastSnapshot();
                                        } else {
                                            log.error(throwable.toString());
                                            throwable.printStackTrace();
                                        }
                                    });
                        }
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

    @Override
    protected void parseBlocksWithChainHeadHeight(int startBlockHeight, int genesisBlockHeight, String genesisTxId) {
        parseBlocks(startBlockHeight, genesisBlockHeight, genesisTxId, 0);
    }

    @Override
    protected void parseBlocks(int startBlockHeight, int genesisBlockHeight, String genesisTxId, Integer chainHeadHeight) {
        requestManager.requestBlocks(startBlockHeight);
    }

    @Override
    protected void onParseBlockchainComplete(int genesisBlockHeight, String genesisTxId) {
        parseBlockchainComplete = true;
        bsqChainStateListeners.stream().forEach(BsqChainStateListener::onBsqChainStateChanged);
    }
}
