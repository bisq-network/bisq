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
import bisq.common.util.Utilities;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.nio.file.Paths;

import java.io.File;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.Level;

import lombok.extern.slf4j.Slf4j;



import sun.misc.Signal;

@Slf4j
public class InventoryMonitorMain {

    private static InventoryMonitor inventoryMonitor;
    private static boolean stopped;

    // prog args for regtest: 10 1 BTC_REGTEST
    public static void main(String[] args) {
        // Default values
        int intervalSec = 120;
        boolean useLocalhostForP2P = false;
        BaseCurrencyNetwork network = BaseCurrencyNetwork.BTC_MAINNET;
        int port = 80;

        if (args.length > 0) {
            intervalSec = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            useLocalhostForP2P = args[1].equals("1");
        }
        if (args.length > 2) {
            network = BaseCurrencyNetwork.valueOf(args[2]);
        }
        if (args.length > 3) {
            port = Integer.parseInt(args[3]);
        }

        String appName = "bisq-inventory-monitor-" + network;
        File appDir = new File(Utilities.getUserDataDir(), appName);
        if (!appDir.exists() && !appDir.mkdir()) {
            log.warn("make appDir failed");
        }
        inventoryMonitor = new InventoryMonitor(appDir, useLocalhostForP2P, network, intervalSec, port);

        setup(network, appDir);

        // We shutdown after 5 days to avoid potential memory leak issue.
        // The start script will restart the app.
        UserThread.runAfter(InventoryMonitorMain::shutDown, TimeUnit.DAYS.toSeconds(5));
    }

    private static void setup(BaseCurrencyNetwork network, File appDir) {
        String logPath = Paths.get(appDir.getPath(), "bisq").toString();
        Log.setup(logPath);
        Log.setLevel(Level.INFO);
        AsciiLogo.showAsciiLogo();
        Version.setBaseCryptoNetworkId(network.ordinal());

        Res.setup(); // Used for some formatting in the webserver

        // We do not set any capabilities as we don't want to receive any network data beside our response.
        // We also do not use capabilities for the request/response messages as we only connect to seeds nodes and

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(inventoryMonitor.getClass().getSimpleName())
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));

        Signal.handle(new Signal("INT"), signal -> {
            UserThread.execute(InventoryMonitorMain::shutDown);
        });

        Signal.handle(new Signal("TERM"), signal -> {
            UserThread.execute(InventoryMonitorMain::shutDown);
        });
        keepRunning();
    }

    private static void shutDown() {
        stopped = true;
        inventoryMonitor.shutDown(() -> {
            System.exit(0);
        });
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
