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

package bisq.btcnodemonitor;


import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.handlers.ResultHandler;
import bisq.common.setup.CommonSetup;
import bisq.common.setup.GracefulShutDownHandler;
import bisq.common.util.SingleThreadExecutorUtils;
import bisq.common.util.Utilities;

import com.google.common.annotations.VisibleForTesting;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BtcNodeMonitorMain implements GracefulShutDownHandler {
    public static void main(String[] args) {
        new BtcNodeMonitorMain(args);
    }

    @Getter
    private final BtcNodeMonitor btcNodeMonitor;
    private CompletableFuture<Void> shutDownFuture;

    public BtcNodeMonitorMain(String[] args) {
        Config config = new Config("bisq_btc_node_monitor", Utilities.getUserDataDir(), args);
        CommonSetup.setup(config, this);
        configUserThread();

        btcNodeMonitor = new BtcNodeMonitor(config);
        btcNodeMonitor.start().join();
        keepRunning();
    }

    @VisibleForTesting
    BtcNodeMonitorMain(BtcNodeMonitor btcNodeMonitor) {
        this.btcNodeMonitor = btcNodeMonitor;
    }

    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        log.info("gracefulShutDown");
        try {
            getShutDownFuture().join();
        } catch (Throwable t) {
            // The service shutdown completes with an error in normal situations, for instance if the Tor
            // shutdown runs into its 2 second timeout. We must not let that skip the completion handler,
            // otherwise the JVM shutdown hook waits for its full timeout of 2 minutes.
            log.error("Shutdown of services failed. We continue with the exit.", t);
        }
        try {
            resultHandler.handleResult();
        } finally {
            exitAfterShutDown();
        }
    }

    private synchronized CompletableFuture<Void> getShutDownFuture() {
        if (shutDownFuture == null) {
            // A termination signal can arrive after CommonSetup.setup published this instance to the JVM
            // shutdown hook but before the constructor assigned btcNodeMonitor.
            shutDownFuture = btcNodeMonitor != null
                    ? btcNodeMonitor.shutdown()
                    : CompletableFuture.completedFuture(null);
        }
        return shutDownFuture;
    }

    @VisibleForTesting
    void exitAfterShutDown() {
        CommonSetup.exitAfter(0, 0, TimeUnit.MILLISECONDS);
    }

    private void keepRunning() {
        try {
            Thread.currentThread().setName("BtcNodeMonitorMain");
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.error("BtcNodeMonitorMain Thread interrupted", e);
            gracefulShutDown(() -> {
            });
        }
    }

    private void configUserThread() {
        UserThread.setExecutor(SingleThreadExecutorUtils.getSingleThreadExecutor("UserThread"));
    }
}
