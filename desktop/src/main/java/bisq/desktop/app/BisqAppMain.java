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

package bisq.desktop.app;

import bisq.desktop.common.UITimer;
import bisq.desktop.common.view.guice.InjectorViewFactory;
import bisq.desktop.components.TxIdTextField;
import bisq.desktop.setup.DesktopPersistedDataHost;
import bisq.desktop.util.GUIUtil;

import bisq.core.app.AvoidStandbyModeService;
import bisq.core.app.BisqExecutable;
import bisq.core.app.TorSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.provider.fee.FeeService;
import bisq.core.user.Cookie;
import bisq.core.user.CookieKey;
import bisq.core.user.User;

import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.app.Version;
import bisq.common.util.Utilities;

import javafx.application.Application;
import javafx.application.Platform;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
public class BisqAppMain extends BisqExecutable {

    public static final String DEFAULT_APP_NAME = "Bisq";

    private BisqApp application;

    public BisqAppMain() {
        super("Bisq Desktop", "bisq-desktop", DEFAULT_APP_NAME, Version.VERSION);
    }

    public static void main(String[] args) {
        // For some reason the JavaFX launch process results in us losing the thread
        // context class loader: reset it. In order to work around a bug in JavaFX 8u25
        // and below, you must include the following code as the first line of your
        // realMain method:
        Thread.currentThread().setContextClassLoader(BisqAppMain.class.getClassLoader());

        // Check for existing instance
        if (!checkForExistingInstance()) {
            System.exit(1);
        }

        new BisqAppMain().execute(args);
    }

    private static boolean checkForExistingInstance() {
    try {
        String appDataDir = Utilities.getUserDataDir().getPath();
        String lockFileName = "bisq-desktop.lock";
        File lockFile = new File(appDataDir, lockFileName);

        if (lockFile.exists()) {
            String pidString = Files.readString(Paths.get(lockFile.getPath()));
            long pid = -1; // declare PID before try/catch
            try {
                pid = Long.parseLong(pidString);
                boolean isRunning = ProcessHandle.of(pid)
                        .map(ProcessHandle::isAlive)
                        .orElse(false);

                if (isRunning) {
                    log.warn("Another instance of Bisq desktop is already running with PID: " + pid);
                    return false;
                } else {
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

        long currentPid = ProcessHandle.current().pid();
        try (FileWriter writer = new FileWriter(lockFile)) {
            writer.write(String.valueOf(currentPid));
        }
        log.info("Created lock file with PID: " + currentPid);

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

    @Override
    public void onSetupComplete() {
        log.debug("onSetupComplete");
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // First synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void configUserThread() {
        UserThread.setExecutor(Platform::runLater);
        UserThread.setTimerClass(UITimer.class);
    }

    @Override
    protected void launchApplication() {
        BisqApp.setAppLaunchedHandler(application -> {
            BisqAppMain.this.application = (BisqApp) application;

            onApplicationLaunched();
        });

        Application.launch(BisqApp.class);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // As application is a JavaFX application we need to wait for onApplicationLaunched
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onApplicationLaunched() {
        super.onApplicationLaunched();
        application.setGracefulShutDownHandler(this);
    }

    @Override
    public void handleUncaughtException(Throwable throwable, boolean doShutDown) {
        application.handleUncaughtException(throwable, doShutDown);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected AppModule getModule() {
        return new BisqAppModule(config);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        application.setInjector(injector);
        injector.getInstance(InjectorViewFactory.class).setInjector(injector);

        GUIUtil.setFeeService(injector.getInstance(FeeService.class));
        TxIdTextField.setWalletService(injector.getInstance(BtcWalletService.class));
    }

    @Override
    protected void readAllPersisted(Runnable completeHandler) {
        super.readAllPersisted(DesktopPersistedDataHost.getPersistedDataHosts(injector), completeHandler);
    }

    @Override
    protected void setupAvoidStandbyMode() {
        injector.getInstance(AvoidStandbyModeService.class).init();
    }

    @Override
    protected void startApplication() {
        Cookie cookie = injector.getInstance(User.class).getCookie();
        cookie.getAsOptionalBoolean(CookieKey.CLEAN_TOR_DIR_AT_RESTART).ifPresent(cleanTorDirAtRestart -> {
            if (cleanTorDirAtRestart) {
                injector.getInstance(TorSetup.class).cleanupTorFiles(() ->
                                cookie.remove(CookieKey.CLEAN_TOR_DIR_AT_RESTART),
                        log::error);
            }
        });

        // We need to be in user thread! We mapped at launchApplication already.  Once
        // the UI is ready we get onApplicationStarted called and start the setup there.
        application.startApplication(this::onApplicationStarted);
    }

    @Override
    protected void onApplicationStarted() {
        super.onApplicationStarted();

        // Relevant to have this in the logs, for support cases
        // This can only be called after JavaFX is initialized, otherwise the version logged will be null
        // Therefore, calling this as part of onApplicationStarted()
        log.info("Using JavaFX {}", System.getProperty("javafx.version"));
    }
}
