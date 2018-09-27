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

package bisq.core.dao.node;

import bisq.core.dao.DaoSetupService;
import bisq.core.dao.node.parser.BlockParser;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.SnapshotManager;
import bisq.core.dao.state.blockchain.RawBlock;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.P2PServiceListener;

import bisq.common.handlers.ErrorMessageHandler;

import com.google.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Base class for the lite and full node.
 * It is responsible or the setup of the parser and snapshot management.
 */
@Slf4j
public abstract class BsqNode implements DaoSetupService {
    protected final BlockParser blockParser;
    private final P2PService p2PService;
    protected final BsqStateService bsqStateService;
    private final String genesisTxId;
    private final int genesisBlockHeight;
    private final SnapshotManager snapshotManager;
    private final P2PServiceListener p2PServiceListener;
    protected boolean parseBlockchainComplete;
    protected boolean p2pNetworkReady;
    @Nullable
    protected ErrorMessageHandler errorMessageHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqNode(BlockParser blockParser,
                   BsqStateService bsqStateService,
                   SnapshotManager snapshotManager,
                   P2PService p2PService) {
        this.blockParser = blockParser;
        this.bsqStateService = bsqStateService;
        this.snapshotManager = snapshotManager;
        this.p2PService = p2PService;

        genesisTxId = bsqStateService.getGenesisTxId();
        genesisBlockHeight = bsqStateService.getGenesisBlockHeight();

        p2PServiceListener = new P2PServiceListener() {
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
                onP2PNetworkReady();
            }

            @Override
            public void onNoPeersAvailable() {
            }

            @Override
            public void onUpdatedDataReceived() {
                onP2PNetworkReady();
            }
        };
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DaoSetupService
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void addListeners() {
    }

    @Override
    public abstract void start();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setErrorMessageHandler(@Nullable ErrorMessageHandler errorMessageHandler) {
        this.errorMessageHandler = errorMessageHandler;
    }

    public abstract void shutDown();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    protected void onInitialized() {
        applySnapshot();

        if (p2PService.isBootstrapped()) {
            log.info("onAllServicesInitialized: isBootstrapped");
            onP2PNetworkReady();
        } else {
            p2PService.addP2PServiceListener(p2PServiceListener);
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void onP2PNetworkReady() {
        p2pNetworkReady = true;
        p2PService.removeP2PServiceListener(p2PServiceListener);
    }

    @SuppressWarnings("WeakerAccess")
    protected int getStartBlockHeight() {
        int chainHeight = bsqStateService.getChainHeight();
        int startBlockHeight = chainHeight;
        if (chainHeight > genesisBlockHeight)
            startBlockHeight = chainHeight + 1;

        log.info("Start parse blocks:\n" +
                        "   Start block height={}\n" +
                        "   Genesis txId={}\n" +
                        "   Genesis block height={}\n" +
                        "   Block height={}\n",
                startBlockHeight,
                genesisTxId,
                genesisBlockHeight,
                chainHeight);

        return startBlockHeight;
    }

    abstract protected void startParseBlocks();

    protected void onParseBlockChainComplete() {
        log.info("onParseBlockChainComplete");
        parseBlockchainComplete = true;
        bsqStateService.onParseBlockChainComplete();

        // log.error("COMPLETED: sb1={}\nsb2={}", BlockParser.sb1.toString(), BlockParser.sb2.toString());
        // log.error("equals? " + BlockParser.sb1.toString().equals(BlockParser.sb2.toString()));
        // Utilities.copyToClipboard(BlockParser.sb1.toString() + "\n\n\n" + BlockParser.sb2.toString());
    }

    @SuppressWarnings("WeakerAccess")
    protected void startReOrgFromLastSnapshot() {
        applySnapshot();
        startParseBlocks();
    }

    protected boolean isBlockAlreadyAdded(RawBlock rawBlock) {
        return bsqStateService.getBlockAtHeight(rawBlock.getHeight()).isPresent();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applySnapshot() {
        snapshotManager.applySnapshot();
    }
}
