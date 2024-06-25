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

package bisq.inventory;


import bisq.core.locale.Res;

import bisq.common.UserThread;
import bisq.common.app.AsciiLogo;
import bisq.common.app.Log;
import bisq.common.app.Version;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.util.SingleThreadExecutorUtils;
import bisq.common.util.Utilities;

import java.nio.file.Paths;

import java.io.File;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.app.BisqExecutable.EXIT_SUCCESS;



import sun.misc.Signal;

@Slf4j
public class InventoryMonitorMain {

    private static InventoryMonitor inventoryMonitor;
    private static boolean stopped;

    // Example prog args:
    // 8080 0 10 5 1 BTC_REGTEST
    public static void main(String[] args) {
        // Default values
        int port = 80;
        boolean cleanupTorFiles = false;
        int intervalSec = 120;
        int shutdownIntervalDays = 5;
        boolean useLocalhostForP2P = false;
        BaseCurrencyNetwork network = BaseCurrencyNetwork.BTC_MAINNET;

       /* port = 8080;
        useLocalhostForP2P = true;
        network = BaseCurrencyNetwork.BTC_REGTEST;*/

        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            cleanupTorFiles = args[1].equals("1");
        }
        if (args.length > 2) {
            intervalSec = Integer.parseInt(args[2]);
        }
        if (args.length > 3) {
            shutdownIntervalDays = Integer.parseInt(args[3]);
        }
        if (args.length > 4) {
            useLocalhostForP2P = args[4].equals("1");
        }
        if (args.length > 5) {
            network = BaseCurrencyNetwork.valueOf(args[5]);
        }

        String appName = "bisq-inventory-monitor-" + network;
        File appDir = new File(Utilities.getUserDataDir(), appName);
        if (!appDir.exists() && !appDir.mkdir()) {
            log.warn("make appDir failed");
        }

        setup(network, appDir);

        inventoryMonitor = new InventoryMonitor(appDir, useLocalhostForP2P, network, intervalSec, cleanupTorFiles);
        inventoryMonitor.start(port);
        // We shut down after 5 days to avoid potential memory leak issue.
        // The start script will restart the app.
        if (shutdownIntervalDays > 0) {
            UserThread.runAfter(InventoryMonitorMain::shutDown, TimeUnit.DAYS.toSeconds(shutdownIntervalDays));
        }

        keepRunning();
    }

    private static void setup(BaseCurrencyNetwork network, File appDir) {
        String logPath = Paths.get(appDir.getPath(), "bisq").toString();
        Log.setup(logPath);
        Log.setLevel(Level.INFO);
        AsciiLogo.showAsciiLogo();
        Version.setBaseCryptoNetworkId(network.ordinal());

        Res.setup(); // Used for some formatting in the webserver

        // We do not set any capabilities as we don't want to receive any network data beside our response.
        // We also do not use capabilities for the request/response messages as we only connect to seeds nodes
        ExecutorService executorService = SingleThreadExecutorUtils.getSingleThreadExecutor(InventoryMonitorMain.class);
        UserThread.setExecutor(executorService);

        Signal.handle(new Signal("INT"), signal -> UserThread.execute(InventoryMonitorMain::shutDown));
        Signal.handle(new Signal("TERM"), signal -> UserThread.execute(InventoryMonitorMain::shutDown));
    }

    private static void shutDown() {
        stopped = true;
        inventoryMonitor.shutDown(() -> System.exit(EXIT_SUCCESS));
    }

    private static void keepRunning() {
        while (!stopped) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }
}
