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

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import io.bisq.common.UserThread;
import io.bisq.common.handlers.ErrorMessageHandler;
import io.bisq.common.storage.Storage;
import io.bisq.core.btc.wallet.WalletUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BsqBlockchainManager {
    private static final Logger log = LoggerFactory.getLogger(BsqBlockchainManager.class);

    //mainnet
    private static final String GENESIS_TX_ID = "cabbf6073aea8f22ec678e973ac30c6d8fc89321011da6a017f63e67b9f66667";
    private static final int GENESIS_BLOCK_HEIGHT = 105301;
    private static final String REG_TEST_GENESIS_TX_ID = "3bc7bc9484e112ec8ddd1a1c984379819245ac463b9ce40fa8b5bf771c0f9236";
    private static final int REG_TEST_GENESIS_BLOCK_HEIGHT = 102;

    // Modulo of blocks for making snapshots of UTXO. 
    // We stay also the value behind for safety against reorgs.
    // E.g. for SNAPSHOT_TRIGGER = 30:
    // If we are block 119 and last snapshot was 60 then we get a new trigger for a snapshot at block 120 and
    // new snapshot is block 90. We only persist at the new snapshot, so we always re-parse from latest snapshot after 
    // a restart.
    private static final int SNAPSHOT_TRIGGER = 30;

    public static int getSnapshotTrigger() {
        return SNAPSHOT_TRIGGER;
    }

    @Getter
    private final BsqUTXOMap bsqUTXOMap;
    @Getter
    private final BsqTXOMap bsqTXOMap;
    @Getter
    private int chainHeadHeight;
    @Getter
    private boolean isUtxoSyncWithChainHeadHeight;

    private final BsqBlockchainService blockchainService;
    private final List<BsqUTXOListener> bsqUTXOListeners = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqBlockchainManager(BsqBlockchainService blockchainService,
                                @Named(Storage.DIR_KEY) File storageDir) {
        this.blockchainService = blockchainService;

        bsqUTXOMap = new BsqUTXOMap(storageDir);
        bsqTXOMap = new BsqTXOMap(storageDir);

        bsqUTXOMap.addListener(c -> bsqUTXOListeners.stream().forEach(e -> e.onBsqUTXOChanged(bsqUTXOMap)));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        blockchainService.setup(this::blockchainServiceSetupCompleted, errorMessageHandler);
    }

    public Set<String> getUtxoTxIdSet() {
        return bsqUTXOMap.getTxIdSet();
    }

    public Set<String> getTxoTxIdSet() {
        return bsqTXOMap.getTxIdSet();
    }

    public void addUtxoListener(BsqUTXOListener bsqUTXOListener) {
        bsqUTXOListeners.add(bsqUTXOListener);
    }

    public void removeUtxoListener(BsqUTXOListener bsqUTXOListener) {
        bsqUTXOListeners.remove(bsqUTXOListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void blockchainServiceSetupCompleted() {
        final int genesisBlockHeight = getGenesisBlockHeight();
        final String genesisTxId = getGenesisTxId();
        int startBlockHeight = Math.max(genesisBlockHeight, bsqUTXOMap.getSnapshotHeight());
        log.info("genesisBlockHeight=" + genesisBlockHeight);
        log.info("startBlockHeight=" + startBlockHeight);
        log.info("bsqUTXOMap.getSnapshotHeight()=" + bsqUTXOMap.getSnapshotHeight());

        if (bsqUTXOMap.getSnapshotHeight() > 0) {
            bsqUTXOListeners.stream().forEach(e -> e.onBsqUTXOChanged(bsqUTXOMap));
        }
        ListenableFuture<Integer> future =
                blockchainService.executeParseBlockchain(bsqUTXOMap,
                        bsqTXOMap,
                        startBlockHeight,
                        genesisBlockHeight,
                        genesisTxId);

        Futures.addCallback(future, new FutureCallback<Integer>() {
            @Override
            public void onSuccess(Integer height) {
                UserThread.execute(() -> {
                    chainHeadHeight = height;
                    isUtxoSyncWithChainHeadHeight = true;
                    blockchainService.parseBlockchainCompete(btcdBlock -> {
                        if (btcdBlock != null) {
                            UserThread.execute(() -> {
                                try {
                                    final BsqBlock bsqBlock = new BsqBlock(btcdBlock.getTx(), btcdBlock.getHeight());
                                    blockchainService.parseBlock(bsqBlock,
                                            genesisBlockHeight,
                                            genesisTxId,
                                            bsqUTXOMap,
                                            bsqTXOMap);
                                } catch (BsqBlockchainException e) {
                                    //TODO
                                    e.printStackTrace();
                                }
                            });
                        }
                    });
                });
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> log.error("syncFromGenesis failed" + throwable.toString()));
            }
        });
    }

    private String getGenesisTxId() {
        return WalletUtils.isRegTest() ? REG_TEST_GENESIS_TX_ID : GENESIS_TX_ID;
    }

    private int getGenesisBlockHeight() {
        return WalletUtils.isRegTest() ? REG_TEST_GENESIS_BLOCK_HEIGHT : GENESIS_BLOCK_HEIGHT;
    }
}
