package bisq.core.dao.node.full.rpc;

import bisq.common.util.Utilities;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
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

import org.jetbrains.annotations.NotNull;

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

    public BitcoindDaemon(String host, int port, Consumer<Throwable> errorHandler) throws IOException {
        this(new ServerSocket(port, 5, InetAddress.getByName(host)), errorHandler);
    }

    @VisibleForTesting
    BitcoindDaemon(ServerSocket serverSocket, Consumer<Throwable> errorHandler) {
        this.serverSocket = serverSocket;
        this.errorHandler = errorHandler;
        initialize();
    }

    private void initialize() {
        active = true;
        var serverFuture = executor.submit((Callable<Void>) () -> {
            while (active) {
                try (var socket = serverSocket.accept(); var is = socket.getInputStream()) {
                    var blockHash = IOUtils.toString(is, StandardCharsets.UTF_8).trim();
                    var future = workerPool.submit(() -> blockListener.blockDetected(blockHash));

                    Futures.addCallback(future, failureCallback(errorHandler), MoreExecutors.directExecutor());
                } catch (SocketException e) {
                    if (active) {
                        throw e;
                    }
                    log.info("Shutting down block notification server");
                }
            }
            return null;
        });

        Futures.addCallback(serverFuture, failureCallback(errorHandler), MoreExecutors.directExecutor());
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

    private static <V> FutureCallback<V> failureCallback(Consumer<Throwable> errorHandler) {
        return new FutureCallback<>() {
            @Override
            public void onSuccess(V result) {
            }

            @Override
            public void onFailure(@NotNull Throwable t) {
                errorHandler.accept(t);
            }
        };
    }

    public interface BlockListener {
        void blockDetected(String blockHash);
    }
}
