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
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.BitcoinNetwork;
import io.bisq.core.dao.RpcOptionKeys;
import io.bisq.core.dao.blockchain.json.JsonExporter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Named;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class BsqBlockchainManager {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    //mainnet
    private static final String GENESIS_TX_ID = "cabbf6073aea8f22ec678e973ac30c6d8fc89321011da6a017f63e67b9f66667";
    // block 300000 2014-05-10
    // block 350000 2015-03-30
    // block 400000 2016-02-25
    // block 450000 2017-01-25
    private static final int GENESIS_BLOCK_HEIGHT = 400000;
    private static final String REG_TEST_GENESIS_TX_ID = "3bc7bc9484e112ec8ddd1a1c984379819245ac463b9ce40fa8b5bf771c0f9236";
    private static final int REG_TEST_GENESIS_BLOCK_HEIGHT = 102;

    // Modulo of blocks for making snapshots of UTXO.
    // We stay also the value behind for safety against reorgs.
    // E.g. for SNAPSHOT_TRIGGER = 30:
    // If we are block 119 and last snapshot was 60 then we get a new trigger for a snapshot at block 120 and
    // new snapshot is block 90. We only persist at the new snapshot, so we always re-parse from latest snapshot after
    // a restart.
    private static final int SNAPSHOT_TRIGGER = 300000;

    public static int getSnapshotTrigger() {
        return SNAPSHOT_TRIGGER;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final BsqBlockchainService blockchainService;
    private final JsonExporter jsonExporter;
    private final BitcoinNetwork bitcoinNetwork;
    private final List<TxOutputMap.Listener> txOutputMapListeners = new ArrayList<>();

    @Getter
    private final TxOutputMap txOutputMap;
    @Getter
    private int chainHeadHeight;
    @Getter
    private boolean parseBlockchainComplete;
    private final boolean connectToBtcCore;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqBlockchainManager(BsqBlockchainService blockchainService,
                                BisqEnvironment bisqEnvironment,
                                JsonExporter jsonExporter,
                                @Named(Storage.DIR_KEY) File storageDir,
                                @Named(RpcOptionKeys.RPC_USER) String rpcUser) {
        this.blockchainService = blockchainService;
        this.jsonExporter = jsonExporter;
        this.bitcoinNetwork = bisqEnvironment.getBitcoinNetwork();
        connectToBtcCore = rpcUser != null && !rpcUser.isEmpty();
        txOutputMap = new TxOutputMap(storageDir);
        txOutputMap.addListener(bsqTxOutputMap -> onBsqTxoChanged());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        if (connectToBtcCore)
            blockchainService.setup(this::onSetupComplete, errorMessageHandler);
    }

    public void addTxOutputMapListener(TxOutputMap.Listener listener) {
        txOutputMapListeners.add(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onSetupComplete() {
        final int genesisBlockHeight = getGenesisBlockHeight();
        final String genesisTxId = getGenesisTxId();
        int startBlockHeight = Math.max(genesisBlockHeight, txOutputMap.getSnapshotHeight());
        log.info("parseBlocks with: genesisTxId={}\ngenesisBlockHeight={}\nstartBlockHeight={}\nsnapshotHeight={}",
                genesisTxId, genesisBlockHeight, startBlockHeight, txOutputMap.getSnapshotHeight());

        // If we have past data we notify our listeners
        if (txOutputMap.getSnapshotHeight() > 0)
            onBsqTxoChanged();

        parseBlocks(startBlockHeight,
                genesisBlockHeight,
                genesisTxId);
    }

    // TODO handle reorgs

    private void parseBlocks(int startBlockHeight, int genesisBlockHeight, String genesisTxId) {
        blockchainService.requestChainHeadHeight(chainHeadHeight -> {
            if (chainHeadHeight != startBlockHeight) {
                blockchainService.parseBlocks(startBlockHeight,
                        chainHeadHeight,
                        genesisBlockHeight,
                        genesisTxId,
                        txOutputMap,
                        () -> {
                            // we are done but it might be that new blocks have arrived in the meantime,
                            // so we try again with startBlockHeight set to current chainHeadHeight
                            parseBlocks(chainHeadHeight,
                                    genesisBlockHeight,
                                    genesisTxId);
                        }, throwable -> {
                            log.error(throwable.toString());
                            throwable.printStackTrace();
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
                            () -> {
                                log.debug("new block parsed. bsqBlock={}", bsqBlock);
                            }, throwable -> {
                                log.error(throwable.toString());
                                throwable.printStackTrace();
                            });
                });
            }
        }, throwable -> {
            log.error(throwable.toString());
            throwable.printStackTrace();
        });
    }

    private void onBsqTxoChanged() {
        txOutputMapListeners.stream().forEach(e -> e.onMapChanged(txOutputMap));
        jsonExporter.export(txOutputMap);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    private String getGenesisTxId() {
        return bitcoinNetwork == BitcoinNetwork.REGTEST ? REG_TEST_GENESIS_TX_ID : GENESIS_TX_ID;
    }

    private int getGenesisBlockHeight() {
        return bitcoinNetwork == BitcoinNetwork.REGTEST ? REG_TEST_GENESIS_BLOCK_HEIGHT : GENESIS_BLOCK_HEIGHT;
    }
}
