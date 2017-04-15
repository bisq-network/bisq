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
import io.bisq.common.proto.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.wallet.WalletUtils;
import io.bisq.core.dao.RpcOptionKeys;
import io.bisq.core.dao.blockchain.exceptions.OrphanDetectedException;
import io.bisq.core.dao.blockchain.json.JsonExporter;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
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

    //TODO
    public interface TxOutputMapListener {
        void onTxOutputMapChanged(BsqChainState bsqChainState);
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
    private static final int SNAPSHOT_TRIGGER = 50000;  // set high to deactivate

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

    private final P2PService p2PService;
    private final BsqBlockchainRequest bsqBlockchainRequest;
    private final BsqChainState bsqChainState;
    private final JsonExporter jsonExporter;
    private final List<TxOutputMapListener> txOutputMapListeners = new ArrayList<>();

    //TODO
    private BsqChainState snapshotBsqChainState;

    @Getter
    private int chainHeadHeight;
    @Getter
    private boolean parseBlockchainComplete;
    private final boolean connectToBtcCore;
    //TODO
    private transient final Storage<BsqChainState> snapshotTxOutputMapStorage;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqBlockchainManager(P2PService p2PService,
                                BsqChainState bsqChainState,
                                BsqBlockchainRequest bsqBlockchainRequest,
                                JsonExporter jsonExporter,
                                PersistenceProtoResolver persistenceProtoResolver,
                                @Named(Storage.DIR_KEY) File storageDir,
                                @Named(RpcOptionKeys.RPC_USER) String rpcUser) {

        this.p2PService = p2PService;
        this.bsqChainState = bsqChainState;
        this.bsqBlockchainRequest = bsqBlockchainRequest;
        this.jsonExporter = jsonExporter;

        //TODO
        snapshotTxOutputMapStorage = new Storage<>(storageDir, persistenceProtoResolver);
        connectToBtcCore = rpcUser != null && !rpcUser.isEmpty();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        //TODO
        BsqChainState persisted = snapshotTxOutputMapStorage.initAndGetPersistedWithFileName("BsqBlockChain");
       /* if (persisted != null) {
            bsqChainState = persisted;
            // If we have persisted data we notify our listeners
            onBsqTxoChanged();
        }*/ /*else {
            bsqChainState = new BsqChainStateImpl();
        }*/

        if (connectToBtcCore)
            bsqBlockchainRequest.setup(this::onSetupComplete, errorMessageHandler);

        p2PService.addHashSetChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry data) {
                final StoragePayload storagePayload = data.getStoragePayload();
                //TODO
               /* if (storagePayload instanceof TxOutput) {
                   *//* if (txOutputMap.putTxOutput((TxOutput) storagePayload) == null)
                        onBsqTxoChanged();*//*
                }*/
            }

            @Override
            public void onRemoved(ProtectedStorageEntry data) {
                // We don't remove items
            }
        });
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
        int startBlockHeight = Math.max(genesisBlockHeight, bsqChainState.getChainHeadHeight() + 1);
        log.info("parseBlocks with:\n" +
                        "genesisTxId={}\n" +
                        "genesisBlockHeight={}\n" +
                        "startBlockHeight={}\n" +
                        "txOutputMap.blockHeight={}",
                genesisTxId,
                genesisBlockHeight,
                startBlockHeight,
                bsqChainState.getChainHeadHeight());

        parseBlocks(startBlockHeight,
                genesisBlockHeight,
                genesisTxId);
    }

    private void parseBlocks(int startBlockHeight, int genesisBlockHeight, String genesisTxId) {
        bsqBlockchainRequest.requestChainHeadHeight(chainHeadHeight -> {
            if (chainHeadHeight != startBlockHeight) {
                bsqBlockchainRequest.parseBlocks(startBlockHeight,
                        chainHeadHeight,
                        genesisBlockHeight,
                        genesisTxId,
                        bsqBlock -> {
                            applyNewTxOutputMap(bsqBlock);
                        }, () -> {
                            // we are done but it might be that new blocks have arrived in the meantime,
                            // so we try again with startBlockHeight set to current chainHeadHeight
                            if (newBlocksReceived())
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
                bsqBlockchainRequest.addBlockHandler(btcdBlock -> {
                    bsqBlockchainRequest.parseBlock(btcdBlock,
                            genesisBlockHeight,
                            genesisTxId,
                            bsqBlock -> {
                                // TODO
                                // if (bsqBlockChain.getChainTip() < bsqBlock.getChainTip()) {
                                applyNewTxOutputMap(bsqBlock);
                                checkForSnapshotUpdate(bsqBlock.getHeight());
                                log.debug("new block parsed. bsqBlock={}", bsqBlock);
                               /* } else {
                                    log.warn("We got a bsqBlock with a lower block height than the one from the " +
                                                    "map we requested. That should not happen, but theoretically could be " +
                                                    "if 2 blocks arrive at nearly the same time and the second is faster in " +
                                                    "parsing than the first, so the callback of the first will have a lower " +
                                                    "height. " +
                                                    "txOutputMap.getBlockHeight()={}; " +
                                                    "bsqBlock.getBlockHeight()={}\n" +
                                                    "To avoid conflicts we start a reorg from the last snapshot.",
                                            bsqBlockChain.getChainTip(),
                                            bsqBlock.getChainTip());
                                    startReOrgFromLastSnapshot(bsqBlock.getChainTip());
                                }*/
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

    //TODO
    private boolean newBlocksReceived() {
        return false;
    }

    //TODO
    private void startReOrgFromLastSnapshot(int blockHeight) {
        log.warn("We have to do a re-org because a new block did not connect to our chain.");
        int startBlockHeight = snapshotBsqChainState != null ? snapshotBsqChainState.getChainHeadHeight() : getGenesisBlockHeight();
        checkArgument(snapshotBsqChainState == null || startBlockHeight >= blockHeight - SNAPSHOT_TRIGGER);
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
                });
    }

    //TODO
    private void applyNewTxOutputMap(BsqBlock newBsqBlock) {
        // bsqBlockChain = newBsqBlockChain;
        txOutputMapListeners.stream().forEach(l -> l.onTxOutputMapChanged(bsqChainState));
        // updateSnapshotIfTrigger(newBlockMap.getChainTip());
    }

    //TODO
    private void checkForSnapshotUpdate(int blockHeight) {
        if (triggersSnapshot(blockHeight)) {
            // At trigger time we store the last memory stored map to disc
            if (snapshotBsqChainState != null) {
                // We clone because storage is in a threaded context
                // BsqChainStateImpl clonedSnapshotBsqChainStateImpl = BsqChainStateImpl.getClonedMap(snapshotBsqChainState);
                //  snapshotTxOutputMapStorage.queueUpForSave(clonedSnapshotBsqChainStateImpl);
            }

            // Now we save the map in memory for the next trigger
            // snapshotBsqChainStateImpl = BsqChainStateImpl.getClonedMap(bsqChainState);
        }
    }

    //TODO
    private void onBsqTxoChanged() {
        txOutputMapListeners.stream().forEach(e -> e.onTxOutputMapChanged(bsqChainState));
        jsonExporter.export(bsqChainState);
    }
}
