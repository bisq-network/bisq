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
import io.bisq.core.dao.blockchain.parse.BsqChainState;
import io.bisq.core.dao.blockchain.parse.BsqParser;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.P2PServiceListener;
import io.bisq.network.p2p.seed.SeedNodesRepository;
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
    protected final BsqChainState bsqChainState;
    protected final SeedNodesRepository seedNodesRepository;
    @SuppressWarnings("WeakerAccess")
    protected final List<BsqChainStateListener> bsqChainStateListeners = new ArrayList<>();
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
                   BsqChainState bsqChainState,
                   FeeService feeService,
                   SeedNodesRepository seedNodesRepository) {

        this.p2PService = p2PService;
        this.bsqParser = bsqParser;
        this.bsqChainState = bsqChainState;
        this.seedNodesRepository = seedNodesRepository;

        genesisTxId = bsqChainState.getGenesisTxId();
        genesisBlockHeight = bsqChainState.getGenesisBlockHeight();

        bsqChainState.setCreateCompensationRequestFee(feeService.getCreateCompensationRequestFee().value,
                genesisBlockHeight);
        bsqChainState.setVotingFee(feeService.getVotingTxFee().value,
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
                public void onRequestingDataCompleted() {
                }

                @Override
                public void onNoSeedNodeAvailable() {
                    log.info("onAllServicesInitialized: onNoPeersAvailable");
                    onP2PNetworkReady();
                }

                @Override
                public void onNoPeersAvailable() {
                }

                @Override
                public void onBootstrapComplete() {
                    log.info("onAllServicesInitialized: onBootstrapComplete");
                    onP2PNetworkReady();
                }
            });
        }
    }

    private void applySnapshot() {
        bsqChainState.applySnapshot();
        bsqChainStateListeners.stream().forEach(BsqChainStateListener::onBsqChainStateChanged);
    }

    @SuppressWarnings("WeakerAccess")
    protected void onP2PNetworkReady() {
        p2pNetworkReady = true;
    }

    @SuppressWarnings("WeakerAccess")
    protected void startParseBlocks() {
        int startBlockHeight = Math.max(genesisBlockHeight, bsqChainState.getChainHeadHeight() + 1);
        log.info("Start parse blocks:\n" +
                        "   Start block height={}\n" +
                        "   Genesis txId={}\n" +
                        "   Genesis block height={}\n" +
                        "   BsqChainState block height={}\n",
                startBlockHeight,
                genesisTxId,
                genesisBlockHeight,
                bsqChainState.getChainHeadHeight());

        parseBlocksWithChainHeadHeight(startBlockHeight,
                genesisBlockHeight,
                genesisTxId);
    }

    abstract protected void parseBlocksWithChainHeadHeight(int startBlockHeight, int genesisBlockHeight, String genesisTxId);

    abstract protected void parseBlocks(int startBlockHeight, int genesisBlockHeight, String genesisTxId, Integer chainHeadHeight);

    abstract protected void onParseBlockchainComplete(int genesisBlockHeight, String genesisTxId);

    @SuppressWarnings("WeakerAccess")
    protected void onNewBsqBlock(BsqBlock bsqBlock) {
        bsqChainStateListeners.stream().forEach(BsqChainStateListener::onBsqChainStateChanged);
    }

    @SuppressWarnings("WeakerAccess")
    protected void startReOrgFromLastSnapshot() {
        applySnapshot();
        startParseBlocks();
    }

    public void addBsqChainStateListener(BsqChainStateListener bsqChainStateListener) {
        bsqChainStateListeners.add(bsqChainStateListener);
    }

    public void removeBsqChainStateListener(BsqChainStateListener bsqChainStateListener) {
        bsqChainStateListeners.remove(bsqChainStateListener);
    }
}
