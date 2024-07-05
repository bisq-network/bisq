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

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BtcNodeMonitorMain implements GracefulShutDownHandler {
    public static void main(String[] args) {
        new BtcNodeMonitorMain(args);
    }

    private final Config config;
    @Getter
    private final BtcNodeMonitor btcNodeMonitor;

    public BtcNodeMonitorMain(String[] args) {
        config = new Config("bisq_btc_node_monitor", Utilities.getUserDataDir(), args);
        CommonSetup.setup(config, this);
        configUserThread();

        btcNodeMonitor = new BtcNodeMonitor(config);
        btcNodeMonitor.start().join();
        keepRunning();
    }

    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        log.info("gracefulShutDown");
        btcNodeMonitor.shutdown().join();
        System.exit(0);
        resultHandler.handleResult();
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
