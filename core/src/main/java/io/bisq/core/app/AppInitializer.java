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

package io.bisq.core.app;

import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.KeyRing;
import io.bisq.core.btc.wallet.WalletsManager;
import io.bisq.core.btc.wallet.WalletsSetup;
import io.bisq.core.dao.DaoManager;
import io.bisq.core.trade.statistics.TradeStatisticsManager;
import io.bisq.core.user.Preferences;
import io.bisq.network.crypto.EncryptionService;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.P2PServiceListener;
import io.bisq.network.p2p.network.CloseConnectionReason;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.ConnectionListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import lombok.extern.slf4j.Slf4j;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

@Slf4j
public class AppInitializer {
    private final WalletsManager walletsManager;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final Preferences preferences;
    private final DaoManager daoManager;
    private final EncryptionService encryptionService;
    private final KeyRing keyRing;

    private MonadicBinding<Boolean> allServicesDone;
    @SuppressWarnings("unused")
    private Subscription priceFeedAllLoadedSubscription;
    private BooleanProperty p2pNetWorkReady;
    private final BooleanProperty walletInitialized = new SimpleBooleanProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public AppInitializer(BisqEnvironment bisqEnvironment,
                          EncryptionService encryptionService,
                          KeyRing keyRing,
                          P2PService p2PService,
                          WalletsManager walletsManager,
                          WalletsSetup walletsSetup,
                          DaoManager daoManager,
                          TradeStatisticsManager tradeStatisticsManager,
                          Preferences preferences) {
        this.walletsManager = walletsManager;
        this.walletsSetup = walletsSetup;
        this.p2PService = p2PService;
        // we need to reference it so the seed node stores tradeStatistics
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.preferences = preferences;
        this.daoManager = daoManager;
        this.encryptionService = encryptionService;
        this.keyRing = keyRing;

        preferences.init();
        preferences.setUseTorForBitcoinJ(false);

        Version.setBtcNetworkId(bisqEnvironment.getBitcoinNetwork().ordinal());
        Version.printVersion();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        SetupUtils.checkCryptoSetup(keyRing, encryptionService, this::startBasicServices, throwable -> {
            log.error(throwable.getMessage());
            throwable.printStackTrace();
            System.exit(1);
        });
    }

    private void startBasicServices() {
        Timer startupTimeout = UserThread.runAfter(() -> {
            log.error("Could nto startup after 4 minutes. We shut down.");
            System.exit(0);
        }, 4, TimeUnit.MINUTES);

        p2pNetWorkReady = initP2PNetwork();

        // We only init wallet service here if not using Tor for bitcoinj.
        // When using Tor, wallet init must be deferred until Tor is ready.
        if (!preferences.getUseTorForBitcoinJ())
            initWalletService();

        // need to store it to not get garbage collected
        allServicesDone = EasyBind.combine(walletInitialized, p2pNetWorkReady, (a, b) -> a && b);
        allServicesDone.subscribe((observable, oldValue, newValue) -> {
            if (newValue) {
                startupTimeout.stop();
                onBasicServicesInitialized();
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BooleanProperty initP2PNetwork() {
        log.info("initP2PNetwork");
        BooleanProperty hiddenServicePublished = new SimpleBooleanProperty();
        BooleanProperty initialP2PNetworkDataReceived = new SimpleBooleanProperty();

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
                if (preferences.getUseTorForBitcoinJ())
                    initWalletService();
            }

            @Override
            public void onHiddenServicePublished() {
                hiddenServicePublished.set(true);
            }

            @Override
            public void onRequestingDataCompleted() {
                initialP2PNetworkDataReceived.set(true);
                log.info("p2pNetworkInitialized");
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoSeedNodeAvailable() {
                log.info("p2pNetworkInitialized");
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoPeersAvailable() {
                log.info("p2pNetworkInitialized");
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
        });

        return p2pNetworkInitialized;
    }

    private void initWalletService() {
        walletsSetup.initialize(null,
                () -> {
                    // We only check one as we apply encryption to all or none
                    if (walletsManager.areWalletsEncrypted()) {
                        log.error("Wallet is encrypted. We shut down.");
                        System.exit(0);
                    } else {
                        log.info("walletInitialized");
                        walletInitialized.set(true);
                    }
                },
                e -> {
                    log.error(e.toString());
                    e.printStackTrace();
                    System.exit(1);
                });
    }

    private void onBasicServicesInitialized() {
        log.info("onBasicServicesInitialized");
        p2PService.onAllServicesInitialized();
        daoManager.onAllServicesInitialized(log::error);
    }
}
