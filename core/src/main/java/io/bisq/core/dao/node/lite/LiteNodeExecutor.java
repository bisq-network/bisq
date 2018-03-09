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

package io.bisq.core.dao.node.lite;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import io.bisq.common.UserThread;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.util.Utilities;
import io.bisq.core.dao.blockchain.BsqBlockChain;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Consumer;

/**
 * Processes tasks in custom threads. Results are mapped back to user thread so client don't need to deal with threading.
 */
@Slf4j
public class LiteNodeExecutor {

    private final LiteNodeParser liteNodeParser;
    private final BsqBlockChain bsqBlockChain;

    private final ListeningExecutorService parseBlocksExecutor = Utilities.getListeningExecutorService("ParseBlocks", 1, 1, 60);
    private final ListeningExecutorService parseBlockExecutor = Utilities.getListeningExecutorService("ParseBlock", 1, 1, 60);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public LiteNodeExecutor(LiteNodeParser liteNodeParser, BsqBlockChain bsqBlockChain) {
        this.liteNodeParser = liteNodeParser;
        this.bsqBlockChain = bsqBlockChain;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package private
    ///////////////////////////////////////////////////////////////////////////////////////////

    void parseBlocks(List<BsqBlock> bsqBlockList,
                     Consumer<BsqBlock> newBlockHandler,
                     ResultHandler resultHandler,
                     Consumer<Throwable> errorHandler) {
        ListenableFuture<Void> future = parseBlocksExecutor.submit(() -> {
            long startTs = System.currentTimeMillis();
            liteNodeParser.parseBsqBlocks(bsqBlockList,
                    newBsqBlock -> UserThread.execute(() -> newBlockHandler.accept(newBsqBlock)));
            log.info("parseBlocks took {} ms for {} blocks", System.currentTimeMillis() - startTs, bsqBlockList.size());
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

    void parseBlock(BsqBlock bsqBlock,
                    ResultHandler resultHandler,
                    Consumer<Throwable> errorHandler) {
        ListenableFuture<Void> future = parseBlockExecutor.submit(() -> {
            long startTs = System.currentTimeMillis();
            liteNodeParser.parseBsqBlock(bsqBlock);
            log.info("parseBlocks took {} ms", System.currentTimeMillis() - startTs);
            bsqBlockChain.addBlock(bsqBlock);
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
}
