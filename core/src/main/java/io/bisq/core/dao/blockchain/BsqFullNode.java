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
import io.bisq.core.dao.blockchain.json.JsonBlockChainExporter;
import io.bisq.core.dao.blockchain.p2p.RequestManager;
import io.bisq.core.dao.blockchain.parse.BsqBlockChain;
import io.bisq.core.dao.blockchain.parse.BsqFullNodeExecutor;
import io.bisq.core.dao.blockchain.parse.BsqParser;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.network.p2p.P2PService;
import lombok.extern.slf4j.Slf4j;

/**
 * Main class for a full node which have Bitcoin Core with rpc running and does the blockchain lookup itself.
 * It also provides the BSQ transactions to lit nodes on request.
 */
@Slf4j
public class BsqFullNode extends BsqNode {

    private final BsqFullNodeExecutor bsqFullNodeExecutor;
    private final JsonBlockChainExporter jsonBlockChainExporter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqFullNode(P2PService p2PService,
                       BsqParser bsqParser,
                       BsqFullNodeExecutor bsqFullNodeExecutor,
                       BsqBlockChain bsqBlockChain,
                       JsonBlockChainExporter jsonBlockChainExporter,
                       FeeService feeService,
                       RequestManager requestManager) {
        super(p2PService,
                bsqParser,
                bsqBlockChain,
                feeService,
                requestManager);
        this.bsqFullNodeExecutor = bsqFullNodeExecutor;
        this.jsonBlockChainExporter = jsonBlockChainExporter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown() {
        super.shutDown();
        jsonBlockChainExporter.shutDown();
    }

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

    @Override
    protected void startParseBlocks() {
        parseBlocks(getStartBlockHeight(), genesisBlockHeight, genesisTxId);
    }

    private void parseBlocks(int startBlockHeight, int genesisBlockHeight, String genesisTxId) {
        log.info("parseBlocksWithChainHeadHeight startBlockHeight={}", startBlockHeight);
        bsqFullNodeExecutor.requestChainHeadHeight(chainHeadHeight -> parseBlocks(startBlockHeight, genesisBlockHeight, genesisTxId, chainHeadHeight),
                throwable -> {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                });
    }

    private void parseBlocks(int startBlockHeight, int genesisBlockHeight, String genesisTxId, Integer chainHeadHeight) {
        log.info("parseBlocks with from={} with chainHeadHeight={}", startBlockHeight, chainHeadHeight);
        if (chainHeadHeight != startBlockHeight) {
            if (startBlockHeight <= chainHeadHeight) {
                bsqFullNodeExecutor.parseBlocks(startBlockHeight,
                        chainHeadHeight,
                        genesisBlockHeight,
                        genesisTxId,
                        this::onNewBsqBlock,
                        () -> {
                            // we are done but it might be that new blocks have arrived in the meantime,
                            // so we try again with startBlockHeight set to current chainHeadHeight
                            // We also set up the listener in the else main branch where we check
                            // if we at chainTip, so do nto include here another check as it would
                            // not trigger the listener registration.
                            parseBlocks(chainHeadHeight,
                                    genesisBlockHeight,
                                    genesisTxId);
                        }, throwable -> {
                            if (throwable instanceof BlockNotConnectingException) {
                                startReOrgFromLastSnapshot();
                            } else {
                                log.error(throwable.toString());
                                throwable.printStackTrace();
                            }
                        });
            } else {
                log.warn("We are trying to start with a block which is above the chain height of bitcoin core. We need probably wait longer until bitcoin core has fully synced. We try again after a delay of 1 min.");
                UserThread.runAfter(() -> parseBlocks(startBlockHeight, genesisBlockHeight, genesisTxId), 60);
            }
        } else {
            // We don't have received new blocks in the meantime so we are completed and we register our handler
            onParseBlockchainComplete(genesisBlockHeight, genesisTxId);
        }
    }

    @Override
    protected void onP2PNetworkReady() {
        super.onP2PNetworkReady();

        if (parseBlockchainComplete)
            addBlockHandler();
    }

    private void onParseBlockchainComplete(int genesisBlockHeight, String genesisTxId) {
        log.info("onParseBlockchainComplete");
        parseBlockchainComplete = true;

        if (p2pNetworkReady)
            addBlockHandler();

        bsqBlockChainListeners.forEach(BsqBlockChainListener::onBsqBlockChainChanged);
    }


    private void addBlockHandler() {
        bsqFullNodeExecutor.addBlockHandler(btcdBlock -> bsqFullNodeExecutor.parseBtcdBlock(btcdBlock,
                genesisBlockHeight,
                genesisTxId,
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

    @Override
    protected void onNewBsqBlock(BsqBlock bsqBlock) {
        super.onNewBsqBlock(bsqBlock);
        jsonBlockChainExporter.maybeExport();
        if (parseBlockchainComplete && p2pNetworkReady)
            requestManager.publishNewBlock(bsqBlock);
    }
}
