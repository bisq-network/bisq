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

package io.bisq.core.dao.node.full;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.neemre.btcdcli4j.core.domain.Block;
import io.bisq.common.UserThread;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.util.Utilities;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.core.dao.node.full.rpc.RpcService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.function.Consumer;

/**
 * Processes tasks in custom thread. Results are mapped back to user thread so client don't need to deal with threading.
 * We use a SingleThreadExecutor to guarantee that the parser is only running from one thread at a time to avoid
 * risks with concurrent write to the BsqBlockChain.
 */
@Slf4j
public class FullNodeExecutor {

    private final FullNodeParser fullNodeParser;
    private final RpcService rpcService;
    private final ListeningExecutorService executor = Utilities.getListeningSingleThreadExecutor("FullNodeExecutor");


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public FullNodeExecutor(RpcService rpcService, FullNodeParser fullNodeParser) {
        this.rpcService = rpcService;
        this.fullNodeParser = fullNodeParser;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package private
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setup(ResultHandler resultHandler, Consumer<Throwable> errorHandler) {
        ListenableFuture<Void> future = executor.submit(() -> {
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

    void requestChainHeadHeight(Consumer<Integer> resultHandler, Consumer<Throwable> errorHandler) {
        ListenableFuture<Integer> future = executor.submit(rpcService::requestChainHeadHeight);
        Futures.addCallback(future, new FutureCallback<Integer>() {
            public void onSuccess(Integer chainHeadHeight) {
                UserThread.execute(() -> resultHandler.accept(chainHeadHeight));
            }

            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    void parseBlocks(int startBlockHeight,
                     int chainHeadHeight,
                     Consumer<BsqBlock> newBlockHandler,
                     ResultHandler resultHandler,
                     Consumer<Throwable> errorHandler) {
        ListenableFuture<Void> future = executor.submit(() -> {
            long startTs = System.currentTimeMillis();
            fullNodeParser.parseBlocks(startBlockHeight,
                    chainHeadHeight,
                    newBsqBlock -> UserThread.execute(() -> newBlockHandler.accept(newBsqBlock)));
            log.info("parseBlocks took {} ms for {} blocks", System.currentTimeMillis() - startTs, chainHeadHeight - startBlockHeight);
            return null;
        });

        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void ignore) {
                UserThread.execute(resultHandler::handleResult);
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                UserThread.execute(() -> errorHandler.accept(throwable));
            }
        });
    }

    void parseBtcdBlock(Block btcdBlock,
                        Consumer<BsqBlock> resultHandler,
                        Consumer<Throwable> errorHandler) {
        ListenableFuture<BsqBlock> future = executor.submit(() -> fullNodeParser.parseBlock(btcdBlock));

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

    void addBlockHandler(Consumer<Block> blockHandler) {
        rpcService.registerBlockHandler(blockHandler);
    }
}
