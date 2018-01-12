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

package io.bisq.core.app;

import io.bisq.common.crypto.KeyRing;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.core.filter.FilterManager;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.trade.statistics.TradeStatisticsManager;
import io.bisq.network.crypto.EncryptionService;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.P2PServiceListener;
import io.bisq.network.p2p.network.CloseConnectionReason;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.ConnectionListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import java.util.ArrayList;

@Slf4j
public class AppSetupWithP2P extends AppSetup {
    protected final P2PService p2PService;
    protected final AccountAgeWitnessService accountAgeWitnessService;
    protected final FilterManager filterManager;
    protected BooleanProperty p2pNetWorkReady;
    protected final TradeStatisticsManager tradeStatisticsManager;

    @Inject
    public AppSetupWithP2P(EncryptionService encryptionService,
                           KeyRing keyRing,
                           P2PService p2PService,
                           TradeStatisticsManager tradeStatisticsManager,
                           AccountAgeWitnessService accountAgeWitnessService,
                           FilterManager filterManager) {
        super(encryptionService,keyRing);
        this.p2PService = p2PService;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.filterManager = filterManager;
    }

    @Override
    public void initPersistedDataHosts() {
        ArrayList<PersistedDataHost> persistedDataHosts = new ArrayList<>();
        persistedDataHosts.add(p2PService);

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

    @Override
    protected void initBasicServices() {
        SetupUtils.readFromResources(p2PService.getP2PDataStorage()).addListener((observable, oldValue, newValue) -> {
            if (newValue)
                startInitP2PNetwork();
        });
    }

    private void startInitP2PNetwork() {
        p2pNetWorkReady = initP2PNetwork();
        p2pNetWorkReady.addListener((observable, oldValue, newValue) -> {
            if (newValue)
                onBasicServicesInitialized();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BooleanProperty initP2PNetwork() {
        log.info("initP2PNetwork");
        p2PService.getNetworkNode().addConnectionListener(new ConnectionListener() {
            @Override
            public void onConnection(Connection connection) {
            }

            @Override
            public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
                // We only check at seed nodes as they are running the latest version
                // Other disconnects might be caused by peers running an older version
                if (connection.getPeerType() == Connection.PeerType.SEED_NODE &&
                        closeConnectionReason == CloseConnectionReason.RULE_VIOLATION) {
                    log.warn("RULE_VIOLATION onDisconnect closeConnectionReason=" + closeConnectionReason);
                    log.warn("RULE_VIOLATION onDisconnect connection=" + connection);
                }
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });

        final BooleanProperty p2pNetworkInitialized = new SimpleBooleanProperty();
        p2PService.start(new P2PServiceListener() {
            @Override
            public void onTorNodeReady() {
            }

            @Override
            public void onHiddenServicePublished() {
                log.info("onHiddenServicePublished");
            }

            @Override
            public void onRequestingDataCompleted() {
                log.info("onRequestingDataCompleted");
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoSeedNodeAvailable() {
                log.info("onNoSeedNodeAvailable");
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoPeersAvailable() {
                log.info("onNoPeersAvailable");
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onBootstrapComplete() {
                log.info("onBootstrapComplete");
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                log.error(throwable.toString());
            }

            @Override
            public void onRequestCustomBridges() {

            }
        });

        return p2pNetworkInitialized;
    }

    protected void onBasicServicesInitialized() {
        log.info("onBasicServicesInitialized");

        p2PService.onAllServicesInitialized();

        tradeStatisticsManager.onAllServicesInitialized();

        accountAgeWitnessService.onAllServicesInitialized();

        filterManager.onAllServicesInitialized();
    }
}
