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
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.core.dao.blockchain.p2p.RequestManager;
import io.bisq.core.dao.blockchain.parse.BsqBlockChain;
import io.bisq.core.dao.blockchain.parse.BsqParser;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.P2PServiceListener;
import io.bisq.network.p2p.seed.SeedNodeRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

// We are in UserThread context. We get callbacks from threaded classes which are already mapped to the UserThread.
@Slf4j
public abstract class BsqNode {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    protected final P2PService p2PService;
    @SuppressWarnings("WeakerAccess")
    protected final BsqParser bsqParser;
    @SuppressWarnings("WeakerAccess")
    protected final BsqBlockChain bsqBlockChain;
    protected final SeedNodeRepository seedNodeRepository;
    @SuppressWarnings("WeakerAccess")
    protected final List<BsqBlockChainListener> bsqBlockChainListeners = new ArrayList<>();
    protected final String genesisTxId;
    protected final int genesisBlockHeight;
    protected RequestManager requestManager;

    @Getter
    protected boolean parseBlockchainComplete;
    @SuppressWarnings("WeakerAccess")
    protected boolean p2pNetworkReady;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqNode(P2PService p2PService,
                   BsqParser bsqParser,
                   BsqBlockChain bsqBlockChain,
                   FeeService feeService,
                   SeedNodeRepository seedNodeRepository) {

        this.p2PService = p2PService;
        this.bsqParser = bsqParser;
        this.bsqBlockChain = bsqBlockChain;
        this.seedNodeRepository = seedNodeRepository;

        genesisTxId = bsqBlockChain.getGenesisTxId();
        genesisBlockHeight = bsqBlockChain.getGenesisBlockHeight();

        bsqBlockChain.setCreateCompensationRequestFee(feeService.getCreateCompensationRequestFee().value,
                genesisBlockHeight);
        bsqBlockChain.setVotingFee(feeService.getVotingTxFee().value,
                genesisBlockHeight);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        applySnapshot();
        log.info("onAllServicesInitialized");
        if (p2PService.isBootstrapped()) {
            log.info("onAllServicesInitialized: isBootstrapped");
            onP2PNetworkReady();
        } else {
            p2PService.addP2PServiceListener(new P2PServiceListener() {
                @Override
                public void onTorNodeReady() {
                }

                @Override
                public void onHiddenServicePublished() {
                }

                @Override
                public void onSetupFailed(Throwable throwable) {
                }

                @Override
                public void onRequestCustomBridges() {
                }

                @Override
                public void onDataReceived() {
                }

                @Override
                public void onNoSeedNodeAvailable() {
                    log.info("onAllServicesInitialized: onNoSeedNodeAvailable");
                    onP2PNetworkReady();
                }

                @Override
                public void onNoPeersAvailable() {
                }

                @Override
                public void onUpdatedDataReceived() {
                    log.info("onAllServicesInitialized: onBootstrapComplete");
                    onP2PNetworkReady();
                }
            });
        }
    }

    private void applySnapshot() {
        bsqBlockChain.applySnapshot();
        bsqBlockChainListeners.stream().forEach(BsqBlockChainListener::onBsqBlockChainChanged);
    }

    @SuppressWarnings("WeakerAccess")
    protected void onP2PNetworkReady() {
        p2pNetworkReady = true;
    }

    @SuppressWarnings("WeakerAccess")
    protected void startParseBlocks() {
        int startBlockHeight = Math.max(genesisBlockHeight, bsqBlockChain.getChainHeadHeight() + 1);
        log.info("Start parse blocks:\n" +
                        "   Start block height={}\n" +
                        "   Genesis txId={}\n" +
                        "   Genesis block height={}\n" +
                        "   BsqBlockChain block height={}\n",
                startBlockHeight,
                genesisTxId,
                genesisBlockHeight,
                bsqBlockChain.getChainHeadHeight());

        parseBlocksWithChainHeadHeight(startBlockHeight,
                genesisBlockHeight,
                genesisTxId);
    }

    abstract protected void parseBlocksWithChainHeadHeight(int startBlockHeight, int genesisBlockHeight, String genesisTxId);

    abstract protected void parseBlocks(int startBlockHeight, int genesisBlockHeight, String genesisTxId, Integer chainHeadHeight);

    abstract protected void onParseBlockchainComplete(int genesisBlockHeight, String genesisTxId);

    @SuppressWarnings("WeakerAccess")
    protected void onNewBsqBlock(BsqBlock bsqBlock) {
        //TODO called at each block at startup parsing. cause a lot of cpu waste at listeners...
        // -> make more fine grained callbacks so UI only listens on final results when parsing is complete
        bsqBlockChainListeners.stream().forEach(BsqBlockChainListener::onBsqBlockChainChanged);
    }

    @SuppressWarnings("WeakerAccess")
    protected void startReOrgFromLastSnapshot() {
        applySnapshot();
        startParseBlocks();
    }

    public void addBsqBlockChainListener(BsqBlockChainListener bsqBlockChainListener) {
        bsqBlockChainListeners.add(bsqBlockChainListener);
    }

    public void removeBsqBlockChainListener(BsqBlockChainListener bsqBlockChainListener) {
        bsqBlockChainListeners.remove(bsqBlockChainListener);
    }
}
