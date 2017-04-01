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
import io.bisq.common.util.Tuple2;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class BsqBlockchainManager {
    private static final Logger log = LoggerFactory.getLogger(BsqBlockchainManager.class);

    private final BsqBlockchainService blockchainService;

    // regtest
    public static final String GENESIS_TX_ID = "c0ddf75202579fd43e0d5a41ac5bae05a6e8295633695af6c350b9100878c5e7";
    public static final int GENESIS_BLOCK_HEIGHT = 102;

    protected BsqUTXOMap utxoByTxIdMap;
    private final List<BsqUTXOListener> bsqUTXOListeners = new ArrayList<>();
    private boolean isUtxoAvailable;
    protected int chainHeadHeight;


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
    // Protected methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void setupComplete() {
        ListenableFuture<Tuple2<BsqUTXOMap, Integer>> future =
                blockchainService.syncFromGenesis(GENESIS_BLOCK_HEIGHT, GENESIS_TX_ID);
        Futures.addCallback(future, new FutureCallback<Tuple2<BsqUTXOMap, Integer>>() {
            @Override
            public void onSuccess(Tuple2<BsqUTXOMap, Integer> tuple) {
                UserThread.execute(() -> {
                    BsqBlockchainManager.this.utxoByTxIdMap = tuple.first;
                    chainHeadHeight = tuple.second;
                    isUtxoAvailable = true;
                    bsqUTXOListeners.stream().forEach(e -> e.onBsqUTXOChanged(utxoByTxIdMap));
                    blockchainService.syncFromGenesisCompete(GENESIS_TX_ID,
                            GENESIS_BLOCK_HEIGHT,
                            btcdBlock -> {
                                if (btcdBlock != null) {
                                    UserThread.execute(() -> {
                                        try {
                                            blockchainService.parseBlock(new BsqBlock(btcdBlock.getTx(), btcdBlock.getHeight()),
                                                    GENESIS_BLOCK_HEIGHT,
                                                    GENESIS_TX_ID,
                                                    utxoByTxIdMap);
                                        } catch (BsqBlockchainException e) {
                                            //TODO
                                            e.printStackTrace();
                                        }
                                        blockchainService.printUtxoMap(utxoByTxIdMap);
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public int getChainHeadHeight() {
        return chainHeadHeight;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}
