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

import bisq.core.app.misc.ExecutableForAppWithP2p;
import bisq.core.app.misc.ModuleForAppWithP2p;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.P2PServiceListener;

import bisq.common.UserThread;
import bisq.common.app.AppModule;
import bisq.common.app.Capabilities;
import bisq.common.app.Capability;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SeedNodeMain extends ExecutableForAppWithP2p {
    private static final String VERSION = "1.3.9";
    private SeedNode seedNode;

    public SeedNodeMain() {
        super("Bisq Seednode", "bisq-seednode", "bisq_seednode", VERSION);
    }

    public static void main(String[] args) throws Exception {
        log.info("SeedNode.VERSION: " + VERSION);
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
}
