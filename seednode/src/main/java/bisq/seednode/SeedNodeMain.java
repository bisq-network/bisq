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

package bisq.seednode;

import bisq.core.app.TorSetup;
import bisq.core.app.misc.ExecutableForAppWithP2p;
import bisq.core.app.misc.ModuleForAppWithP2p;
import bisq.core.dao.state.DaoStateSnapshotService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.P2PServiceListener;
import bisq.network.p2p.peers.PeerManager;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.app.DevEnv;
import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;
import bisq.common.handlers.ResultHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SeedNodeMain extends ExecutableForAppWithP2p {
    private static final long CHECK_CONNECTION_LOSS_SEC = 30;
    private static final String VERSION = "1.5.3";
    private SeedNode seedNode;
    private Timer checkConnectionLossTime;

    public SeedNodeMain() {
        super("Bisq Seednode", "bisq-seednode", "bisq_seednode", VERSION);
    }

    public static void main(String[] args) {
        System.out.println("SeedNode.VERSION: " + VERSION);
        new SeedNodeMain().execute(args);
    }

    @Override
    protected void doExecute() {
        super.doExecute();

        checkMemory(config, this);

        keepRunning();
    }

    @Override
    protected void addCapabilities() {
        Capabilities.app.addAll(Capability.SEED_NODE);
    }

    @Override
    protected void launchApplication() {
        UserThread.execute(() -> {
            try {
                seedNode = new SeedNode();
                UserThread.execute(this::onApplicationLaunched);
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

        seedNode.setInjector(injector);

        if (DevEnv.isDaoActivated()) {
            injector.getInstance(DaoStateSnapshotService.class).setDaoRequiresRestartHandler(() -> gracefulShutDown(() -> {
            }));
        }
    }

    @Override
    protected void startApplication() {
        seedNode.startApplication();

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
                startShutDownInterval(SeedNodeMain.this);
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
                // Removing cache files help in case the node got flagged from Tor's dos protection
                injector.getInstance(TorSetup.class).cleanupTorFiles(() -> {
                    log.info("Tor directory reset");
                    shutDown(this);
                }, log::error);
            }
        }, CHECK_CONNECTION_LOSS_SEC);

    }

    @Override
    public void gracefulShutDown(ResultHandler resultHandler) {
        seedNode.shutDown();
        super.gracefulShutDown(resultHandler);
    }
}
