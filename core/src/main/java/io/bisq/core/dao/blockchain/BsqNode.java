/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.dao.blockchain;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.proto.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.BitcoinNetwork;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.network.p2p.BootstrapListener;
import io.bisq.network.p2p.P2PService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Named;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// We are in UserThread context. We get callbacks from threaded classes which are already mapped to the UserThread.
@Slf4j
public abstract class BsqNode {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Modulo of blocks for making snapshots of UTXO.
    // We stay also the value behind for safety against reorgs.
    // E.g. for SNAPSHOT_TRIGGER = 30:
    // If we are block 119 and last snapshot was 60 then we get a new trigger for a snapshot at block 120 and
    // new snapshot is block 90. We only persist at the new snapshot, so we always re-parse from latest snapshot after
    // a restart.
    // As we only store snapshots when Txos are added it might be that there are bigger gaps than SNAPSHOT_TRIGGER.
    private static final int SNAPSHOT_GRID = 10;  // set high to deactivate

    //mainnet
    private static final String GENESIS_TX_ID = "cabbf6073aea8f22ec678e973ac30c6d8fc89321011da6a017f63e67b9f66667";
    // block 300000 2014-05-10
    // block 350000 2015-03-30
    // block 400000 2016-02-25
    // block 450000 2017-01-25
    private static final int GENESIS_BLOCK_HEIGHT = 400000;
    private static final String REG_TEST_GENESIS_TX_ID = "3bc7bc9484e112ec8ddd1a1c984379819245ac463b9ce40fa8b5bf771c0f9236";
    private static final int REG_TEST_GENESIS_BLOCK_HEIGHT = 102;
    private boolean isRegTest;

    @SuppressWarnings("WeakerAccess")
    protected String getGenesisTxId() {
        return isRegTest ? REG_TEST_GENESIS_TX_ID : GENESIS_TX_ID;
    }

    @SuppressWarnings("WeakerAccess")
    protected int getGenesisBlockHeight() {
        return isRegTest ? REG_TEST_GENESIS_BLOCK_HEIGHT : GENESIS_BLOCK_HEIGHT;
    }

    @VisibleForTesting
    static int getSnapshotHeight(int genesisHeight, int height, int grid) {
        return Math.round(Math.max(genesisHeight + 3 * grid, height) / grid) * grid - grid;
    }

    protected int getSnapshotHeight(int height) {
        return getSnapshotHeight(getGenesisBlockHeight(), height, SNAPSHOT_GRID);
    }

    @VisibleForTesting
    static boolean isSnapshotHeight(int genesisHeight, int height, int grid) {
        return height % grid == 0 && height >= getSnapshotHeight(genesisHeight, height, grid);
    }

    private boolean isSnapshotHeight(int height) {
        return isSnapshotHeight(getGenesisBlockHeight(), height, SNAPSHOT_GRID);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    protected final P2PService p2PService;
    @SuppressWarnings("WeakerAccess")
    protected final BsqChainState bsqChainState;
    @SuppressWarnings("WeakerAccess")
    protected final BsqParser bsqParser;
    @SuppressWarnings("WeakerAccess")
    protected final List<BsqChainStateListener> bsqChainStateListeners = new ArrayList<>();
    private final Storage<BsqChainState> snapshotBsqChainStateStorage;

    @Getter
    protected boolean parseBlockchainComplete;
    private BsqChainState snapshotBsqChainState;
    @SuppressWarnings("WeakerAccess")
    protected boolean p2pNetworkReady;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqNode(BisqEnvironment bisqEnvironment,
                   P2PService p2PService,
                   BsqChainState bsqChainState,
                   BsqParser bsqParser,
                   PersistenceProtoResolver persistenceProtoResolver,
                   @Named(Storage.STORAGE_DIR) File storageDir) {

        this.p2PService = p2PService;
        this.bsqChainState = bsqChainState;
        this.bsqParser = bsqParser;

        isRegTest = bisqEnvironment.getBitcoinNetwork() == BitcoinNetwork.REGTEST;
        snapshotBsqChainStateStorage = new Storage<>(storageDir, persistenceProtoResolver);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        BsqChainState persistedBsqChainState = snapshotBsqChainStateStorage.initAndGetPersistedWithFileName("BsqChainState");
        if (persistedBsqChainState != null) {
            bsqChainState.applyPersisted(persistedBsqChainState);
            bsqChainStateListeners.stream().forEach(BsqChainStateListener::onBsqChainStateChanged);
        }

        if (p2PService.isBootstrapped()) {
            onP2PNetworkReady();
        } else {
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onBootstrapComplete() {
                    onP2PNetworkReady();
                }
            });
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void onP2PNetworkReady() {
        p2pNetworkReady = true;
    }

    @SuppressWarnings("WeakerAccess")
    protected void startParseBlocks() {
        final int genesisBlockHeight = getGenesisBlockHeight();
        final String genesisTxId = getGenesisTxId();
        int startBlockHeight = Math.max(genesisBlockHeight, bsqChainState.getChainHeadHeight() + 1);
        log.info("Parse blocks:\n" +
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
        //log.error("onNewBsqBlock " + bsqBlock.getHeight());
        bsqChainStateListeners.stream().forEach(BsqChainStateListener::onBsqChainStateChanged);
        maybeMakeSnapshot();
    }

    //TODO
    @SuppressWarnings("WeakerAccess")
    protected void startReOrgFromLastSnapshot(BsqBlock bsqBlock) {
        log.error("not connection block: bsqBlock.getHeight()={}, bsqBlock.getHash()={}", bsqBlock.getHeight(), bsqBlock.getHash());
       /* log.warn("We have to do a re-org because a new block did not connected to our chain.");
        int startBlockHeight = snapshotBsqChainState != null ? snapshotBsqChainState.getChainHeadHeight() : getGenesisBlockHeight();
        checkArgument(snapshotBsqChainState == null || startBlockHeight >= blockHeight - SNAPSHOT_GRID);
        bsqBlockchainRequest.requestBlock(startBlockHeight,
                block -> {
                    // TODO
                    if (snapshotBsqChainState != null) {
                        checkArgument(startBlockHeight <= block.getHeight());
                        // checkArgument(block.getHash().equals(snapshotBsqBlockChain.getBlockHash()));
                        // applyNewTxOutputMap(snapshotBsqBlockChain);
                    } else {
                        // applyNewTxOutputMap(new BsqBlockChain());
                    }
                    parseBlocks(startBlockHeight,
                            getGenesisBlockHeight(),
                            getGenesisTxId());
                }, throwable -> {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                });*/
    }

    @SuppressWarnings("WeakerAccess")
    protected void maybeMakeSnapshot() {
        // We might have got updates in the bsqChainState by another thread.
        // We get called on UserThread.execute so we are slower...
        final int chainHeadHeight = bsqChainState.getChainHeadHeight();
        if (isSnapshotHeight(chainHeadHeight) &&
                (snapshotBsqChainState == null ||
                        snapshotBsqChainState.getChainHeadHeight() != chainHeadHeight)) {
            // At trigger time we store the last memory stored map to disc
            if (snapshotBsqChainState != null) {
                // We clone because storage is in a threaded context
                BsqChainState cloned = BsqChainState.getClone(snapshotBsqChainState);
                snapshotBsqChainStateStorage.queueUpForSave(cloned);
                log.debug("Saved snapshotBsqChainState to Disc at height " + cloned.getChainHeadHeight());
            }

            // Now we save the map in memory for the next trigger
            snapshotBsqChainState = BsqChainState.getClone(bsqChainState);
        }
    }

    public void addBsqChainStateListener(BsqChainStateListener bsqChainStateListener) {
        bsqChainStateListeners.add(bsqChainStateListener);
    }
}
