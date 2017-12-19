/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.monitor;

import io.bisq.common.app.Version;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.app.SetupUtils;
import io.bisq.core.btc.wallet.WalletsSetup;
import io.bisq.monitor.metrics.p2p.MonitorP2PService;
import io.bisq.network.crypto.EncryptionService;
import io.bisq.network.p2p.network.SetupListener;
import io.bisq.network.p2p.peers.PeerManager;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.ArrayList;

@Slf4j
public class MonitorAppSetup {
    private MonitorP2PService seedNodeMonitorP2PService;
    private final WalletsSetup walletsSetup;
    private PeerManager peerManager;
    private final KeyRing keyRing;
    private final EncryptionService encryptionService;

    @Inject
    public MonitorAppSetup(MonitorP2PService seedNodeMonitorP2PService,
                           WalletsSetup walletsSetup,
                           PeerManager peerManager,
                           KeyRing keyRing,
                           EncryptionService encryptionService) {
        this.seedNodeMonitorP2PService = seedNodeMonitorP2PService;
        this.walletsSetup = walletsSetup;
        this.peerManager = peerManager;
        this.keyRing = keyRing;
        this.encryptionService = encryptionService;
        Version.setBaseCryptoNetworkId(BisqEnvironment.getBaseCurrencyNetwork().ordinal());
        Version.printVersion();
    }

    public void start() {
        SetupUtils.checkCryptoSetup(keyRing, encryptionService, () -> {
            initPersistedDataHosts();
            initBasicServices();
        }, throwable -> {
            log.error(throwable.getMessage());
            throwable.printStackTrace();
            System.exit(1);
        });
    }

    public void initPersistedDataHosts() {
        ArrayList<PersistedDataHost> persistedDataHosts = new ArrayList<>();
        persistedDataHosts.add(seedNodeMonitorP2PService);
        persistedDataHosts.add(peerManager);

        // we apply at startup the reading of persisted data but don't want to get it triggered in the constructor
        persistedDataHosts.stream().forEach(e -> {
            try {
                log.info("call readPersisted at " + e.getClass().getSimpleName());
                e.readPersisted();
            } catch (Throwable e1) {
                log.error("readPersisted error", e1);
            }
        });
    }

    protected void initBasicServices() {
        SetupUtils.readFromResources(seedNodeMonitorP2PService.getP2PDataStorage()).addListener((observable, oldValue, newValue) -> {
            if (newValue) {
                seedNodeMonitorP2PService.start(new SetupListener() {


                    @Override
                    public void onTorNodeReady() {
                        walletsSetup.initialize(null,
                                () -> log.info("walletsSetup completed"),
                                throwable -> log.error(throwable.toString()));
                    }

                    @Override
                    public void onHiddenServicePublished() {
                    }

                    @Override
                    public void onSetupFailed(Throwable throwable) {
                    }

                    @Override
                    public void onRequestCustomBridges() {
                    }
                });

            }
        });
    }
}
