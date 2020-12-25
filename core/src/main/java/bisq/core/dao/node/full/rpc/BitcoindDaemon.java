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

package bisq.core.dao.node.full.rpc;

import bisq.common.util.Utilities;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.apache.commons.io.IOUtils;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketException;

import java.nio.charset.StandardCharsets;

import java.io.IOException;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BitcoindDaemon {
    private final ListeningExecutorService executor = Utilities.getSingleThreadListeningExecutor("block-notification-server");
    private final ListeningExecutorService workerPool = Utilities.getListeningExecutorService("block-notification-worker-%d",
            1, 10, 60, new ArrayBlockingQueue<>(100));
    private final ServerSocket serverSocket;
    private final Consumer<Throwable> errorHandler;
    private volatile boolean active;
    private volatile BlockListener blockListener = blockHash -> {
    };

    public BitcoindDaemon(String host, int port, Consumer<Throwable> errorHandler) throws NotificationHandlerException {
        this(newServerSocket(host, port), errorHandler);
    }

    @VisibleForTesting
    BitcoindDaemon(ServerSocket serverSocket, Consumer<Throwable> errorHandler) {
        this.serverSocket = serverSocket;
        this.errorHandler = errorHandler;
        initialize();
    }

    private static ServerSocket newServerSocket(String host, int port) throws NotificationHandlerException {
        try {
            return new ServerSocket(port, 5, InetAddress.getByName(host));
        } catch (Exception e) {
            throw new NotificationHandlerException(e);
        }
    }

    private void initialize() {
        active = true;
        var serverFuture = executor.submit((Callable<Void>) () -> {
            try {
                while (active) {
                    try (var socket = serverSocket.accept(); var is = socket.getInputStream()) {
                        var blockHash = IOUtils.toString(is, StandardCharsets.UTF_8).trim();
                        var future = workerPool.submit((Callable<Void>) () -> {
                            try {
                                blockListener.blockDetected(blockHash);
                                return null;
                            } catch (RuntimeException e) {
                                throw new NotificationHandlerException(e);
                            }
                        });
                        Futures.addCallback(future, Utilities.failureCallback(errorHandler), MoreExecutors.directExecutor());
                    }
                }
            } catch (SocketException e) {
                if (active) {
                    throw new NotificationHandlerException(e);
                }
            } catch (Exception e) {
                throw new NotificationHandlerException(e);
            } finally {
                log.info("Shutting down block notification server");
            }
            return null;
        });
        Futures.addCallback(serverFuture, Utilities.failureCallback(errorHandler), MoreExecutors.directExecutor());
    }

    public void shutdown() {
        active = false;
        try {
            serverSocket.close();
        } catch (IOException e) {
            log.error("Error closing block notification server socket", e);
        } finally {
            Utilities.shutdownAndAwaitTermination(executor, 1, TimeUnit.SECONDS);
            Utilities.shutdownAndAwaitTermination(workerPool, 5, TimeUnit.SECONDS);
        }
    }

    public void setBlockListener(BlockListener blockListener) {
        this.blockListener = blockListener;
    }

    public interface BlockListener {
        void blockDetected(String blockHash);
    }
}
