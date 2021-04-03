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

import bisq.core.account.sign.SignedWitnessService;
import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.filter.FilterManager;
import bisq.core.trade.statistics.TradeStatisticsManager;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.P2PServiceListener;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.storage.P2PDataStorage;

import bisq.common.config.Config;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import javax.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.ArrayList;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AppSetupWithP2P extends AppSetup {
    protected final P2PService p2PService;
    protected final AccountAgeWitnessService accountAgeWitnessService;
    private final SignedWitnessService signedWitnessService;
    protected final FilterManager filterManager;
    private final P2PDataStorage p2PDataStorage;
    private final PeerManager peerManager;
    protected final TradeStatisticsManager tradeStatisticsManager;
    protected ArrayList<PersistedDataHost> persistedDataHosts;
    protected BooleanProperty p2pNetWorkReady;

    @Inject
    public AppSetupWithP2P(P2PService p2PService,
                           P2PDataStorage p2PDataStorage,
                           PeerManager peerManager,
                           TradeStatisticsManager tradeStatisticsManager,
                           AccountAgeWitnessService accountAgeWitnessService,
                           SignedWitnessService signedWitnessService,
                           FilterManager filterManager,
                           Config config) {
        super(config);
        this.p2PService = p2PService;
        this.p2PDataStorage = p2PDataStorage;
        this.peerManager = peerManager;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.signedWitnessService = signedWitnessService;
        this.filterManager = filterManager;
        this.persistedDataHosts = new ArrayList<>();
    }

    @Override
    public void initPersistedDataHosts() {
        persistedDataHosts.add(p2PDataStorage);
        persistedDataHosts.add(peerManager);

        // we apply at startup the reading of persisted data but don't want to get it triggered in the constructor
        persistedDataHosts.forEach(e -> {
            try {
                e.readPersisted(() -> {
                });
            } catch (Throwable e1) {
                log.error("readPersisted error", e1);
            }
        });
    }

    @Override
    protected void initBasicServices() {
        String postFix = "_" + config.baseCurrencyNetwork.name();
        p2PDataStorage.readFromResources(postFix, this::startInitP2PNetwork);
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
                if (connection.getConnectionState().isSeedNode() &&
                        closeConnectionReason == CloseConnectionReason.RULE_VIOLATION) {
                    log.warn("RULE_VIOLATION onDisconnect closeConnectionReason={}. connection={}",
                            closeConnectionReason, connection);
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
            public void onDataReceived() {
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
            public void onUpdatedDataReceived() {
                log.info("onUpdatedDataReceived");
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
        PersistenceManager.onAllServicesInitialized();

        p2PService.onAllServicesInitialized();

        tradeStatisticsManager.onAllServicesInitialized();

        accountAgeWitnessService.onAllServicesInitialized();
        signedWitnessService.onAllServicesInitialized();

        filterManager.onAllServicesInitialized();
    }
}
