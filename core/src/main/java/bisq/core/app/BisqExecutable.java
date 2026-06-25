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
import bisq.core.dao.node.full.RpcService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.bsq_swap.OpenBsqSwapOfferService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.setup.CorePersistedDataHost;
import bisq.core.setup.CoreSetup;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.locale.Res;
import bisq.core.trade.txproof.xmr.XmrTxProofService;

import bisq.network.p2p.P2PService;

import bisq.common.ClockWatcher;
import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.app.InstanceLock;
import bisq.common.config.BisqHelpFormatter;
import bisq.common.config.Config;
import bisq.common.config.ConfigException;
import bisq.common.handlers.ResultHandler;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.setup.CommonSetup;
import bisq.common.setup.GracefulShutDownHandler;
import bisq.common.setup.UncaughtExceptionHandler;
import bisq.common.util.Utilities;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.io.IOException;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public abstract class BisqExecutable implements GracefulShutDownHandler, BisqSetup.BisqSetupListener, UncaughtExceptionHandler {

    public static final int EXIT_SUCCESS = 0;
    public static final int EXIT_FAILURE = 1;

    private final String fullName;
    private final String scriptName;
    private final String appName;
    private final String version;

    protected Injector injector;
    protected AppModule module;
    protected Config config;
    protected InstanceLock instanceLock;
    protected volatile boolean isShutdownInProgress;
    private boolean hasDowngraded;

    public BisqExecutable(String fullName, String scriptName, String appName, String version) {
        this.fullName = fullName;
        this.scriptName = scriptName;
        this.appName = appName;
        this.version = version;
    }

    public void execute(String[] args) {
        try {
            config = new Config(appName, Utilities.getUserDataDir(), args);
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
        if (!acquireInstanceLock()) {
            // Another instance is already running against this data directory. The handler
            // has already informed the user and terminated the process; we stop here defensively.
            return;
        }

        CommonSetup.setup(config, this);
        CoreSetup.setup(config);

        addCapabilities();

        // If application is JavaFX application we need to wait until it is initialized
        launchApplication();
    }

    /**
     * Acquires the single-instance lock on the application data directory.
     *
     * @return true if startup may proceed, false if another instance already runs (in which
     *         case {@link #handleAnotherInstanceRunning} has terminated the process).
     */
    private boolean acquireInstanceLock() {
        instanceLock = new InstanceLock(config.appDataDir.toPath());
        try {
            if (instanceLock.tryLock()) {
                return true;
            }
        } catch (IOException e) {
            // The lock mechanism itself is unusable (e.g. data dir not writable). We fail open
            // rather than bricking startup: an unusable lock file must not prevent the app from
            // running, and an unwritable data dir will surface its own error downstream.
            log.warn("Could not acquire single-instance lock, continuing without it", e);
            return true;
        }
        // Res is used by the conflict handlers but its currency substitution is only safe after
        // setup(). This runs before CoreSetup, so initialize it here. Config is already built.
        Res.setup();
        handleAnotherInstanceRunning();
        return false;
    }

    /**
     * Called when another instance already holds the lock. Default implementation logs the
     * reason and exits. Desktop overrides this to additionally show a user-facing dialog.
     */
    protected void handleAnotherInstanceRunning() {
        String pidInfo = instanceLock.readOwnerPid().map(pid -> " (PID " + pid + ")").orElse("");
        log.error("Another instance of {} is already running{} using data directory {}",
                appName, pidInfo, config.appDataDir);
        System.err.println("error: " + Res.get("popup.alreadyRunning.msg", appName, config.appDataDir));
        System.exit(EXIT_FAILURE);
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
    // Desktop gets called from JavaFx thread
    protected void onApplicationLaunched() {
        configUserThread();
        ShutdownDelayer.setClock(new ShutdownDelayer.BlockingClock());

        // Now we can use the user thread start periodic tasks
        CommonSetup.startPeriodicTasks();

        // As the handler method might be overwritten by subclasses and they use the application as handler
        // we need to setup the handler after the application is created.
        CommonSetup.setupUncaughtExceptionHandler(this);
        setupGuice();

        hasDowngraded = BisqSetup.hasDowngraded();
        if (hasDowngraded) {
            // If user tried to downgrade we do not read the persisted data to avoid data corruption
            // We call startApplication to enable UI to show popup. We prevent in BisqSetup to go further
            // in the process and require a shut down.
            UserThread.execute(this::startApplication);
        } else {
            readAllPersisted(this::startApplication);
        }
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
        // Subclasses might configure classes with the injector here
    }

    protected void readAllPersisted(Runnable completeHandler) {
        readAllPersisted(null, completeHandler);
    }

    protected void readAllPersisted(@Nullable List<PersistedDataHost> additionalHosts, Runnable completeHandler) {
        List<PersistedDataHost> hosts = CorePersistedDataHost.getPersistedDataHosts(injector);
        if (additionalHosts != null) {
            hosts.addAll(additionalHosts);
        }

        AtomicInteger remaining = new AtomicInteger(hosts.size());
        hosts.forEach(host -> {
            host.readPersisted(() -> {
                if (remaining.decrementAndGet() == 0) {
                    UserThread.execute(completeHandler);
                }
            });
        });
    }

    protected void setupAvoidStandbyMode() {
    }

    protected abstract void startApplication();

    // Once the application is ready we get that callback and we start the setup
    protected void onApplicationStarted() {
        runBisqSetup();
        setupAvoidStandbyMode();
    }

    protected void runBisqSetup() {
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
            log.info("Shut down called before injector was created");
            resultHandler.handleResult();
            System.exit(EXIT_SUCCESS);
        }

        // We do not use the UserThread to avoid that the timeout would not get triggered in case the UserThread
        // would get blocked by a shutdown routine.
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                log.warn("Graceful shutdown not completed in 10 sec. We trigger our timeout handler.");
                flushAndExit(resultHandler, EXIT_SUCCESS);
            }
        }, 10000);

        try {
            injector.getInstance(ClockWatcher.class).shutDown();
            injector.getInstance(OpenBsqSwapOfferService.class).shutDown();
            injector.getInstance(PriceFeedService.class).shutDown();
            injector.getInstance(TradeStatisticsManager.class).shutDown();
            injector.getInstance(XmrTxProofService.class).shutDown();
            injector.getInstance(RpcService.class).shutDown();
            injector.getInstance(DaoSetup.class).shutDown();
            injector.getInstance(AvoidStandbyModeService.class).shutDown();
            log.info("OpenOfferManager shutdown started");
            injector.getInstance(OpenOfferManager.class).shutDown(() -> {
                log.info("OpenOfferManager shutdown completed");

                injector.getInstance(BtcWalletService.class).shutDown();
                injector.getInstance(BsqWalletService.class).shutDown();

                // We need to shut down BitcoinJ before the P2PService as it uses Tor.
                WalletsSetup walletsSetup = injector.getInstance(WalletsSetup.class);
                walletsSetup.shutDownComplete.addListener((ov, o, n) -> {
                    log.info("WalletsSetup shutdown completed");
                    injector.getInstance(P2PService.class).shutDown(() -> {
                        log.info("P2PService shutdown completed");
                        module.close(injector);
                        flushAndExit(resultHandler, EXIT_SUCCESS);
                    });
                });
                walletsSetup.shutDown();
            });
        } catch (Throwable t) {
            log.error("App shutdown failed with an exception", t);
            flushAndExit(resultHandler, EXIT_FAILURE);
        }
    }

    protected void flushAndExit(ResultHandler resultHandler, int status) {
        // The OS releases the lock on process exit; we release explicitly for a clean handover
        // to a restarting instance.
        if (instanceLock != null) {
            instanceLock.close();
        }
        if (!hasDowngraded) {
            // If user tried to downgrade we do not write the persistable data to avoid data corruption
            log.info("PersistenceManager flushAllDataToDiskAtShutdown started");
            PersistenceManager.flushAllDataToDiskAtShutdown(() -> {
                log.info("Graceful shutdown completed. Exiting now.");
                resultHandler.handleResult();
                UserThread.runAfter(() -> System.exit(status), 100, TimeUnit.MILLISECONDS);
            });
        } else {
            UserThread.runAfter(() -> System.exit(status), 100, TimeUnit.MILLISECONDS);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UncaughtExceptionHandler implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleUncaughtException(Throwable throwable, boolean doShutDown) {
        log.error(throwable.toString());

        if (doShutDown)
            gracefulShutDown(() -> log.info("gracefulShutDown complete"));
    }
}
