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


import bisq.common.UserThread;
import bisq.common.app.Log;
import bisq.common.app.Version;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.util.Utilities;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.nio.file.Paths;

import java.io.File;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import ch.qos.logback.classic.Level;

import lombok.extern.slf4j.Slf4j;



import sun.misc.Signal;

@Slf4j
public class InventoryMonitorMain {

    private static InventoryMonitor inventoryMonitor;
    private static boolean stopped;

    // prog args for regtest: 10 1 BTC_REGTEST
    public static void main(String[] args) {
        int intervalSec = 600;
        boolean useLocalhostForP2P = false;
        BaseCurrencyNetwork network = BaseCurrencyNetwork.BTC_MAINNET;

        if (args.length > 0) {
            intervalSec = Integer.parseInt(args[0]);
        }
        if (args.length > 1) {
            useLocalhostForP2P = args[1].equals("1");
        }
        if (args.length > 2) {
            network = BaseCurrencyNetwork.valueOf(args[2]);
        }

        String appName = "bisq-InventoryMonitor-" + network;

        File appDir = new File(Utilities.getUserDataDir(), appName);
        String logPath = Paths.get(appDir.getPath(), "bisq").toString();
        Log.setup(logPath);
        Log.setLevel(Level.INFO);
        Version.setBaseCryptoNetworkId(network.ordinal());

        inventoryMonitor = new InventoryMonitor(appDir, useLocalhostForP2P, network, intervalSec);

        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(inventoryMonitor.getClass().getSimpleName())
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));

        Signal.handle(new Signal("INT"), signal -> {
            shutDown();
        });

        Signal.handle(new Signal("TERM"), signal -> {
            shutDown();
        });
        keepRunning();
    }

    private static void shutDown() {
        inventoryMonitor.shutDown();
        stopped = true;
        System.exit(0);
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
