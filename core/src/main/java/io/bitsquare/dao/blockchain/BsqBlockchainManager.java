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

package io.bitsquare.dao.blockchain;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.inject.Inject;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Tuple2;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class BsqBlockchainManager {
    private static final Logger log = LoggerFactory.getLogger(BsqBlockchainManager.class);

    private final BsqBlockchainService blockchainService;

    // regtest
    public static final String GENESIS_TX_ID = "c01129ff48082f8f9613dd505899359227cb71aa457903359cfd0ca9c152dcd6";
    public static final int GENESIS_BLOCK_HEIGHT = 103;

    protected Map<String, Map<Integer, BsqUTXO>> utxoByTxIdMap;
    private List<UtxoListener> utxoListeners = new ArrayList<>();
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

    public void onAllServicesInitialized() {
        blockchainService.setup(this::setupComplete, errorMessage -> {
            log.error("setup failed" + errorMessage);
        });
    }

    public Map<String, Map<Integer, BsqUTXO>> getUtxoByTxIdMap() {
        return utxoByTxIdMap;
    }


    public void addUtxoListener(UtxoListener utxoListener) {
        utxoListeners.add(utxoListener);
    }

    public void removeUtxoListener(UtxoListener utxoListener) {
        utxoListeners.remove(utxoListener);
    }

    public boolean isUtxoAvailable() {
        return isUtxoAvailable;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void setupComplete() {
        ListenableFuture<Tuple2<Map<String, Map<Integer, BsqUTXO>>, Integer>> future = blockchainService.syncFromGenesis(GENESIS_BLOCK_HEIGHT, GENESIS_TX_ID);
        Futures.addCallback(future, new FutureCallback<Tuple2<Map<String, Map<Integer, BsqUTXO>>, Integer>>() {
            @Override
            public void onSuccess(Tuple2<Map<String, Map<Integer, BsqUTXO>>, Integer> tulpe) {
                UserThread.execute(() -> {
                    BsqBlockchainManager.this.utxoByTxIdMap = tulpe.first;
                    chainHeadHeight = tulpe.second;
                    isUtxoAvailable = true;
                    utxoListeners.stream().forEach(e -> e.onUtxoChanged(utxoByTxIdMap));
                    blockchainService.syncFromGenesisCompete(GENESIS_TX_ID,
                            GENESIS_BLOCK_HEIGHT,
                            block -> {
                                if (block != null) {
                                    UserThread.execute(() -> {
                                        try {
                                            blockchainService.parseBlock(new BsqBlock(block.getTx(), block.getHeight()),
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
            public void onFailure(Throwable throwable) {
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
