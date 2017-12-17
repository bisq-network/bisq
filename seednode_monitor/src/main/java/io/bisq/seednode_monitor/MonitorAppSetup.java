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

package io.bisq.seednode_monitor;

import io.bisq.common.app.Version;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.app.SetupUtils;
import io.bisq.network.crypto.EncryptionService;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.ArrayList;

@Slf4j
public class MonitorAppSetup {
    private MonitorP2PService seedNodeMonitorP2PService;
    private final KeyRing keyRing;
    private final EncryptionService encryptionService;

    @Inject
    public MonitorAppSetup(MonitorP2PService seedNodeMonitorP2PService, KeyRing keyRing, EncryptionService encryptionService) {
        this.seedNodeMonitorP2PService = seedNodeMonitorP2PService;
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
            if (newValue)
                startInitP2PNetwork();
        });
    }

    private void startInitP2PNetwork() {
        seedNodeMonitorP2PService.start();
    }
}
