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

package bisq.desktop.app;

import bisq.core.arbitration.ArbitratorManager;
import bisq.core.arbitration.DisputeManager;
import bisq.core.btc.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletsSetup;
import bisq.core.filter.FilterManager;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.trade.TradeManager;
import bisq.core.trade.statistics.TradeStatisticsManager;

import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.P2PServiceListener;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;

import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;

import com.google.inject.Injector;

import javax.inject.Inject;

import org.fxmisc.easybind.EasyBind;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DesktopAppSetup extends AppSetup {


    private final DisputeManager disputeManager;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final ArbitratorManager arbitratorManager;
    private final FeeService feeService;
    private final PriceFeedService priceFeedService;
    private final BtcWalletService btcWalletService;
    private final WalletsSetup walletsSetup;
    private final P2PService p2PService;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final FilterManager filterManager;

    protected BooleanProperty p2pNetWorkReady;


    private BooleanProperty walletInitialized;
    private ObjectProperty<Throwable> walletServiceException;

    @Inject
    public DesktopAppSetup(EncryptionService encryptionService,
                           KeyRing keyRing,
                           P2PService p2PService,
                           TradeStatisticsManager tradeStatisticsManager,
                           AccountAgeWitnessService accountAgeWitnessService,
                           FilterManager filterManager,
                           Injector injector,
                           ArbitratorManager arbitratorManager,
                           BtcWalletService btcWalletService,
                           FeeService feeService,
                           PriceFeedService priceFeedService,
                           OpenOfferManager openOfferManager,
                           TradeManager tradeManager,
                           DisputeManager disputeManager,
                           WalletsSetup walletsSetup
    ) {
        super(encryptionService, injector, keyRing, p2PService);
        this.p2PService = p2PService;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.filterManager = filterManager;
        this.disputeManager = disputeManager;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.arbitratorManager = arbitratorManager;
        this.feeService = feeService;
        this.priceFeedService = priceFeedService;
        this.btcWalletService = btcWalletService;
        this.walletsSetup = walletsSetup;

        walletInitialized = new SimpleBooleanProperty();
        walletServiceException = new SimpleObjectProperty<>();
    }

    @Override
    protected CompletableFuture<Void> doInitBasicServices() {
        return readFromResources().thenCompose(i -> {
            final CompletableFuture<Void> completableFuture = new CompletableFuture<>();
            startInitP2PNetwork();
            p2pNetWorkReady.addListener((observable1, oldValue1, newValue1) -> {
                if (!newValue1) return;
                initWalletService();
            });
            EasyBind.combine(walletInitialized, p2pNetWorkReady,
                    (a, b) -> {
                        log.debug("\nwalletInitialized={}\n" +
                                        "p2pNetWorkReady={}",
                                a, b);
                        return a && b;
                    }).addListener((observable1, oldValue1, newValue1) -> {
                if (newValue1)
                    completableFuture.complete(null);
            });
            return completableFuture;
        }).thenRun(this::onBasicServicesInitialized);
    }

    private void initWalletService() {
        walletsSetup.initialize(null).thenRun(() -> {
            log.debug("walletsSetup.onInitialized");
            walletInitialized.set(true);
        }).exceptionally(e -> {
            walletServiceException.set(e);
            return null;
        });
    }

    @Override
    protected void onBasicServicesInitialized() {
        log.debug("onBasicServicesInitialized");
        PaymentMethod.onAllServicesInitialized();
        p2PService.onAllServicesInitialized();
        tradeStatisticsManager.onAllServicesInitialized();
        accountAgeWitnessService.onAllServicesInitialized();
        filterManager.onAllServicesInitialized();
        disputeManager.onAllServicesInitialized();
        tradeManager.onAllServicesInitialized();
        openOfferManager.onAllServicesInitialized();
        arbitratorManager.onAllServicesInitialized();
        feeService.onAllServicesInitialized();
        priceFeedService.setCurrencyCodeOnInit();

        swapPendingOfferFundingEntries();
        checkIfOpenOffersMatchTradeProtocolVersion();
    }

    private void swapPendingOfferFundingEntries() {
        tradeManager.getAddressEntriesForAvailableBalanceStream()
                .filter(addressEntry -> addressEntry.getOfferId() != null)
                .forEach(addressEntry -> {
                    log.debug("swapPendingOfferFundingEntries, offerId={}, OFFER_FUNDING", addressEntry.getOfferId());
                    btcWalletService.swapTradeEntryToAvailableEntry(addressEntry.getOfferId(), AddressEntry.Context.OFFER_FUNDING);
                });
    }

    private void checkIfOpenOffersMatchTradeProtocolVersion() {
        List<OpenOffer> outDatedOffers = openOfferManager.getObservableList()
                .stream()
                .filter(e -> e.getOffer().getProtocolVersion() != Version.TRADE_PROTOCOL_VERSION)
                .collect(Collectors.toList());
        if (!outDatedOffers.isEmpty()) {
            openOfferManager.removeOpenOffers(outDatedOffers, null);
        }
    }

    private void startInitP2PNetwork() {
        p2pNetWorkReady = initP2PNetwork();
    }

    private BooleanProperty initP2PNetwork() {
        log.debug("initP2PNetwork");
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
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            p2PService.start(new P2PServiceListener() {
                @Override
                public void onTorNodeReady() {
                    log.debug("onTorNodeReady");
                }

                @Override
                public void onHiddenServicePublished() {
                    log.debug("onHiddenServicePublished");
                }

                @Override
                public void onDataReceived() {
                    log.debug("onRequestingDataCompleted");
                    p2pNetworkInitialized.set(true);
                }

                @Override
                public void onNoSeedNodeAvailable() {
                    log.debug("onNoSeedNodeAvailable");
                    p2pNetworkInitialized.set(true);
                }

                @Override
                public void onNoPeersAvailable() {
                    log.debug("onNoPeersAvailable");
                    p2pNetworkInitialized.set(true);
                }

                @Override
                public void onUpdatedDataReceived() {
                }

                @Override
                public void onSetupFailed(Throwable throwable) {
                    log.error(throwable.toString());
                }

                @Override
                public void onRequestCustomBridges() {
                    log.debug("onRequestCustomBridges");
                }
            });
        }).start();
        return p2pNetworkInitialized;
    }
}
