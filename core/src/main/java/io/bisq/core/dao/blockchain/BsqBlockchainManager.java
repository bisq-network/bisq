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

import com.google.inject.Inject;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.wallet.WalletUtils;
import io.bisq.core.dao.RpcOptionKeys;
import io.bisq.core.dao.blockchain.json.JsonExporter;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import io.bisq.network.p2p.storage.payload.StoragePayload;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Named;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;

// We are in UserThread context. We get callbacks from threaded classes which are already mapped to the UserThread.
@Slf4j
public class BsqBlockchainManager {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface
    ///////////////////////////////////////////////////////////////////////////////////////////


    public interface TxOutputMapListener {
        void onTxOutputMapChanged(TxOutputMap txOutputMap);
    }

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
    private static final int SNAPSHOT_TRIGGER = 50;  // set high to deactivate

    public static int getSnapshotTrigger() {
        return SNAPSHOT_TRIGGER;
    }

    //mainnet
    private static final String GENESIS_TX_ID = "cabbf6073aea8f22ec678e973ac30c6d8fc89321011da6a017f63e67b9f66667";
    // block 300000 2014-05-10 
    // block 350000 2015-03-30
    // block 400000 2016-02-25
    // block 450000 2017-01-25
    private static final int GENESIS_BLOCK_HEIGHT = 400000;
    private static final String REG_TEST_GENESIS_TX_ID = "3bc7bc9484e112ec8ddd1a1c984379819245ac463b9ce40fa8b5bf771c0f9236";
    private static final int REG_TEST_GENESIS_BLOCK_HEIGHT = 102;

    private static String getGenesisTxId() {
        return WalletUtils.isRegTest() ? REG_TEST_GENESIS_TX_ID : GENESIS_TX_ID;
    }

    private static int getGenesisBlockHeight() {
        return WalletUtils.isRegTest() ? REG_TEST_GENESIS_BLOCK_HEIGHT : GENESIS_BLOCK_HEIGHT;
    }

    public static int getSnapshotHeight(int height) {
        final int trigger = Math.round(height / SNAPSHOT_TRIGGER) * SNAPSHOT_TRIGGER - SNAPSHOT_TRIGGER;
        return Math.max(getGenesisBlockHeight() + SNAPSHOT_TRIGGER, trigger);
    }

    public static boolean triggersSnapshot(int height) {
        return height - SNAPSHOT_TRIGGER == getSnapshotHeight(height);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final BsqBlockchainService blockchainService;
    private final P2PService p2PService;
    private final JsonExporter jsonExporter;
    private final List<TxOutputMapListener> txOutputMapListeners = new ArrayList<>();

    @Getter
    private TxOutputMap txOutputMap;
    private TxOutputMap snapshotTxOutputMap;

    @Getter
    private int chainHeadHeight;
    @Getter
    private boolean parseBlockchainComplete;
    private final boolean connectToBtcCore;
    private transient final Storage<TxOutputMap> snapshotTxOutputMapStorage;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqBlockchainManager(BsqBlockchainService blockchainService,
                                P2PService p2PService,
                                JsonExporter jsonExporter,
                                @Named(Storage.DIR_KEY) File storageDir,
                                @Named(RpcOptionKeys.RPC_USER) String rpcUser) {
        this.blockchainService = blockchainService;
        this.p2PService = p2PService;
        this.jsonExporter = jsonExporter;
        snapshotTxOutputMapStorage = new Storage<>(storageDir);

        connectToBtcCore = rpcUser != null && !rpcUser.isEmpty();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        TxOutputMap persisted = snapshotTxOutputMapStorage.initAndGetPersistedWithFileName("TxOutputMap");
        if (persisted != null) {
            txOutputMap = persisted;
            // If we have persisted data we notify our listeners
            onBsqTxoChanged();
        } else {
            txOutputMap = new TxOutputMap();
        }

        if (connectToBtcCore)
            blockchainService.setup(this::onSetupComplete, errorMessageHandler);

        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                final StoragePayload storagePayload = data.getStoragePayload();
                if (storagePayload instanceof TxOutput)
                    add((TxOutput) storagePayload);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                // We don't remove items
            }
        });
    }

    public void add(TxOutput txOutput) {
        if (txOutputMap.put(txOutput) == null)
            onBsqTxoChanged();
    }

    public void addTxOutputMapListener(BsqBlockchainManager.TxOutputMapListener txOutputMapListener) {
        txOutputMapListeners.add(txOutputMapListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onSetupComplete() {
        final int genesisBlockHeight = getGenesisBlockHeight();
        final String genesisTxId = getGenesisTxId();
        int startBlockHeight = Math.max(genesisBlockHeight, txOutputMap.getBlockHeight() + 1);
        log.info("parseBlocks with:\n" +
                        "genesisTxId={}\n" +
                        "genesisBlockHeight={}\n" +
                        "startBlockHeight={}\n" +
                        "txOutputMap.blockHeight={}",
                genesisTxId,
                genesisBlockHeight,
                startBlockHeight,
                txOutputMap.getBlockHeight());

        parseBlocks(startBlockHeight,
                genesisBlockHeight,
                genesisTxId);
    }

    private void parseBlocks(int startBlockHeight, int genesisBlockHeight, String genesisTxId) {
        blockchainService.requestChainHeadHeight(chainHeadHeight -> {
            if (chainHeadHeight != startBlockHeight) {
                blockchainService.parseBlocks(startBlockHeight,
                        chainHeadHeight,
                        genesisBlockHeight,
                        genesisTxId,
                        txOutputMap,
                        newBlockMap -> {
                            applyNewTxOutputMap(newBlockMap);
                            updateSnapshotIfTrigger(newBlockMap.getBlockHeight());
                        }, chainTipTxOutputMap -> {
                            // we are done but it might be that new blocks have arrived in the meantime,
                            // so we try again with startBlockHeight set to current chainHeadHeight
                            applyNewTxOutputMap(chainTipTxOutputMap);
                            parseBlocks(chainHeadHeight,
                                    genesisBlockHeight,
                                    genesisTxId);
                        }, throwable -> {
                            if (throwable instanceof OrphanDetectedException) {
                                startReOrgFromLastSnapshot(((OrphanDetectedException) throwable).getBlockHeight());
                            } else {
                                log.error(throwable.toString());
                                throwable.printStackTrace();
                            }
                        });
            } else {
                // We dont have received new blocks in the meantime so we are completed and we register our handler
                BsqBlockchainManager.this.chainHeadHeight = chainHeadHeight;
                parseBlockchainComplete = true;

                // We register our handler for new blocks
                blockchainService.addBlockHandler(bsqBlock -> {
                    blockchainService.parseBlock(bsqBlock,
                            genesisBlockHeight,
                            genesisTxId,
                            txOutputMap,
                            newBlockMap -> {
                                if (txOutputMap.getBlockHeight() < newBlockMap.getBlockHeight()) {
                                    applyNewTxOutputMap(newBlockMap);
                                    updateSnapshotIfTrigger(newBlockMap.getBlockHeight());
                                    log.debug("new block parsed. bsqBlock={}", bsqBlock);
                                } else {
                                    log.warn("We got a newBlockMap with a lower block height than the one from the " +
                                                    "map we requested. That should not happen, but theoretically could be " +
                                                    "if 2 blocks arrive at nearly the same time and the second is faster in " +
                                                    "parsing than the first, so the callback of the first will have a lower " +
                                                    "height. " +
                                                    "txOutputMap.getBlockHeight()={}; " +
                                                    "newBlockMap.getBlockHeight()={}\n" +
                                                    "To avoid conflicts we start a reorg from the last snapshot.",
                                            txOutputMap.getBlockHeight(),
                                            newBlockMap.getBlockHeight());
                                    startReOrgFromLastSnapshot(newBlockMap.getBlockHeight());
                                }
                            }, throwable -> {
                                if (throwable instanceof OrphanDetectedException) {
                                    startReOrgFromLastSnapshot(((OrphanDetectedException) throwable).getBlockHeight());
                                } else {
                                    log.error(throwable.toString());
                                    throwable.printStackTrace();
                                }
                            });
                });
            }
        }, throwable -> {
            log.error(throwable.toString());
            throwable.printStackTrace();
        });
    }

    private void startReOrgFromLastSnapshot(int blockHeight) {
        log.warn("We have to do a re-org because a new block did not connect to our chain.");
        int startBlockHeight = snapshotTxOutputMap != null ? snapshotTxOutputMap.getBlockHeight() : getGenesisBlockHeight();
        checkArgument(snapshotTxOutputMap == null || startBlockHeight >= blockHeight - SNAPSHOT_TRIGGER);
        blockchainService.requestBlock(startBlockHeight,
                block -> {
                    if (snapshotTxOutputMap != null) {
                        checkArgument(startBlockHeight <= block.getHeight());
                        checkArgument(block.getHash().equals(snapshotTxOutputMap.getBlockHash()));
                        applyNewTxOutputMap(snapshotTxOutputMap);
                    } else {
                        applyNewTxOutputMap(new TxOutputMap());
                    }
                    parseBlocks(startBlockHeight,
                            getGenesisBlockHeight(),
                            getGenesisTxId());
                }, throwable -> {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                });
    }

    private void applyNewTxOutputMap(TxOutputMap newTxOutputMap) {
        txOutputMap = newTxOutputMap;
        txOutputMapListeners.stream().forEach(l -> l.onTxOutputMapChanged(txOutputMap));
    }

    private void updateSnapshotIfTrigger(int blockHeight) {
        if (triggersSnapshot(blockHeight)) {
            // At trigger time we store the last memory stored map to disc
            if (snapshotTxOutputMap != null) {
                // We clone because storage is in a threaded context
                TxOutputMap clonedSnapshotTxOutputMap = TxOutputMap.getClonedMap(snapshotTxOutputMap);
                snapshotTxOutputMapStorage.queueUpForSave(clonedSnapshotTxOutputMap);
            }

            // Now we save the map in memory for the next trigger
            snapshotTxOutputMap = TxOutputMap.getClonedMap(txOutputMap);
        }
    }

    private void onBsqTxoChanged() {
        txOutputMapListeners.stream().forEach(e -> e.onTxOutputMapChanged(txOutputMap));
        jsonExporter.export(txOutputMap);
    }
}
