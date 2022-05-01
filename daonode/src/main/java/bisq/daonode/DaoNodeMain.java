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

package bisq.daonode;


import bisq.core.app.TorSetup;
import bisq.core.app.misc.ExecutableForAppWithP2p;
import bisq.core.app.misc.ModuleForAppWithP2p;
import bisq.core.dao.state.DaoStateSnapshotService;
import bisq.core.user.Cookie;
import bisq.core.user.CookieKey;
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
import bisq.common.handlers.ResultHandler;

import com.google.inject.Key;
import com.google.inject.name.Names;

import lombok.extern.slf4j.Slf4j;
//todo not sure if the restart handling from seed nodes is required

@Slf4j
public class DaoNodeMain extends ExecutableForAppWithP2p {
    private static final long CHECK_CONNECTION_LOSS_SEC = 30;
    private static final int DEFAULT_REST_SERVER_PORT = 8080;
    private static final String VERSION = "1.8.4";

    private DaoNode daoNode;
    private Timer checkConnectionLossTime;
    private int restServerPort = DEFAULT_REST_SERVER_PORT;

    public DaoNodeMain() {
        super("Bisq Daonode", "bisq-daonode", "bisq_daonode", VERSION);
    }

    public static void main(String[] args) {
        System.out.println("DaoNode.VERSION: " + VERSION);

        new DaoNodeMain().execute(args);
    }

    @Override
    protected void doExecute() {
        super.doExecute();

        checkMemory(config, this);

        keepRunning();
    }

    @Override
    protected void addCapabilities() {
    }

    @Override
    protected void launchApplication() {
        UserThread.execute(() -> {
            try {
                daoNode = new DaoNode();
                onApplicationLaunched();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Override
    protected void onApplicationLaunched() {
        super.onApplicationLaunched();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // We continue with a series of synchronous execution tasks
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected AppModule getModule() {
        return new ModuleForAppWithP2p(config);
    }

    @Override
    protected void applyInjector() {
        super.applyInjector();

        daoNode.setInjector(injector);

        if (DevEnv.isDaoActivated()) {
            injector.getInstance(DaoStateSnapshotService.class).setDaoRequiresRestartHandler(this::gracefulShutDown);
        }
    }

    @Override
    protected void startApplication() {
        super.startApplication();

        Cookie cookie = injector.getInstance(User.class).getCookie();
        cookie.getAsOptionalBoolean(CookieKey.CLEAN_TOR_DIR_AT_RESTART).ifPresent(wasCleanTorDirSet -> {
            if (wasCleanTorDirSet) {
                injector.getInstance(TorSetup.class).cleanupTorFiles(() -> {
                    log.info("Tor directory reset");
                    cookie.remove(CookieKey.CLEAN_TOR_DIR_AT_RESTART);
                }, log::error);
            }
        });

        //todo add program arg for port
        daoNode.startApplication(restServerPort);

        injector.getInstance(P2PService.class).addP2PServiceListener(new P2PServiceListener() {
            @Override
            public void onDataReceived() {
                // Do nothing
            }

            @Override
            public void onNoSeedNodeAvailable() {
                // Do nothing
            }

            @Override
            public void onNoPeersAvailable() {
                // Do nothing
            }

            @Override
            public void onUpdatedDataReceived() {
                // Do nothing
            }

            @Override
            public void onTorNodeReady() {
                // Do nothing
            }

            @Override
            public void onHiddenServicePublished() {
                boolean preventPeriodicShutdownAtSeedNode = injector.getInstance(Key.get(boolean.class,
                        Names.named(Config.PREVENT_PERIODIC_SHUTDOWN_AT_SEED_NODE)));
                if (!preventPeriodicShutdownAtSeedNode) {
                    startShutDownInterval(DaoNodeMain.this);
                }
                UserThread.runAfter(() -> setupConnectionLossCheck(), 60);
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                // Do nothing
            }

            @Override
            public void onRequestCustomBridges() {
                // Do nothing
            }
        });
    }

    private void setupConnectionLossCheck() {
        // For dev testing (usually on BTC_REGTEST) we don't want to get the seed shut
        // down as it is normal that the seed is the only actively running node.
        if (Config.baseCurrencyNetwork() == BaseCurrencyNetwork.BTC_REGTEST) {
            return;
        }

        if (checkConnectionLossTime != null) {
            return;
        }

        checkConnectionLossTime = UserThread.runPeriodically(() -> {
            if (injector.getInstance(PeerManager.class).getNumAllConnectionsLostEvents() > 1) {
                // We set a flag to clear tor cache files at re-start. We cannot clear it now as Tor is used and
                // that can cause problems.
                injector.getInstance(User.class).getCookie().putAsBoolean(CookieKey.CLEAN_TOR_DIR_AT_RESTART, true);
                shutDown(this);
            }
        }, CHECK_CONNECTION_LOSS_SEC);

    }

    private void gracefulShutDown() {
        gracefulShutDown(() -> {
        });
    }

    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        daoNode.shutDown();
        super.gracefulShutDown(resultHandler);
    }
}
