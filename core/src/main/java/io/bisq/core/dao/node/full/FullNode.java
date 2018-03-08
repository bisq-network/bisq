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

package io.bisq.core.dao.node.full;

import com.google.inject.Inject;
import io.bisq.common.UserThread;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.core.dao.blockchain.BsqBlockChain;
import io.bisq.core.dao.blockchain.BsqBlockChainListener;
import io.bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import io.bisq.core.dao.blockchain.json.JsonBlockChainExporter;
import io.bisq.core.dao.blockchain.p2p.RequestManager;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.dao.node.BsqNode;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.network.p2p.P2PService;
import lombok.extern.slf4j.Slf4j;

/**
 * Main class for a full node which have Bitcoin Core with rpc running and does the blockchain lookup itself.
 * It also provides the BSQ transactions to lite nodes on request.
 */
@Slf4j
public class FullNode extends BsqNode {

    private final FullNodeExecutor bsqFullNodeExecutor;
    private final JsonBlockChainExporter jsonBlockChainExporter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public FullNode(P2PService p2PService,
                    FullNodeExecutor bsqFullNodeExecutor,
                    BsqBlockChain bsqBlockChain,
                    JsonBlockChainExporter jsonBlockChainExporter,
                    FeeService feeService,
                    RequestManager requestManager) {
        super(p2PService,
                bsqBlockChain,
                feeService,
                requestManager);
        this.bsqFullNodeExecutor = bsqFullNodeExecutor;
        this.jsonBlockChainExporter = jsonBlockChainExporter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        bsqFullNodeExecutor.setup(() -> {
                    super.onInitialized();
                    startParseBlocks();
                },
                throwable -> {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                    errorMessageHandler.handleErrorMessage("Initializing BsqFullNode failed: Error=" + throwable.toString());
                });
    }

    public void shutDown() {
        super.shutDown();
        jsonBlockChainExporter.shutDown();
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

        if (parseBlockchainComplete)
            addBlockHandler();
    }

    @Override
    protected void onNewBsqBlock(BsqBlock bsqBlock) {
        super.onNewBsqBlock(bsqBlock);
        jsonBlockChainExporter.maybeExport();
        if (parseBlockchainComplete && p2pNetworkReady)
            requestManager.publishNewBlock(bsqBlock);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBlockHandler() {
        bsqFullNodeExecutor.addBlockHandler(btcdBlock -> bsqFullNodeExecutor.parseBtcdBlock(btcdBlock,
                this::onNewBsqBlock,
                throwable -> {
                    if (throwable instanceof BlockNotConnectingException) {
                        startReOrgFromLastSnapshot();
                    } else {
                        log.error(throwable.toString());
                        throwable.printStackTrace();
                    }
                }));
    }

    private void requestChainHeadHeightAndParseBlocks(int startBlockHeight) {
        log.info("parseBlocks startBlockHeight={}", startBlockHeight);
        bsqFullNodeExecutor.requestChainHeadHeight(chainHeadHeight -> parseBlocksOnHeadHeight(startBlockHeight, chainHeadHeight),
                throwable -> {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                });
    }

    private void parseBlocksOnHeadHeight(int startBlockHeight, Integer chainHeadHeight) {
        log.info("parseBlocks with from={} with chainHeadHeight={}", startBlockHeight, chainHeadHeight);
        if (chainHeadHeight != startBlockHeight) {
            if (startBlockHeight <= chainHeadHeight) {
                bsqFullNodeExecutor.parseBlocks(startBlockHeight,
                        chainHeadHeight,
                        this::onNewBsqBlock,
                        () -> {
                            // We are done but it might be that new blocks have arrived in the meantime,
                            // so we try again with startBlockHeight set to current chainHeadHeight
                            // We also set up the listener in the else main branch where we check
                            // if we are at chainTip, so do not include here another check as it would
                            // not trigger the listener registration.
                            requestChainHeadHeightAndParseBlocks(chainHeadHeight);
                        }, throwable -> {
                            if (throwable instanceof BlockNotConnectingException) {
                                startReOrgFromLastSnapshot();
                            } else {
                                log.error(throwable.toString());
                                throwable.printStackTrace();
                                //TODO write error to an errorProperty
                            }
                        });
            } else {
                log.warn("We are trying to start with a block which is above the chain height of bitcoin core. We need probably wait longer until bitcoin core has fully synced. We try again after a delay of 1 min.");
                UserThread.runAfter(() -> requestChainHeadHeightAndParseBlocks(startBlockHeight), 60);
            }
        } else {
            // We don't have received new blocks in the meantime so we are completed and we register our handler
            onParseBlockchainComplete();
        }
    }

    private void onParseBlockchainComplete() {
        log.info("onParseBlockchainComplete");
        parseBlockchainComplete = true;

        if (p2pNetworkReady)
            addBlockHandler();

        bsqBlockChainListeners.forEach(BsqBlockChainListener::onBsqBlockChainChanged);
    }
}
