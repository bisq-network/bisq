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
import io.bisq.common.util.Tuple3;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BsqBlockchainManager {
    private static final Logger log = LoggerFactory.getLogger(BsqBlockchainManager.class);

    private final BsqBlockchainService blockchainService;

    // regtest
    public static final String GENESIS_TX_ID = "3bc7bc9484e112ec8ddd1a1c984379819245ac463b9ce40fa8b5bf771c0f9236";
    public static final int GENESIS_BLOCK_HEIGHT = 102;

    private BsqUTXOMap utxoByTxIdMap;
    private final List<BsqUTXOListener> bsqUTXOListeners = new ArrayList<>();
    private boolean isUtxoAvailable;
    @Getter
    private int chainHeadHeight;

    // We prefer a list over a set. See: http://stackoverflow.com/questions/24799125/what-is-the-observableset-equivalent-for-setall-method-from-observablelist
    @Getter
    private final ObservableList<BsqBlock> bsqBlocks = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BsqBlockchainManager(BsqBlockchainService blockchainService) {
        this.blockchainService = blockchainService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        blockchainService.setup(this::setupComplete, errorMessageHandler);
    }

    public BsqUTXOMap getUtxoByTxIdMap() {
        return utxoByTxIdMap;
    }


    public void addUtxoListener(BsqUTXOListener bsqUTXOListener) {
        bsqUTXOListeners.add(bsqUTXOListener);
    }

    public void removeUtxoListener(BsqUTXOListener bsqUTXOListener) {
        bsqUTXOListeners.remove(bsqUTXOListener);
    }

    public boolean isUtxoAvailable() {
        return isUtxoAvailable;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupComplete() {
        ListenableFuture<Tuple3<Set<BsqBlock>, BsqUTXOMap, Integer>> future =
                blockchainService.syncFromGenesis(GENESIS_BLOCK_HEIGHT, GENESIS_TX_ID);
        Futures.addCallback(future, new FutureCallback<Tuple3<Set<BsqBlock>, BsqUTXOMap, Integer>>() {
            @Override
            public void onSuccess(Tuple3<Set<BsqBlock>, BsqUTXOMap, Integer> tuple) {
                UserThread.execute(() -> {
                    BsqBlockchainManager.this.bsqBlocks.setAll(tuple.first);
                    BsqBlockchainManager.this.utxoByTxIdMap = tuple.second;
                    chainHeadHeight = tuple.third;
                    isUtxoAvailable = true;
                    bsqUTXOListeners.stream().forEach(e -> e.onBsqUTXOChanged(utxoByTxIdMap));
                    blockchainService.syncFromGenesisCompete(GENESIS_TX_ID,
                            GENESIS_BLOCK_HEIGHT,
                            btcdBlock -> {
                                if (btcdBlock != null) {
                                    UserThread.execute(() -> {
                                        try {
                                            final BsqBlock bsqBlock = new BsqBlock(btcdBlock.getTx(), btcdBlock.getHeight());
                                            blockchainService.parseBlock(bsqBlock,
                                                    GENESIS_BLOCK_HEIGHT,
                                                    GENESIS_TX_ID,
                                                    utxoByTxIdMap);
                                            if (!BsqBlockchainManager.this.bsqBlocks.contains(bsqBlock))
                                                BsqBlockchainManager.this.bsqBlocks.add(bsqBlock);
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
}
