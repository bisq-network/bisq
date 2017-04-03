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
import io.bisq.core.btc.wallet.WalletUtils;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Getter
    private final BsqUTXOMap bsqUTXOMap = new BsqUTXOMap();
    @Getter
    private final BsqTXOMap bsqTXOMap = new BsqTXOMap();
    @Getter
    private int chainHeadHeight;
    @Getter
    private boolean isUtxoAvailable;

    private final BsqBlockchainService blockchainService;
    private final List<BsqUTXOListener> bsqUTXOListeners = new ArrayList<>();


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

    private void setupComplete() {
        ListenableFuture<Tuple2<BsqUTXOMap, Integer>> future =
                blockchainService.syncFromGenesis(bsqUTXOMap, bsqTXOMap, getGenesisBlockHeight(), getGenesisTxId());
        Futures.addCallback(future, new FutureCallback<Tuple2<BsqUTXOMap, Integer>>() {
            @Override
            public void onSuccess(Tuple2<BsqUTXOMap, Integer> tuple) {
                UserThread.execute(() -> {
                    chainHeadHeight = tuple.second;
                    isUtxoAvailable = true;
                    bsqUTXOListeners.stream().forEach(e -> e.onBsqUTXOChanged(bsqUTXOMap));
                    blockchainService.syncFromGenesisCompete(bsqUTXOMap, bsqTXOMap, getGenesisTxId(),
                            getGenesisBlockHeight(),
                            btcdBlock -> {
                                if (btcdBlock != null) {
                                    UserThread.execute(() -> {
                                        try {
                                            final BsqBlock bsqBlock = new BsqBlock(btcdBlock.getTx(), btcdBlock.getHeight());
                                            blockchainService.parseBlock(bsqBlock,
                                                    getGenesisBlockHeight(),
                                                    getGenesisTxId(),
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
