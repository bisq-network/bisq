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

package bisq.daemon.app;

import bisq.core.app.BisqHeadlessAppMain;
import bisq.core.app.BisqSetup;
import bisq.core.app.CoreModule;

import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.SingleThreadExecutorUtils;

import java.util.concurrent.ExecutorService;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;



import bisq.daemon.grpc.GrpcServer;

@Slf4j
public class BisqDaemonMain extends BisqHeadlessAppMain implements BisqSetup.BisqSetupListener {

    private GrpcServer grpcServer;

    public static void main(String[] args) {
        // Check for existing instance
        if (!checkForExistingInstance()) {
            System.exit(1);
        }
        
        new BisqDaemonMain().execute(args);
    }

   private static boolean checkForExistingInstance() {
    try {
        String appDataDir = System.getProperty("user.dir");
        String lockFileName = "bisq-daemon.lock";
        File lockFile = new File(appDataDir, lockFileName);

        // If lock file exists, check if that PID is still running
        if (lockFile.exists()) {
            String pidString = Files.readString(Paths.get(lockFile.getPath()));
            long pid = -1; // default invalid PID
            try {
                pid = Long.parseLong(pidString);
                boolean isRunning = ProcessHandle.of(pid)
                        .map(ProcessHandle::isAlive)
                        .orElse(false);

                if (isRunning) {
                    log.warn("Another instance of Bisq daemon is already running with PID: " + pid);
                    return false;
                } else {
                    // Process not running, remove stale lock file
                    log.info("Stale lock file found with PID: " + pid + ", removing it");
                    lockFile.delete();
                }
            } catch (NumberFormatException e) {
                log.info("Invalid PID in lock file, removing stale lock file");
                lockFile.delete();
            } catch (Exception e) {
                log.info("Could not verify process with PID: " + pid + ", removing stale lock file");
                lockFile.delete();
            }
        }

        // Create new lock file with current PID
        long currentPid = ProcessHandle.current().pid();
        try (FileWriter writer = new FileWriter(lockFile)) {
            writer.write(String.valueOf(currentPid));
        }
        log.info("Created lock file with PID: " + currentPid);

        // Ensure lock file is deleted on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (lockFile.exists()) {
                lockFile.delete();
                log.info("Removed lock file");
            }
        }));

        return true;
    } catch (IOException e) {
        log.error("Failed to check for existing instance", e);
        return false;
    }
}


    /////////////////////////////////////////////////////////////////////////////////////
    // First synchronous execution tasks
    /////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void configUserThread() {
        ExecutorService executorService = SingleThreadExecutorUtils.getSingleThreadExecutor(this.getClass());
        UserThread.setExecutor(executorService);
    }

    @Override
    protected void launchApplication() {
        headlessApp = new BisqDaemon();

        UserThread.execute(this::onApplicationLaunched);
    }

    @Override
    protected void onApplicationLaunched() {
        super.onApplicationLaunched();
        headlessApp.setGracefulShutDownHandler(this);
    }


    /////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    /////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected AppModule getModule() {
        return new CoreModule(config);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        headlessApp.setInjector(injector);
    }

    @Override
    protected void startApplication() {
        super.startApplication();
    }

    @Override
    protected void onApplicationStarted() {
        super.onApplicationStarted();

        grpcServer = injector.getInstance(GrpcServer.class);
        grpcServer.start();
    }

    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        super.gracefulShutDown(resultHandler);

        grpcServer.shutdown();
    }
}
