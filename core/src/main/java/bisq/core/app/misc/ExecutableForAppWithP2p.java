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
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoSetup;
import bisq.core.dao.node.full.RpcService;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.bsq_swap.OpenBsqSwapOfferService;
import bisq.core.payment.TradeLimits;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.seed.SeedNodeRepository;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.config.Config;
import bisq.common.file.JsonFileManager;
import bisq.common.handlers.ResultHandler;
import bisq.common.persistence.PersistenceManager;
import bisq.common.setup.GracefulShutDownHandler;
import bisq.common.util.Profiler;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class ExecutableForAppWithP2p extends BisqExecutable {
    private static final long CHECK_MEMORY_PERIOD_SEC = 300;
    private static final long CHECK_SHUTDOWN_SEC = TimeUnit.HOURS.toSeconds(1);
    private static final long SHUTDOWN_INTERVAL = TimeUnit.HOURS.toMillis(24);
    private volatile boolean stopped;
    private final long startTime = System.currentTimeMillis();
    private TradeLimits tradeLimits;

    public ExecutableForAppWithP2p(String fullName, String scriptName, String appName, String version) {
        super(fullName, scriptName, appName, version);
    }

    @Override
    protected void configUserThread() {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(this.getClass().getSimpleName())
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));
    }

    @Override
    protected void startApplication() {
        // Pin that as it is used in PaymentMethods and verification in TradeStatistics
        tradeLimits = injector.getInstance(TradeLimits.class);
    }

    @Override
    public void onSetupComplete() {
        log.info("onSetupComplete");
    }

    // We don't use the gracefulShutDown implementation of the super class as we have a limited set of modules
    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        log.info("gracefulShutDown");
        try {
            if (injector != null) {
                JsonFileManager.shutDownAllInstances();
                injector.getInstance(OpenBsqSwapOfferService.class).shutDown();
                injector.getInstance(RpcService.class).shutDown();
                injector.getInstance(DaoSetup.class).shutDown();
                injector.getInstance(ArbitratorManager.class).shutDown();
                injector.getInstance(OpenOfferManager.class).shutDown(() -> injector.getInstance(P2PService.class).shutDown(() -> {
                    injector.getInstance(WalletsSetup.class).shutDownComplete.addListener((ov, o, n) -> {
                        module.close(injector);

                        PersistenceManager.flushAllDataToDiskAtShutdown(() -> {
                            resultHandler.handleResult();
                            log.info("Graceful shutdown completed. Exiting now.");
                            UserThread.runAfter(() -> System.exit(BisqExecutable.EXIT_SUCCESS), 1);
                        });
                    });
                    injector.getInstance(WalletsSetup.class).shutDown();
                    injector.getInstance(BtcWalletService.class).shutDown();
                    injector.getInstance(BsqWalletService.class).shutDown();
                }));
                // we wait max 5 sec.
                UserThread.runAfter(() -> {
                    PersistenceManager.flushAllDataToDiskAtShutdown(() -> {
                        resultHandler.handleResult();
                        log.info("Graceful shutdown caused a timeout. Exiting now.");
                        UserThread.runAfter(() -> System.exit(BisqExecutable.EXIT_SUCCESS), 1);
                    });
                }, 5);
            } else {
                UserThread.runAfter(() -> {
                    resultHandler.handleResult();
                    System.exit(BisqExecutable.EXIT_SUCCESS);
                }, 1);
            }
        } catch (Throwable t) {
            log.debug("App shutdown failed with exception");
            t.printStackTrace();
            PersistenceManager.flushAllDataToDiskAtShutdown(() -> {
                resultHandler.handleResult();
                log.info("Graceful shutdown resulted in an error. Exiting now.");
                UserThread.runAfter(() -> System.exit(BisqExecutable.EXIT_FAILURE), 1);
            });

        }
    }

    public void startShutDownInterval(GracefulShutDownHandler gracefulShutDownHandler) {
        if (DevEnv.isDevMode() || injector.getInstance(Config.class).useLocalhostForP2P) {
            return;
        }

        List<NodeAddress> seedNodeAddresses = new ArrayList<>(injector.getInstance(SeedNodeRepository.class).getSeedNodeAddresses());
        seedNodeAddresses.sort(Comparator.comparing(NodeAddress::getFullAddress));

        NodeAddress myAddress = injector.getInstance(P2PService.class).getNetworkNode().getNodeAddress();
        int myIndex = -1;
        for (int i = 0; i < seedNodeAddresses.size(); i++) {
            if (seedNodeAddresses.get(i).equals(myAddress)) {
                myIndex = i;
                break;
            }
        }

        if (myIndex == -1) {
            log.warn("We did not find our node address in the seed nodes repository. " +
                            "We use a 24 hour delay after startup as shut down strategy." +
                            "myAddress={}, seedNodeAddresses={}",
                    myAddress, seedNodeAddresses);

            UserThread.runPeriodically(() -> {
                if (System.currentTimeMillis() - startTime > SHUTDOWN_INTERVAL) {
                    log.warn("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                    "Shut down as node was running longer as {} hours" +
                                    "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n\n",
                            SHUTDOWN_INTERVAL / 3600000);

                    shutDown(gracefulShutDownHandler);
                }

            }, CHECK_SHUTDOWN_SEC);
            return;
        }

        // We interpret the value of myIndex as hour of day (0-23). That way we avoid the risk of a restart of
        // multiple nodes around the same time in case it would be not deterministic.

        // We wrap our periodic check in a delay of 2 hours to avoid that we get
        // triggered multiple times after a restart while being in the same hour. It can be that we miss our target
        // hour during that delay but that is not considered problematic, the seed would just restart a bit longer than
        // 24 hours.
        int target = myIndex;
        UserThread.runAfter(() -> {
            // We check every hour if we are in the target hour.
            UserThread.runPeriodically(() -> {
                int currentHour = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")).getHour();
                if (currentHour == target) {
                    log.warn("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                    "Shut down node at hour {} (UTC time is {})" +
                                    "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n\n",
                            target,
                            ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC")).toString());
                    shutDown(gracefulShutDownHandler);
                }
            }, TimeUnit.MINUTES.toSeconds(10));
        }, TimeUnit.HOURS.toSeconds(2));
    }

    @SuppressWarnings("InfiniteLoopStatement")
    protected void keepRunning() {
        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }

    protected void checkMemory(Config config, GracefulShutDownHandler gracefulShutDownHandler) {
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
                                (int) maxMemory, usedMemory, Profiler.getFreeMemoryInMB());
                        shutDown(gracefulShutDownHandler);
                    }
                }, 5);
            }
        }, CHECK_MEMORY_PERIOD_SEC);
    }

    protected void shutDown(GracefulShutDownHandler gracefulShutDownHandler) {
        stopped = true;
        gracefulShutDownHandler.gracefulShutDown(() -> {
            log.info("Shutdown complete");
            System.exit(1);
        });
    }
}
