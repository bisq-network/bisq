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

package bisq.apitest;

import bisq.common.config.BisqHelpFormatter;
import bisq.common.storage.FileUtil;
import bisq.common.util.Utilities;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.apitest.config.BisqAppConfig.*;
import static java.lang.String.format;
import static java.lang.System.err;
import static java.lang.System.exit;
import static java.lang.System.out;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;



import bisq.apitest.config.ApiTestConfig;
import bisq.apitest.config.BisqAppConfig;
import bisq.apitest.linux.BashCommand;
import bisq.apitest.linux.BisqApp;
import bisq.apitest.linux.BitcoinDaemon;

@Slf4j
public class Scaffold {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_FAILURE = 1;

    public final ApiTestConfig config;

    @Nullable
    private SetupTask bitcoindTask;
    @Nullable
    private Future<SetupTask.Status> bitcoindTaskFuture;
    @Nullable
    private SetupTask seedNodeTask;
    @Nullable
    private Future<SetupTask.Status> seedNodeTaskFuture;
    @Nullable
    private SetupTask arbNodeTask;
    @Nullable
    private Future<SetupTask.Status> arbNodeTaskFuture;
    @Nullable
    private SetupTask aliceNodeTask;
    @Nullable
    private Future<SetupTask.Status> aliceNodeTaskFuture;
    @Nullable
    private SetupTask bobNodeTask;
    @Nullable
    private Future<SetupTask.Status> bobNodeTaskFuture;

    private final ExecutorService executor;

    public Scaffold(String[] args) {
        this(new ApiTestConfig(args));
    }

    public Scaffold(ApiTestConfig config) {
        verifyNotWindows();
        this.config = config;
        this.executor = Executors.newFixedThreadPool(config.numSetupTasks);
        if (config.helpRequested) {
            config.printHelp(out,
                    new BisqHelpFormatter(
                            "Bisq ApiTest",
                            "bisq-apitest",
                            "0.1.0"));
            exit(EXIT_SUCCESS);
        }
    }


    public Scaffold setUp() {
        try {
            installDaoSetupDirectories();

            // Start each background process from an executor, then add a shutdown hook.
            CountDownLatch countdownLatch = new CountDownLatch(config.numSetupTasks);
            startBackgroundProcesses(executor, countdownLatch);
            installShutdownHook();

            // Wait for all submitted startup tasks to decrement the count of the latch.
            Objects.requireNonNull(countdownLatch).await();

            // Verify each startup task's future is done.
            verifyStartupCompleted();

        } catch (Throwable ex) {
            err.println("Fault: An unexpected error occurred. " +
                    "Please file a report at https://bisq.network/issues");
            ex.printStackTrace(err);
            exit(EXIT_FAILURE);
        }
        return this;
    }

    public void tearDown() {
        if (!executor.isTerminated()) {
            try {
                log.info("Shutting down executor service ...");
                executor.shutdownNow();
                executor.awaitTermination(config.numSetupTasks * 2000, MILLISECONDS);

                SetupTask[] orderedTasks = new SetupTask[]{
                        bobNodeTask, aliceNodeTask, arbNodeTask, seedNodeTask, bitcoindTask};
                stream(orderedTasks).filter(t -> t != null && t.getLinuxProcess() != null)
                        .forEachOrdered(t -> {
                            try {
                                t.getLinuxProcess().shutdown();
                                MILLISECONDS.sleep(1000);
                            } catch (IOException | InterruptedException ex) {
                                throw new IllegalStateException(ex);
                            }
                        });

                log.info("Teardown complete");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    public void installDaoSetupDirectories() {
        cleanDaoSetupDirectories();

        String srcResourcesDir = Paths.get("apitest", "src", "main", "resources", "dao-setup").toFile().getAbsolutePath();
        String buildDataDir = config.rootAppDataDir.getAbsolutePath();
        try {
            if (!new File(srcResourcesDir).exists())
                throw new FileNotFoundException(
                        format("Dao setup dir '%s' not found.  Run gradle :apitest:installDaoSetup"
                                        + " to download dao-setup.zip and extract contents to resources folder",
                                srcResourcesDir));

            BashCommand copyBitcoinRegtestDir = new BashCommand(
                    "cp -rf " + srcResourcesDir + "/Bitcoin-regtest/regtest"
                            + " " + config.bitcoinDatadir);
            if (copyBitcoinRegtestDir.run().getExitStatus() != 0)
                throw new IllegalStateException("Could not install bitcoin regtest dir");

            BashCommand copyAliceDataDir = new BashCommand(
                    "cp -rf " + srcResourcesDir + "/" + alicedaemon.appName
                            + " " + config.rootAppDataDir);
            if (copyAliceDataDir.run().getExitStatus() != 0)
                throw new IllegalStateException("Could not install alice data dir");

            BashCommand copyBobDataDir = new BashCommand(
                    "cp -rf " + srcResourcesDir + "/" + bobdaemon.appName
                            + " " + config.rootAppDataDir);
            if (copyBobDataDir.run().getExitStatus() != 0)
                throw new IllegalStateException("Could not install bob data dir");

            log.info("Installed dao-setup files into {}", buildDataDir);

            // Write a bitcoin.conf file with the correct path to the blocknotify script,
            // and save it to the build resource dir.
            installBitcoinConf();

            // Copy the blocknotify script from the src resources dir to the
            // build resources dir.  Users may want to edit it sometimes,
            // when all default block notifcation ports are being used.
            installBitcoinBlocknotify();

        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("Could not install dao-setup files from " + srcResourcesDir, ex);
        }
    }

    private void cleanDaoSetupDirectories() {
        String buildDataDir = config.rootAppDataDir.getAbsolutePath();
        log.info("Cleaning dao-setup data in {}", buildDataDir);

        try {
            BashCommand rmBobDataDir = new BashCommand("rm -rf " + config.rootAppDataDir + "/" + bobdaemon.appName);
            if (rmBobDataDir.run().getExitStatus() != 0)
                throw new IllegalStateException("Could not delete bob data dir");

            BashCommand rmAliceDataDir = new BashCommand("rm -rf " + config.rootAppDataDir + "/" + alicedaemon.appName);
            if (rmAliceDataDir.run().getExitStatus() != 0)
                throw new IllegalStateException("Could not delete alice data dir");

            BashCommand rmArbNodeDataDir = new BashCommand("rm -rf " + config.rootAppDataDir + "/" + arbdaemon.appName);
            if (rmArbNodeDataDir.run().getExitStatus() != 0)
                throw new IllegalStateException("Could not delete arbitrator data dir");

            BashCommand rmSeedNodeDataDir = new BashCommand("rm -rf " + config.rootAppDataDir + "/" + seednode.appName);
            if (rmSeedNodeDataDir.run().getExitStatus() != 0)
                throw new IllegalStateException("Could not delete seednode data dir");

            BashCommand rmBitcoinRegtestDir = new BashCommand("rm -rf " + config.bitcoinDatadir + "/regtest");
            if (rmBitcoinRegtestDir.run().getExitStatus() != 0)
                throw new IllegalStateException("Could not clean bitcoind regtest dir");

        } catch (IOException | InterruptedException ex) {
            throw new IllegalStateException("Could not clean dao-setup files from " + buildDataDir, ex);
        }
    }

    private void installBitcoinConf() {
        // We write out and install a bitcoin.conf file for regtest/dao mode because
        // the path to the blocknotify script is not known until runtime.
        String bitcoinConf = "\n"
                + "regtest=1\n"
                + "[regtest]\n"
                + "peerbloomfilters=1\n"
                + "rpcport=18443\n"
                + "server=1\n"
                + "txindex=1\n"
                + "debug=net\n"
                + "deprecatedrpc=generate\n"
                + "rpcuser=apitest\n"
                + "rpcpassword=apitest\n"
                + "blocknotify=" + config.bashPath + " " + config.bitcoinDatadir + "/blocknotify %\n";
        String chmod644Perms = "rw-r--r--";
        saveToFile(bitcoinConf, config.bitcoinDatadir, "bitcoin.conf", chmod644Perms);
        log.info("Installed {} with perms {}.", config.bitcoinDatadir + "/bitcoin.conf", chmod644Perms);
    }

    private void installBitcoinBlocknotify() {
        // gradle is not working for this
        try {
            Path srcPath = Paths.get("apitest", "src", "main", "resources", "blocknotify");
            Path destPath = Paths.get(config.bitcoinDatadir, "blocknotify");
            Files.copy(srcPath, destPath, REPLACE_EXISTING);
            String chmod700Perms = "rwx------";
            Files.setPosixFilePermissions(destPath, PosixFilePermissions.fromString(chmod700Perms));
            log.info("Installed {} with perms {}.", destPath.toString(), chmod700Perms);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void saveToFile(String content,
                            String parentDir,
                            @SuppressWarnings("SameParameterValue") String relativeFilePath,
                            String posixFilePermissions) {
        File tempFile = null;
        File file;
        try {
            file = Paths.get(parentDir, relativeFilePath).toFile();
            tempFile = File.createTempFile("temp", relativeFilePath, file.getParentFile());
            tempFile.deleteOnExit();
            try (PrintWriter out = new PrintWriter(tempFile)) {
                out.println(content);
            }
            FileUtil.renameFile(tempFile, file);
            Files.setPosixFilePermissions(Paths.get(file.toURI()), PosixFilePermissions.fromString(posixFilePermissions));
        } catch (IOException ex) {
            throw new IllegalStateException(format("Error saving %s/%s to disk", parentDir, relativeFilePath), ex);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                log.warn("Temp file still exists after failed save; deleting {} now.", tempFile.getAbsolutePath());
                if (!tempFile.delete())
                    log.error("Cannot delete temp file.");
            }
        }
    }

    private void installShutdownHook() {
        // Background apps can be left running until the jvm is manually shutdown,
        // so we add a shutdown hook for that use case.
        Runtime.getRuntime().addShutdownHook(new Thread(this::tearDown));
    }

    // Starts bitcoind and bisq apps (seednode, arbnode, etc...)
    private void startBackgroundProcesses(ExecutorService executor,
                                          CountDownLatch countdownLatch)
            throws InterruptedException, IOException {
        // The configured number of setup tasks determines which bisq apps are started in
        // the background, and in what order.
        //
        // If config.numSetupTasks = 0, no setup tasks are run.  If 1, the bitcoind
        // process is started in the background.  If 2, bitcoind and seednode.
        // If 3, bitcoind, seednode and arbnode are started.  If 4, bitcoind, seednode,
        // arbnode, and alicenode are started.  If 5,  bitcoind, seednode, arbnode,
        // alicenode and bobnode are started.
        //
        // This affords an easier way to choose which setup tasks are run, rather than
        // commenting and uncommenting code blocks.  You have to remember seednode
        // depends on bitcoind, arbnode on seednode, and that bob & alice cannot trade
        // unless arbnode is running with a registered mediator and refund agent.
        if (config.numSetupTasks > 0) {
            BitcoinDaemon bitcoinDaemon = new BitcoinDaemon(config);
            bitcoinDaemon.verifyBitcoinConfig(true);
            bitcoindTask = new SetupTask(bitcoinDaemon, countdownLatch);
            bitcoindTaskFuture = executor.submit(bitcoindTask);
            SECONDS.sleep(5);
            bitcoinDaemon.verifyBitcoindRunning();
        }
        if (config.numSetupTasks > 1) {
            startBisqApp(seednode, executor, countdownLatch);
        }
        if (config.numSetupTasks > 2) {
            startBisqApp(config.runArbNodeAsDesktop ? arbdesktop : arbdaemon, executor, countdownLatch);
        }
        if (config.numSetupTasks > 3) {
            startBisqApp(config.runAliceNodeAsDesktop ? alicedesktop : alicedaemon, executor, countdownLatch);
        }
        if (config.numSetupTasks > 4) {
            startBisqApp(config.runBobNodeAsDesktop ? bobdesktop : bobdaemon, executor, countdownLatch);
        }
    }

    private void startBisqApp(BisqAppConfig bisqAppConfig,
                              ExecutorService executor,
                              CountDownLatch countdownLatch)
            throws IOException, InterruptedException {

        BisqApp bisqApp;
        switch (bisqAppConfig) {
            case seednode:
                bisqApp = createBisqApp(seednode);
                seedNodeTask = new SetupTask(bisqApp, countdownLatch);
                seedNodeTaskFuture = executor.submit(seedNodeTask);
                break;
            case arbdaemon:
            case arbdesktop:
                bisqApp = createBisqApp(config.runArbNodeAsDesktop ? arbdesktop : arbdaemon);
                arbNodeTask = new SetupTask(bisqApp, countdownLatch);
                arbNodeTaskFuture = executor.submit(arbNodeTask);
                break;
            case alicedaemon:
            case alicedesktop:
                bisqApp = createBisqApp(config.runAliceNodeAsDesktop ? alicedesktop : alicedaemon);
                aliceNodeTask = new SetupTask(bisqApp, countdownLatch);
                aliceNodeTaskFuture = executor.submit(aliceNodeTask);
                break;
            case bobdaemon:
            case bobdesktop:
                bisqApp = createBisqApp(config.runBobNodeAsDesktop ? bobdesktop : bobdaemon);
                bobNodeTask = new SetupTask(bisqApp, countdownLatch);
                bobNodeTaskFuture = executor.submit(bobNodeTask);
                break;
            default:
                throw new IllegalStateException("Unknown Bisq App " + bisqAppConfig.appName);
        }
        SECONDS.sleep(5);
        if (bisqApp.hasStartupExceptions()) {
            for (Throwable t : bisqApp.getStartupExceptions()) {
                log.error("", t);
            }
            exit(EXIT_FAILURE);
        }
    }

    private BisqApp createBisqApp(BisqAppConfig bisqAppConfig)
            throws IOException, InterruptedException {
        BisqApp bisqNode = new BisqApp(bisqAppConfig, config);
        bisqNode.verifyAppNotRunning();
        bisqNode.verifyAppDataDirInstalled();
        return bisqNode;
    }

    private void verifyStartupCompleted()
            throws ExecutionException, InterruptedException {
        if (config.numSetupTasks > 0)
            verifyStartupCompleted(bitcoindTaskFuture);

        if (config.numSetupTasks > 1)
            verifyStartupCompleted(seedNodeTaskFuture);

        if (config.numSetupTasks > 2)
            verifyStartupCompleted(arbNodeTaskFuture);

        if (config.numSetupTasks > 3)
            verifyStartupCompleted(aliceNodeTaskFuture);

        if (config.numSetupTasks > 4)
            verifyStartupCompleted(bobNodeTaskFuture);
    }

    private void verifyStartupCompleted(Future<SetupTask.Status> futureStatus)
            throws ExecutionException, InterruptedException {
        for (int i = 0; i < 10; i++) {
            if (futureStatus.isDone()) {
                log.info("{} completed startup at {} {}",
                        futureStatus.get().getName(),
                        futureStatus.get().getStartTime().toLocalDate(),
                        futureStatus.get().getStartTime().toLocalTime());
                return;
            } else {
                // We are giving the thread more time to terminate after the countdown
                // latch reached 0.  If we are running only bitcoind, we need to be even
                // more lenient.
                SECONDS.sleep(config.numSetupTasks == 1 ? 2 : 1);
            }
        }
        throw new IllegalStateException(format("%s did not complete startup", futureStatus.get().getName()));
    }

    private void verifyNotWindows() {
        if (Utilities.isWindows())
            throw new IllegalStateException("ApiTest not supported on Windows");
    }
}
