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

package bisq.core.app.misc;

import bisq.core.app.BisqExecutable;
import bisq.core.app.TorSetup;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoSetup;
import bisq.core.dao.monitoring.DaoStateMonitoringService;
import bisq.core.dao.node.full.RpcService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.bsq_swap.OpenBsqSwapOfferService;
import bisq.core.payment.TradeLimits;
import bisq.core.user.Cookie;
import bisq.core.user.CookieKey;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.P2PServiceListener;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.app.DevEnv;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;
import bisq.common.file.JsonFileManager;
import bisq.common.handlers.ResultHandler;
import bisq.common.persistence.PersistenceManager;
import bisq.common.util.Profiler;
import bisq.common.util.SingleThreadExecutorUtils;

import com.google.inject.Key;
import com.google.inject.name.Names;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ExecutableForAppWithP2p extends BisqExecutable {
    private static final long CHECK_MEMORY_PERIOD_SEC = 300;
    protected static final long CHECK_SHUTDOWN_SEC = TimeUnit.HOURS.toSeconds(1);
    protected static final long SHUTDOWN_INTERVAL = TimeUnit.HOURS.toMillis(24);
    private static final long CHECK_CONNECTION_LOSS_SEC = 30;

    private volatile boolean stopped;
    private final long startTime = System.currentTimeMillis();
    private TradeLimits tradeLimits;
    private AppSetupWithP2PAndDAO appSetupWithP2PAndDAO;
    protected P2PService p2PService;
    protected Preferences preferences;
    protected DaoStateMonitoringService daoStateMonitoringService;

    protected Cookie cookie;
    private Timer checkConnectionLossTimer;
    private Boolean preventPeriodicShutdownAtSeedNode;

    public ExecutableForAppWithP2p(String fullName, String scriptName, String appName, String version) {
        super(fullName, scriptName, appName, version);
    }

    @Override
    protected void configUserThread() {
        ExecutorService executorService = SingleThreadExecutorUtils.getSingleThreadExecutor(this.getClass());
        UserThread.setExecutor(executorService);
    }

    @Override
    protected AppModule getModule() {
        return new ModuleForAppWithP2p(config);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        appSetupWithP2PAndDAO = injector.getInstance(AppSetupWithP2PAndDAO.class);
        p2PService = injector.getInstance(P2PService.class);
        cookie = injector.getInstance(User.class).getCookie();
        // Pin that as it is used in PaymentMethods and verification in TradeStatistics
        tradeLimits = injector.getInstance(TradeLimits.class);
        daoStateMonitoringService = injector.getInstance(DaoStateMonitoringService.class);
        preferences = injector.getInstance(Preferences.class);
        preventPeriodicShutdownAtSeedNode = injector.getInstance(Key.get(boolean.class,
                Names.named(Config.PREVENT_PERIODIC_SHUTDOWN_AT_SEED_NODE)));
    }

    @Override
    protected void startApplication() {
        cookie.getAsOptionalBoolean(CookieKey.CLEAN_TOR_DIR_AT_RESTART).ifPresent(cleanTorDirAtRestart -> {
            if (cleanTorDirAtRestart) {
                injector.getInstance(TorSetup.class).cleanupTorFiles(() ->
                                cookie.remove(CookieKey.CLEAN_TOR_DIR_AT_RESTART),
                        log::error);
            }
        });

        // If the option useFullModeDaoMonitor is set the preferences value is ignored, otherwise we default to true
        // as we expect that headless nodes run as full dao nodes
        preferences.setUseFullModeDaoMonitor(true);

        daoStateMonitoringService.addListener(new DaoStateMonitoringService.Listener() {
            @Override
            public void onCheckpointFailed() {
                ExecutableForAppWithP2p.this.shutDown();
            }
        });

        p2PService.addP2PServiceListener(new P2PServiceListener() {
            @Override
            public void onDataReceived() {
            }

            @Override
            public void onNoSeedNodeAvailable() {
            }

            @Override
            public void onNoPeersAvailable() {
            }

            @Override
            public void onUpdatedDataReceived() {
            }

            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onHiddenServicePublished() {
                ExecutableForAppWithP2p.this.onHiddenServicePublished();
            }
        });

        appSetupWithP2PAndDAO.start();
    }

    protected void onHiddenServicePublished() {
        if (!preventPeriodicShutdownAtSeedNode) {
            startShutDownInterval();
        }
        UserThread.runAfter(this::setupConnectionLossCheck, 60);
    }

    @Override
    protected void launchApplication() {
        onApplicationLaunched();
    }

    @Override
    public void onSetupComplete() {
        log.info("onSetupComplete");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UncaughtExceptionHandler implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void handleUncaughtException(Throwable throwable, boolean doShutDown) {
        if (throwable instanceof OutOfMemoryError || doShutDown) {
            log.error("We shut down because of an uncaught error.", throwable);
            shutDown();
        }
    }

    // We don't use the gracefulShutDown implementation of the super class as we have a limited set of modules
    @Override
    public final void gracefulShutDown(ResultHandler resultHandler) {
        gracefulShutDown(resultHandler, BisqExecutable.EXIT_SUCCESS);
    }

    private void gracefulShutDown(ResultHandler resultHandler, int requestedExitStatus) {
        log.info("gracefulShutDown");
        if (!beginGracefulShutDown(resultHandler)) {
            if (requestedExitStatus != BisqExecutable.EXIT_SUCCESS) {
                log.warn("A shutdown is already in progress. We keep the exit status of that shutdown and " +
                        "do not apply the requested exit status {}.", requestedExitStatus);
            }
            return;
        }

        if (checkConnectionLossTimer != null) {
            checkConnectionLossTimer.stop();
        }
        try {
            shutDownAdditionalServices();
            if (injector != null) {
                JsonFileManager.shutDownAllInstances();
                injector.getInstance(OpenBsqSwapOfferService.class).shutDown();
                injector.getInstance(RpcService.class).shutDown();
                injector.getInstance(DaoSetup.class).shutDown();
                injector.getInstance(OpenOfferManager.class).shutDown(() -> injector.getInstance(P2PService.class).shutDown(() -> {
                    injector.getInstance(WalletsSetup.class).shutDownComplete.addListener((ov, o, n) -> {
                        module.close(injector);

                        PersistenceManager.flushAllDataToDiskAtShutdown(() -> {
                            completeShutDown(requestedExitStatus, 1, TimeUnit.SECONDS);
                            log.info("Graceful shutdown completed. Exiting now.");
                        });
                    });
                    injector.getInstance(WalletsSetup.class).shutDown();
                    injector.getInstance(BtcWalletService.class).shutDown();
                    injector.getInstance(BsqWalletService.class).shutDown();
                }));
                // we wait max 5 sec.
                UserThread.runAfter(() -> {
                    PersistenceManager.flushAllDataToDiskAtShutdown(() -> {
                        completeShutDown(requestedExitStatus, 1, TimeUnit.SECONDS);
                        log.info("Graceful shutdown caused a timeout. Exiting now.");
                    });
                }, 5);
            } else {
                completeShutDown(requestedExitStatus, 1, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.error("App shutdown failed with exception", e);
            PersistenceManager.flushAllDataToDiskAtShutdown(() -> {
                completeShutDown(BisqExecutable.EXIT_FAILURE, 1, TimeUnit.SECONDS);
                log.info("Graceful shutdown resulted in an error. Exiting now.");
            });

        }
    }

    protected void shutDownAdditionalServices() {
    }

    public void startShutDownInterval() {
        if (DevEnv.isDevMode() || injector.getInstance(Config.class).useLocalhostForP2P) {
            return;
        }

        UserThread.runPeriodically(() -> {
            if (System.currentTimeMillis() - startTime > SHUTDOWN_INTERVAL) {
                log.warn("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                "Shut down as node was running longer as {} hours" +
                                "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n\n",
                        SHUTDOWN_INTERVAL / 3600000);

                shutDown();
            }

        }, CHECK_SHUTDOWN_SEC);
    }

    @SuppressWarnings("InfiniteLoopStatement")
    protected void keepRunning() {
        while (true) {
            try {
                //noinspection BusyWait
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }

    protected void checkMemory(Config config) {
        int maxMemory = config.maxMemory;
        UserThread.runPeriodically(() -> {
            Profiler.printSystemLoad();
            if (!stopped) {
                long usedMemoryInMB = Profiler.getUsedMemoryInMB();
                double warningTrigger = maxMemory * 0.8;
                if (usedMemoryInMB > warningTrigger) {
                    log.warn("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                    "We are over 80% of our memory limit ({}) and call the GC. usedMemory: {} MB. freeMemory: {} MB" +
                                    "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n\n",
                            (int) warningTrigger, usedMemoryInMB, Profiler.getFreeMemoryInMB());
                    System.gc();
                    Profiler.printSystemLoad();
                }

                UserThread.runAfter(() -> {
                    log.info("Memory 2 sec. after calling the GC. usedMemory: {} MB. freeMemory: {} MB",
                            Profiler.getUsedMemoryInMB(), Profiler.getFreeMemoryInMB());
                }, 2);

                UserThread.runAfter(() -> {
                    long usedMemory = Profiler.getUsedMemoryInMB();
                    if (usedMemory > maxMemory) {
                        log.warn("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                        "We are over our memory limit ({}) and trigger a shutdown. usedMemory: {} MB. freeMemory: {} MB" +
                                        "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n\n",
                                maxMemory, usedMemory, Profiler.getFreeMemoryInMB());
                        shutDown();
                    }
                }, 5);
            }
        }, CHECK_MEMORY_PERIOD_SEC);
    }

    /**
     * Shut down after an error condition. The process exits with {@link BisqExecutable#EXIT_FAILURE} so that the
     * wrapper script or systemd restarts the node.
     */
    protected void shutDown() {
        stopped = true;
        gracefulShutDown(() -> log.info("Shutdown complete"), BisqExecutable.EXIT_FAILURE);
    }

    protected void setupConnectionLossCheck() {
        // For dev testing (usually on BTC_REGTEST) we don't want to get the seed shut
        // down as it is normal that the seed is the only actively running node.
        if (Config.baseCurrencyNetwork() == BaseCurrencyNetwork.BTC_REGTEST) {
            return;
        }

        if (checkConnectionLossTimer != null) {
            return;
        }

        checkConnectionLossTimer = UserThread.runPeriodically(() -> {
            if (injector.getInstance(PeerManager.class).getNumAllConnectionsLostEvents() > 1) {
                // We set a flag to clear tor cache files at re-start. We cannot clear it now as Tor is used and
                // that can cause problems.
                injector.getInstance(User.class).getCookie().putAsBoolean(CookieKey.CLEAN_TOR_DIR_AT_RESTART, true);
                shutDown();
            }
        }, CHECK_CONNECTION_LOSS_SEC);
    }
}
