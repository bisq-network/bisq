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

package bisq.core.app;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoSetup;
import bisq.core.offer.OpenOfferManager;
import bisq.core.setup.CorePersistedDataHost;
import bisq.core.setup.CoreSetup;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.txproof.xmr.XmrTxProofService;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.app.DevEnv;
import bisq.common.config.BisqHelpFormatter;
import bisq.common.config.Config;
import bisq.common.config.ConfigException;
import bisq.common.handlers.ResultHandler;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.setup.GracefulShutDownHandler;
import bisq.common.util.Utilities;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.nio.file.Paths;

import java.io.File;

import lombok.extern.slf4j.Slf4j;



import sun.misc.Signal;

@Slf4j
public abstract class BisqExecutable implements GracefulShutDownHandler, BisqSetup.BisqSetupListener {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_FAILURE = 1;

    private final String fullName;
    private final String scriptName;
    private final String appName;
    private final String version;

    protected Injector injector;
    protected AppModule module;
    protected Config config;
    private boolean isShutdownInProgress;

    public BisqExecutable(String fullName, String scriptName, String appName, String version) {
        this.fullName = fullName;
        this.scriptName = scriptName;
        this.appName = appName;
        this.version = version;
    }

    public void execute(String[] args) {
        try {
            config = new Config(appName, osUserDataDir(), args);
            if (config.helpRequested) {
                config.printHelp(System.out, new BisqHelpFormatter(fullName, scriptName, version));
                System.exit(EXIT_SUCCESS);
            }
        } catch (ConfigException ex) {
            System.err.println("error: " + ex.getMessage());
            System.exit(EXIT_FAILURE);
        } catch (Throwable ex) {
            System.err.println("fault: An unexpected error occurred. " +
                    "Please file a report at https://bisq.network/issues");
            ex.printStackTrace(System.err);
            System.exit(EXIT_FAILURE);
        }

        doExecute();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // First synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void doExecute() {
        configUserThread();
        CoreSetup.setup(config);
        addCapabilities();

        Signal.handle(new Signal("INT"), signal -> {
            gracefulShutDown(() -> {
            });
        });

        Signal.handle(new Signal("TERM"), signal -> {
            gracefulShutDown(() -> {
            });
        });

        // If application is JavaFX application we need to wait until it is initialized
        launchApplication();
    }

    protected abstract void configUserThread();

    protected void addCapabilities() {
    }

    // The onApplicationLaunched call must map to UserThread, so that all following methods are running in the
    // thread the application is running and we don't run into thread interference.
    protected abstract void launchApplication();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // If application is a JavaFX application we need wait for onApplicationLaunched
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Headless versions can call inside launchApplication the onApplicationLaunched() manually
    protected void onApplicationLaunched() {
        setupGuice();
        startApplication();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void setupGuice() {
        module = getModule();
        injector = getInjector();
        applyInjector();
    }

    protected abstract AppModule getModule();

    protected Injector getInjector() {
        return Guice.createInjector(module);
    }

    protected void applyInjector() {
        setupDevEnv();

        setupPersistedDataHosts(injector);
    }

    protected void setupDevEnv() {
        DevEnv.setDevMode(config.useDevMode);
        DevEnv.setDaoActivated(config.daoActivated);
    }

    protected void setupPersistedDataHosts(Injector injector) {
        try {
            PersistedDataHost.apply(CorePersistedDataHost.getPersistedDataHosts(injector));
        } catch (Throwable t) {
            log.error("Error at PersistedDataHost.apply: {}", t.toString(), t);
            // If we are in dev mode we want to get the exception if some db files are corrupted
            // We need to delay it as the stage is not created yet and so popups would not be shown.
            if (DevEnv.isDevMode())
                UserThread.runAfter(() -> {
                    throw t;
                }, 2);
        }
    }

    protected abstract void startApplication();

    // Once the application is ready we get that callback and we start the setup
    protected void onApplicationStarted() {
        startAppSetup();
    }

    protected void startAppSetup() {
        BisqSetup bisqSetup = injector.getInstance(BisqSetup.class);
        bisqSetup.addBisqSetupListener(this);
        bisqSetup.start();
    }

    public abstract void onSetupComplete();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // GracefulShutDownHandler implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    // This might need to be overwritten in case the application is not using all modules
    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        log.info("Start graceful shutDown");
        if (isShutdownInProgress) {
            return;
        }

        isShutdownInProgress = true;

        if (injector == null) {
            log.warn("Shut down called before injector was created");
            resultHandler.handleResult();
            System.exit(EXIT_SUCCESS);
        }

        try {
            injector.getInstance(ArbitratorManager.class).shutDown();
            injector.getInstance(TradeManager.class).shutDown();
            injector.getInstance(XmrTxProofService.class).shutDown();
            injector.getInstance(DaoSetup.class).shutDown();
            injector.getInstance(AvoidStandbyModeService.class).shutDown();
            injector.getInstance(OpenOfferManager.class).shutDown(() -> {
                log.info("OpenOfferManager shutdown completed");

                injector.getInstance(BtcWalletService.class).shutDown();
                injector.getInstance(BsqWalletService.class).shutDown();

                // We need to shutdown BitcoinJ before the P2PService as it uses Tor.
                WalletsSetup walletsSetup = injector.getInstance(WalletsSetup.class);
                walletsSetup.shutDownComplete.addListener((ov, o, n) -> {
                    log.info("WalletsSetup shutdown completed");

                    injector.getInstance(P2PService.class).shutDown(() -> {
                        log.info("P2PService shutdown completed");
                        module.close(injector);
                        PersistenceManager.flushAllDataToDisk(() -> {
                            log.info("Graceful shutdown completed. Exiting now.");
                            resultHandler.handleResult();
                            System.exit(EXIT_SUCCESS);
                        });
                    });
                });
                walletsSetup.shutDown();

            });

            // Wait max 20 sec.
            UserThread.runAfter(() -> {
                log.warn("Timeout triggered resultHandler");
                PersistenceManager.flushAllDataToDisk(() -> {
                    log.info("Graceful shutdown resulted in a timeout. Exiting now.");
                    resultHandler.handleResult();
                    System.exit(EXIT_SUCCESS);
                });
            }, 20);
        } catch (Throwable t) {
            log.error("App shutdown failed with exception {}", t.toString());
            t.printStackTrace();
            PersistenceManager.flushAllDataToDisk(() -> {
                log.info("Graceful shutdown resulted in an error. Exiting now.");
                resultHandler.handleResult();
                System.exit(EXIT_FAILURE);
            });
        }
    }

    /**
     * Returns the well-known "user data directory" for the current operating system.
     */
    private static File osUserDataDir() {
        if (Utilities.isWindows())
            return new File(System.getenv("APPDATA"));

        if (Utilities.isOSX())
            return Paths.get(System.getProperty("user.home"), "Library", "Application Support").toFile();

        // *nix
        return Paths.get(System.getProperty("user.home"), ".local", "share").toFile();
    }
}
