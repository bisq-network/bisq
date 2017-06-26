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

package io.bisq.core.dao.blockchain.parse;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.neemre.btcdcli4j.core.domain.Block;
import io.bisq.common.UserThread;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.util.Utilities;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.function.Consumer;

// Used for non blocking access to BTC blockchain data and parsing. Encapsulate thread context, so caller 
// gets always called on UserThread
@Slf4j
public class BsqFullNodeExecutor {

    private final BsqParser bsqParser;
    private final RpcService rpcService;

    private final ListeningExecutorService parseBlocksExecutor = Utilities.getListeningExecutorService("ParseBlocks", 1, 1, 60);
    private final ListeningExecutorService getChainHeightExecutor = Utilities.getListeningExecutorService("GetChainHeight", 1, 1, 60);
    private final ListeningExecutorService setupExecutor = Utilities.getListeningExecutorService("RpcServiceSetup", 1, 1, 5);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqFullNodeExecutor(RpcService rpcService, BsqParser bsqParser) {
        this.rpcService = rpcService;
        this.bsqParser = bsqParser;
    }

    public void setup(ResultHandler resultHandler, Consumer<Throwable> errorHandler) {
        ListenableFuture<Void> future = setupExecutor.submit(() -> {
            rpcService.setup();
            return null;
        });

        Futures.addCallback(future, new FutureCallback<Void>() {
            public void onSuccess(Void ignore) {
                UserThread.execute(resultHandler::handleResult);
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    public void requestChainHeadHeight(Consumer<Integer> resultHandler, Consumer<Throwable> errorHandler) {
        ListenableFuture<Integer> future = getChainHeightExecutor.submit(rpcService::requestChainHeadHeight);

        Futures.addCallback(future, new FutureCallback<Integer>() {
            public void onSuccess(Integer chainHeadHeight) {
                resultHandler.accept(chainHeadHeight);
            }

            public void onFailure(@NotNull Throwable throwable) {
                errorHandler.accept(throwable);
            }
        });
    }

    public void parseBlocks(int startBlockHeight,
                            int chainHeadHeight,
                            int genesisBlockHeight,
                            String genesisTxId,
                            Consumer<BsqBlock> newBlockHandler,
                            ResultHandler resultHandler,
                            Consumer<Throwable> errorHandler) {
        ListenableFuture<Void> future = parseBlocksExecutor.submit(() -> {
            long startTs = System.currentTimeMillis();
            bsqParser.parseBlocks(startBlockHeight,
                    chainHeadHeight,
                    genesisBlockHeight,
                    genesisTxId,
                    newBsqBlock -> UserThread.execute(() -> newBlockHandler.accept(newBsqBlock)));
            log.info("parseBlocks took {} ms for {} blocks", System.currentTimeMillis() - startTs, chainHeadHeight - startBlockHeight);
            return null;
        });

        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void ignore) {
                UserThread.execute(() -> UserThread.execute(resultHandler::handleResult));
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    public void parseBtcdBlock(Block btcdBlock,
                               int genesisBlockHeight,
                               String genesisTxId,
                               Consumer<BsqBlock> resultHandler,
                               Consumer<Throwable> errorHandler) {
        ListenableFuture<BsqBlock> future = parseBlocksExecutor.submit(() -> bsqParser.parseBlock(btcdBlock,
                genesisBlockHeight,
                genesisTxId));

        Futures.addCallback(future, new FutureCallback<BsqBlock>() {
            @Override
            public void onSuccess(BsqBlock bsqBlock) {
                UserThread.execute(() -> resultHandler.accept(bsqBlock));
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    public void addBlockHandler(Consumer<Block> blockHandler) {
        rpcService.registerBlockHandler(blockHandler);
    }
}
