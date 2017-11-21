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

package io.bisq.gui.main;

import com.google.common.net.InetAddresses;
import com.google.inject.Inject;
import io.bisq.common.Clock;
import io.bisq.common.GlobalSettings;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.DevEnv;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.SealedAndSigned;
import io.bisq.common.locale.CurrencyUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.alert.Alert;
import io.bisq.core.alert.AlertManager;
import io.bisq.core.alert.PrivateNotificationManager;
import io.bisq.core.alert.PrivateNotificationPayload;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.app.SetupUtils;
import io.bisq.core.arbitration.ArbitratorManager;
import io.bisq.core.arbitration.Dispute;
import io.bisq.core.arbitration.DisputeManager;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.listeners.BalanceListener;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.WalletsManager;
import io.bisq.core.btc.wallet.WalletsSetup;
import io.bisq.core.dao.DaoManager;
import io.bisq.core.filter.FilterManager;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.CryptoCurrencyAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.PerfectMoneyAccount;
import io.bisq.core.payment.payload.PaymentMethod;
import io.bisq.core.provider.fee.FeeService;
import io.bisq.core.provider.price.MarketPrice;
import io.bisq.core.provider.price.PriceFeedService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.core.trade.failed.FailedTradesManager;
import io.bisq.core.trade.statistics.TradeStatisticsManager;
import io.bisq.core.user.DontShowAgainLookup;
import io.bisq.core.user.Preferences;
import io.bisq.core.user.User;
import io.bisq.gui.common.model.ViewModel;
import io.bisq.gui.components.BalanceWithConfirmationTextField;
import io.bisq.gui.components.TxIdTextField;
import io.bisq.gui.main.overlays.notifications.NotificationCenter;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.main.overlays.windows.DisplayAlertMessageWindow;
import io.bisq.gui.main.overlays.windows.TacWindow;
import io.bisq.gui.main.overlays.windows.TorNetworkSettingsWindow;
import io.bisq.gui.main.overlays.windows.WalletPasswordWindow;
import io.bisq.gui.main.overlays.windows.downloadupdate.DisplayUpdateDownloadWindow;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.GUIUtil;
import io.bisq.network.crypto.DecryptedDataTuple;
import io.bisq.network.crypto.EncryptionService;
import io.bisq.network.p2p.BootstrapListener;
import io.bisq.network.p2p.P2PService;
import io.bisq.network.p2p.P2PServiceListener;
import io.bisq.network.p2p.network.CloseConnectionReason;
import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.ConnectionListener;
import io.bisq.network.p2p.peers.keepalive.messages.Ping;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.ChainFileLockedException;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.Security;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class MainViewModel implements ViewModel {
    private static final long STARTUP_TIMEOUT_MINUTES = 4;

    private final WalletsManager walletsManager;
    private final WalletsSetup walletsSetup;
    private final BtcWalletService btcWalletService;
    private final ArbitratorManager arbitratorManager;
    private final P2PService p2PService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final DisputeManager disputeManager;
    final Preferences preferences;
    private final AlertManager alertManager;
    private final PrivateNotificationManager privateNotificationManager;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private final FilterManager filterManager;
    private final WalletPasswordWindow walletPasswordWindow;
    private final TradeStatisticsManager tradeStatisticsManager;
    private final NotificationCenter notificationCenter;
    private final TacWindow tacWindow;
    private final Clock clock;
    private final FeeService feeService;
    private final DaoManager daoManager;
    private final EncryptionService encryptionService;
    private final KeyRing keyRing;
    private final BisqEnvironment bisqEnvironment;
    private final FailedTradesManager failedTradesManager;
    private final ClosedTradableManager closedTradableManager;
    private final AccountAgeWitnessService accountAgeWitnessService;
    private final BSFormatter formatter;

    // BTC network
    final StringProperty btcInfo = new SimpleStringProperty(Res.get("mainView.footer.btcInfo.initializing"));
    @SuppressWarnings("ConstantConditions")
    final DoubleProperty btcSyncProgress = new SimpleDoubleProperty(DevEnv.STRESS_TEST_MODE ? 0 : -1);
    final StringProperty walletServiceErrorMsg = new SimpleStringProperty();
    final StringProperty btcSplashSyncIconId = new SimpleStringProperty();
    private final StringProperty marketPriceCurrencyCode = new SimpleStringProperty("");
    final ObjectProperty<PriceFeedComboBoxItem> selectedPriceFeedComboBoxItemProperty = new SimpleObjectProperty<>();
    final BooleanProperty isFiatCurrencyPriceFeedSelected = new SimpleBooleanProperty(true);
    final BooleanProperty isCryptoCurrencyPriceFeedSelected = new SimpleBooleanProperty(false);
    final BooleanProperty isExternallyProvidedPrice = new SimpleBooleanProperty(true);
    final BooleanProperty isPriceAvailable = new SimpleBooleanProperty(false);
    final IntegerProperty marketPriceUpdated = new SimpleIntegerProperty(0);
    final StringProperty availableBalance = new SimpleStringProperty();
    final StringProperty reservedBalance = new SimpleStringProperty();
    final StringProperty lockedBalance = new SimpleStringProperty();
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<String> btcInfoBinding;

    private final StringProperty marketPrice = new SimpleStringProperty(Res.get("shared.na"));

    // P2P network
    final StringProperty p2PNetworkInfo = new SimpleStringProperty();
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<String> p2PNetworkInfoBinding;
    final BooleanProperty splashP2PNetworkAnimationVisible = new SimpleBooleanProperty(true);
    final StringProperty p2pNetworkWarnMsg = new SimpleStringProperty();
    final StringProperty p2PNetworkIconId = new SimpleStringProperty();
    final BooleanProperty bootstrapComplete = new SimpleBooleanProperty();
    final BooleanProperty showAppScreen = new SimpleBooleanProperty();
    final StringProperty numPendingTradesAsString = new SimpleStringProperty();
    final BooleanProperty showPendingTradesNotification = new SimpleBooleanProperty();
    final StringProperty numOpenDisputesAsString = new SimpleStringProperty();
    final BooleanProperty showOpenDisputesNotification = new SimpleBooleanProperty();
    private final BooleanProperty isSplashScreenRemoved = new SimpleBooleanProperty();
    private final String btcNetworkAsString;
    final StringProperty p2pNetworkLabelId = new SimpleStringProperty("footer-pane");

    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Boolean> allServicesDone, tradesAndUIReady;
    final PriceFeedService priceFeedService;
    private final User user;
    private int numBtcPeers = 0;
    private Timer checkNumberOfBtcPeersTimer;
    private Timer checkNumberOfP2pNetworkPeersTimer;
    private final Map<String, Subscription> disputeIsClosedSubscriptionsMap = new HashMap<>();
    final ObservableList<PriceFeedComboBoxItem> priceFeedComboBoxItems = FXCollections.observableArrayList();
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<String> marketPriceBinding;
    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private Subscription priceFeedAllLoadedSubscription;
    private TorNetworkSettingsWindow torNetworkSettingsWindow;
    private BooleanProperty p2pNetWorkReady;
    private final BooleanProperty walletInitialized = new SimpleBooleanProperty();
    private boolean allBasicServicesInitialized;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public MainViewModel(WalletsManager walletsManager, WalletsSetup walletsSetup,
                         BtcWalletService btcWalletService, PriceFeedService priceFeedService,
                         ArbitratorManager arbitratorManager, P2PService p2PService, TradeManager tradeManager,
                         OpenOfferManager openOfferManager, DisputeManager disputeManager, Preferences preferences,
                         User user, AlertManager alertManager, PrivateNotificationManager privateNotificationManager,
                         FilterManager filterManager, WalletPasswordWindow walletPasswordWindow, TradeStatisticsManager tradeStatisticsManager,
                         NotificationCenter notificationCenter, TacWindow tacWindow, Clock clock, FeeService feeService,
                         DaoManager daoManager, EncryptionService encryptionService,
                         KeyRing keyRing, BisqEnvironment bisqEnvironment, FailedTradesManager failedTradesManager,
                         ClosedTradableManager closedTradableManager, AccountAgeWitnessService accountAgeWitnessService,
                         BSFormatter formatter) {
        this.walletsManager = walletsManager;
        this.walletsSetup = walletsSetup;
        this.btcWalletService = btcWalletService;
        this.priceFeedService = priceFeedService;
        this.user = user;
        this.arbitratorManager = arbitratorManager;
        this.p2PService = p2PService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.disputeManager = disputeManager;
        this.preferences = preferences;
        this.alertManager = alertManager;
        this.privateNotificationManager = privateNotificationManager;
        this.filterManager = filterManager; // Reference so it's initialized and eventListener gets registered
        this.walletPasswordWindow = walletPasswordWindow;
        this.tradeStatisticsManager = tradeStatisticsManager;
        this.notificationCenter = notificationCenter;
        this.tacWindow = tacWindow;
        this.clock = clock;
        this.feeService = feeService;
        this.daoManager = daoManager;
        this.encryptionService = encryptionService;
        this.keyRing = keyRing;
        this.bisqEnvironment = bisqEnvironment;
        this.failedTradesManager = failedTradesManager;
        this.closedTradableManager = closedTradableManager;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.formatter = formatter;

        btcNetworkAsString = Res.get(BisqEnvironment.getBaseCurrencyNetwork().name()) +
                (preferences.getUseTorForBitcoinJ() ? (" " + Res.get("mainView.footer.usingTor")) : "");

        TxIdTextField.setPreferences(preferences);

        // TODO
        TxIdTextField.setWalletService(btcWalletService);
        BalanceWithConfirmationTextField.setWalletService(btcWalletService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        //noinspection ConstantConditions,ConstantConditions
        bisqEnvironment.saveBaseCryptoNetwork(BisqEnvironment.getBaseCurrencyNetwork());

        //noinspection ConstantConditions,ConstantConditions,PointlessBooleanExpression
        if (!preferences.isTacAccepted() && !DevEnv.DEV_MODE) {
            UserThread.runAfter(() -> {
                tacWindow.onAction(() -> {
                    preferences.setTacAccepted(true);
                    checkIfLocalHostNodeIsRunning();
                }).show();
            }, 1);
        } else {
            checkIfLocalHostNodeIsRunning();
        }
    }

    private void readMapsFromResources() {
        SetupUtils.readFromResources(p2PService).addListener((observable, oldValue, newValue) -> {
            if (newValue)
                startBasicServices();
        });

        // TODO can be removed in jdk 9
        checkCryptoSetup();
    }

    private void startBasicServices() {
        log.info("startBasicServices");

        ChangeListener<Boolean> walletInitializedListener = (observable, oldValue, newValue) -> {
            if (newValue && !p2pNetWorkReady.get())
                showTorNetworkSettingsWindow();
        };

        Timer startupTimeout = UserThread.runAfter(() -> {
            log.warn("startupTimeout called");
            if (walletsManager.areWalletsEncrypted())
                walletInitialized.addListener(walletInitializedListener);
            else
                showTorNetworkSettingsWindow();
        }, STARTUP_TIMEOUT_MINUTES, TimeUnit.MINUTES);

        p2pNetWorkReady = initP2PNetwork();

        // We only init wallet service here if not using Tor for bitcoinj.
        // When using Tor, wallet init must be deferred until Tor is ready.
        if (!preferences.getUseTorForBitcoinJ() || bisqEnvironment.isBitcoinLocalhostNodeRunning())
            initWalletService();

        // need to store it to not get garbage collected
        allServicesDone = EasyBind.combine(walletInitialized, p2pNetWorkReady,
                (a, b) -> {
                    log.debug("\nwalletInitialized={}\n" +
                                    "p2pNetWorkReady={}",
                            a, b);
                    return a && b;
                });
        allServicesDone.subscribe((observable, oldValue, newValue) -> {
            if (newValue) {
                startupTimeout.stop();
                walletInitialized.removeListener(walletInitializedListener);
                onBasicServicesInitialized();
                if (torNetworkSettingsWindow != null)
                    torNetworkSettingsWindow.hide();
            }
        });
    }

    private void showTorNetworkSettingsWindow() {
        MainView.blur();
        torNetworkSettingsWindow = new TorNetworkSettingsWindow(preferences).useShutDownButton();
        torNetworkSettingsWindow.show();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BooleanProperty initP2PNetwork() {
        log.info("initP2PNetwork");

        StringProperty bootstrapState = new SimpleStringProperty();
        StringProperty bootstrapWarning = new SimpleStringProperty();
        BooleanProperty hiddenServicePublished = new SimpleBooleanProperty();
        BooleanProperty initialP2PNetworkDataReceived = new SimpleBooleanProperty();

        p2PNetworkInfoBinding = EasyBind.combine(bootstrapState, bootstrapWarning, p2PService.getNumConnectedPeers(), hiddenServicePublished, initialP2PNetworkDataReceived,
                (state, warning, numPeers, hiddenService, dataReceived) -> {
                    String result = "";
                    int peers = (int) numPeers;
                    if (warning != null && peers == 0) {
                        result = warning;
                    } else {
                        String p2pInfo = Res.get("mainView.footer.p2pInfo", numPeers);
                        if (dataReceived && hiddenService) {
                            result = p2pInfo;
                        } else if (peers == 0)
                            result = state;
                        else
                            result = state + " / " + p2pInfo;
                    }
                    return result;
                });
        p2PNetworkInfoBinding.subscribe((observable, oldValue, newValue) -> {
            p2PNetworkInfo.set(newValue);
        });

        bootstrapState.set(Res.get("mainView.bootstrapState.connectionToTorNetwork"));

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
                log.debug("onTorNodeReady");
                bootstrapState.set(Res.get("mainView.bootstrapState.torNodeCreated"));
                p2PNetworkIconId.set("image-connection-tor");

                if (preferences.getUseTorForBitcoinJ())
                    initWalletService();

                // We want to get early connected to the price relay so we call it already now
                long ts = new Date().getTime();
                final boolean[] logged = {false};
                priceFeedService.setCurrencyCodeOnInit();
                priceFeedService.requestPriceFeed(price -> {
                            if (!logged[0]) {
                                log.info("We received data from the price relay after {} ms.",
                                        (new Date().getTime() - ts));
                                logged[0] = true;
                            }
                        },
                        (errorMessage, throwable) -> log.error("requestPriceFeed failed:" + errorMessage));
            }

            @Override
            public void onHiddenServicePublished() {
                log.debug("onHiddenServicePublished");
                hiddenServicePublished.set(true);
                bootstrapState.set(Res.get("mainView.bootstrapState.hiddenServicePublished"));
            }

            @Override
            public void onRequestingDataCompleted() {
                log.debug("onRequestingDataCompleted");
                initialP2PNetworkDataReceived.set(true);
                bootstrapState.set(Res.get("mainView.bootstrapState.initialDataReceived"));
                splashP2PNetworkAnimationVisible.set(false);
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoSeedNodeAvailable() {
                log.warn("onNoSeedNodeAvailable");
                if (p2PService.getNumConnectedPeers().get() == 0)
                    bootstrapWarning.set(Res.get("mainView.bootstrapWarning.noSeedNodesAvailable"));
                else
                    bootstrapWarning.set(null);

                splashP2PNetworkAnimationVisible.set(false);
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoPeersAvailable() {
                log.warn("onNoPeersAvailable");
                if (p2PService.getNumConnectedPeers().get() == 0) {
                    p2pNetworkWarnMsg.set(Res.get("mainView.p2pNetworkWarnMsg.noNodesAvailable"));
                    bootstrapWarning.set(Res.get("mainView.bootstrapWarning.noNodesAvailable"));
                    p2pNetworkLabelId.set("splash-error-state-msg");
                } else {
                    bootstrapWarning.set(null);
                    p2pNetworkLabelId.set("footer-pane");
                }
                splashP2PNetworkAnimationVisible.set(false);
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onBootstrapComplete() {
                log.debug("onBootstrapComplete");
                splashP2PNetworkAnimationVisible.set(false);
                bootstrapComplete.set(true);
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                log.warn("onSetupFailed");
                p2pNetworkWarnMsg.set(Res.get("mainView.p2pNetworkWarnMsg.connectionToP2PFailed", throwable.getMessage()));
                splashP2PNetworkAnimationVisible.set(false);
                bootstrapWarning.set(Res.get("mainView.bootstrapWarning.bootstrappingToP2PFailed"));
                p2pNetworkLabelId.set("splash-error-state-msg");
            }

            @Override
            public void onRequestCustomBridges() {
                showTorNetworkSettingsWindow();
            }
        });

        return p2pNetworkInitialized;
    }

    private void initWalletService() {
        log.info("initWalletService");

        ObjectProperty<Throwable> walletServiceException = new SimpleObjectProperty<>();
        btcInfoBinding = EasyBind.combine(walletsSetup.downloadPercentageProperty(), walletsSetup.numPeersProperty(), walletServiceException,
                (downloadPercentage, numPeers, exception) -> {
                    String result = "";
                    if (exception == null) {
                        double percentage = (double) downloadPercentage;
                        int peers = (int) numPeers;
                        btcSyncProgress.set(percentage);
                        if (percentage == 1) {
                            result = Res.get("mainView.footer.btcInfo",
                                    peers,
                                    Res.get("mainView.footer.btcInfo.synchronizedWith"),
                                    btcNetworkAsString);
                            btcSplashSyncIconId.set("image-connection-synced");

                            if (allBasicServicesInitialized)
                                checkForLockedUpFunds();
                        } else if (percentage > 0.0) {
                            result = Res.get("mainView.footer.btcInfo",
                                    peers,
                                    Res.get("mainView.footer.btcInfo.synchronizedWith"),
                                    btcNetworkAsString + ": " + formatter.formatToPercentWithSymbol(percentage));
                        } else {
                            result = Res.get("mainView.footer.btcInfo",
                                    peers,
                                    Res.get("mainView.footer.btcInfo.connectingTo"),
                                    btcNetworkAsString);
                        }
                    } else {
                        result = Res.get("mainView.footer.btcInfo",
                                numBtcPeers,
                                Res.get("mainView.footer.btcInfo.connectionFailed"),
                                btcNetworkAsString);
                        log.error(exception.getMessage());
                        if (exception instanceof TimeoutException) {
                            walletServiceErrorMsg.set(Res.get("mainView.walletServiceErrorMsg.timeout"));
                        } else if (exception.getCause() instanceof BlockStoreException) {
                            if (exception.getCause().getCause() instanceof ChainFileLockedException) {
                                new Popup<>().warning(Res.get("popup.warning.startupFailed.twoInstances"))
                                        .useShutDownButton()
                                        .show();
                            } else {
                                new Popup<>().warning(Res.get("error.spvFileCorrupted",
                                        exception.getMessage()))
                                        .actionButtonText(Res.get("settings.net.reSyncSPVChainButton"))
                                        .onAction(() -> {
                                            if (walletsSetup.reSyncSPVChain())
                                                new Popup<>().feedback(Res.get("settings.net.reSyncSPVSuccess"))
                                                        .useShutDownButton().show();
                                            else
                                                new Popup<>().error(Res.get("settings.net.reSyncSPVFailed")).show();
                                        })
                                        .show();
                            }
                        } else {
                            walletServiceErrorMsg.set(Res.get("mainView.walletServiceErrorMsg.connectionError", exception.toString()));
                        }
                    }
                    return result;

                });
        btcInfoBinding.subscribe((observable, oldValue, newValue) -> {
            btcInfo.set(newValue);
        });

        walletsSetup.initialize(null,
                () -> {
                    log.debug("walletsSetup.onInitialized");
                    numBtcPeers = walletsSetup.numPeersProperty().get();

                    // We only check one as we apply encryption to all or none
                    if (walletsManager.areWalletsEncrypted()) {
                        if (p2pNetWorkReady.get())
                            splashP2PNetworkAnimationVisible.set(false);

                        walletPasswordWindow
                                .onAesKey(aesKey -> {
                                    walletsManager.setAesKey(aesKey);
                                    if (preferences.isResyncSpvRequested()) {
                                        showFirstPopupIfResyncSPVRequested();
                                    } else {
                                        walletInitialized.set(true);
                                    }
                                })
                                .hideCloseButton()
                                .show();
                    } else {
                        if (preferences.isResyncSpvRequested()) {
                            showFirstPopupIfResyncSPVRequested();
                        } else {
                            walletInitialized.set(true);
                        }
                    }
                },
                walletServiceException::set);
    }

    private void onBasicServicesInitialized() {
        log.info("onBasicServicesInitialized");

        clock.start();

        PaymentMethod.onAllServicesInitialized();

        // disputeManager
        disputeManager.onAllServicesInitialized();
        disputeManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) change -> {
            change.next();
            onDisputesChangeListener(change.getAddedSubList(), change.getRemoved());
        });
        onDisputesChangeListener(disputeManager.getDisputesAsObservableList(), null);

        // tradeManager
        tradeManager.onAllServicesInitialized();
        tradeManager.getTradableList().addListener((ListChangeListener<Trade>) c -> updateBalance());
        tradeManager.getTradableList().addListener((ListChangeListener<Trade>) change -> onTradesChanged());
        onTradesChanged();
        // We handle the trade period here as we display a global popup if we reached dispute time
        tradesAndUIReady = EasyBind.combine(isSplashScreenRemoved, tradeManager.pendingTradesInitializedProperty(), (a, b) -> a && b);
        tradesAndUIReady.subscribe((observable, oldValue, newValue) -> {
            if (newValue)
                applyTradePeriodState();
        });
        tradeManager.setTakeOfferRequestErrorMessageHandler(errorMessage -> new Popup<>()
                .warning(Res.get("popup.error.takeOfferRequestFailed", errorMessage))
                .show());

        // walletService
        btcWalletService.addBalanceListener(new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();
            }
        });

        openOfferManager.getObservableList().addListener((ListChangeListener<OpenOffer>) c -> updateBalance());
        tradeManager.getTradableList().addListener((ListChangeListener<Trade>) c -> updateBalance());
        openOfferManager.onAllServicesInitialized();
        removeOffersWithoutAccountAgeWitness();

        arbitratorManager.onAllServicesInitialized();
        alertManager.alertMessageProperty().addListener((observable, oldValue, newValue) -> displayAlertIfPresent(newValue));
        privateNotificationManager.privateNotificationProperty().addListener((observable, oldValue, newValue) -> displayPrivateNotification(newValue));
        displayAlertIfPresent(alertManager.alertMessageProperty().get());

        p2PService.onAllServicesInitialized();

        feeService.onAllServicesInitialized();
        GUIUtil.setFeeService(feeService);

        daoManager.onAllServicesInitialized(errorMessage -> new Popup<>().error(errorMessage).show());

        tradeStatisticsManager.onAllServicesInitialized();

        accountAgeWitnessService.onAllServicesInitialized();

        priceFeedService.setCurrencyCodeOnInit();

        filterManager.onAllServicesInitialized();
        filterManager.addListener(filter -> {
            if (filter != null) {
                if (filter.getSeedNodes() != null && !filter.getSeedNodes().isEmpty())
                    new Popup<>().warning(Res.get("popup.warning.nodeBanned", Res.get("popup.warning.seed"))).show();

                if (filter.getPriceRelayNodes() != null && !filter.getPriceRelayNodes().isEmpty())
                    new Popup<>().warning(Res.get("popup.warning.nodeBanned", Res.get("popup.warning.priceRelay"))).show();
            }
        });

        setupBtcNumPeersWatcher();
        setupP2PNumPeersWatcher();
        updateBalance();
        if (DevEnv.DEV_MODE) {
            preferences.setShowOwnOffersInOfferBook(true);
            setupDevDummyPaymentAccounts();
        }

        fillPriceFeedComboBoxItems();
        setupMarketPriceFeed();
        swapPendingOfferFundingEntries();

        showAppScreen.set(true);

        String key = "remindPasswordAndBackup";
        user.getPaymentAccountsAsObservable().addListener((SetChangeListener<PaymentAccount>) change -> {
            if (!walletsManager.areWalletsEncrypted() && preferences.showAgain(key) && change.wasAdded()) {
                new Popup<>().headLine(Res.get("popup.securityRecommendation.headline"))
                        .information(Res.get("popup.securityRecommendation.msg"))
                        .dontShowAgainId(key)
                        .show();
            }
        });

        checkIfOpenOffersMatchTradeProtocolVersion();

        if (walletsSetup.downloadPercentageProperty().get() == 1)
            checkForLockedUpFunds();

        allBasicServicesInitialized = true;
    }

    private void showFirstPopupIfResyncSPVRequested() {
        Popup firstPopup = new Popup<>();
        firstPopup.information(Res.get("settings.net.reSyncSPVAfterRestart")).show();
        if (btcSyncProgress.get() == 1) {
            showSecondPopupIfResyncSPVRequested(firstPopup);
        } else {
            btcSyncProgress.addListener((observable, oldValue, newValue) -> {
                if ((double) newValue == 1)
                    showSecondPopupIfResyncSPVRequested(firstPopup);
            });
        }
    }

    private void showSecondPopupIfResyncSPVRequested(Popup firstPopup) {
        firstPopup.hide();
        preferences.setResyncSpvRequested(false);
        new Popup<>().information(Res.get("settings.net.reSyncSPVAfterRestartCompleted"))
                .hideCloseButton()
                .useShutDownButton()
                .show();
    }

    private void checkIfLocalHostNodeIsRunning() {
        Thread checkIfLocalHostNodeIsRunningThread = new Thread() {
            @Override
            public void run() {
                Thread.currentThread().setName("checkIfLocalHostNodeIsRunningThread");
                Socket socket = null;
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(InetAddresses.forString("127.0.0.1"),
                            BisqEnvironment.getBaseCurrencyNetwork().getParameters().getPort()), 5000);
                    log.info("Localhost peer detected.");
                    UserThread.execute(() -> {
                        bisqEnvironment.setBitcoinLocalhostNodeRunning(true);
                        readMapsFromResources();
                    });
                } catch (Throwable e) {
                    log.info("Localhost peer not detected.");
                    UserThread.execute(MainViewModel.this::readMapsFromResources);
                } finally {
                    if (socket != null) {
                        try {
                            socket.close();
                        } catch (IOException ignore) {
                        }
                    }
                }
            }
        };
        checkIfLocalHostNodeIsRunningThread.start();
    }

    private void checkCryptoSetup() {
        BooleanProperty result = new SimpleBooleanProperty();
        // We want to test if the client is compiled with the correct crypto provider (BountyCastle)
        // and if the unlimited Strength for cryptographic keys is set.
        // If users compile themselves they might miss that step and then would get an exception in the trade.
        // To avoid that we add here at startup a sample encryption and signing to see if it don't causes an exception.
        // See: https://github.com/bisq-network/exchange/blob/master/doc/build.md#7-enable-unlimited-strength-for-cryptographic-keys
        Thread checkCryptoThread = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.currentThread().setName("checkCryptoThread");
                    log.trace("Run crypto test");
                    // just use any simple dummy msg
                    Ping payload = new Ping(1, 1);
                    SealedAndSigned sealedAndSigned = EncryptionService.encryptHybridWithSignature(payload,
                            keyRing.getSignatureKeyPair(), keyRing.getPubKeyRing().getEncryptionPubKey());
                    DecryptedDataTuple tuple = encryptionService.decryptHybridWithSignature(sealedAndSigned, keyRing.getEncryptionKeyPair().getPrivate());
                    if (tuple.getNetworkEnvelope() instanceof Ping &&
                            ((Ping) tuple.getNetworkEnvelope()).getNonce() == payload.getNonce() &&
                            ((Ping) tuple.getNetworkEnvelope()).getLastRoundTripTime() == payload.getLastRoundTripTime()) {
                        log.debug("Crypto test succeeded");

                        if (Security.getProvider("BC") != null) {
                            UserThread.execute(() -> result.set(true));
                        } else {
                            throw new CryptoException("Security provider BountyCastle is not available.");
                        }
                    } else {
                        throw new CryptoException("Payload not correct after decryption");
                    }
                } catch (CryptoException e) {
                    e.printStackTrace();
                    String msg = Res.get("popup.warning.cryptoTestFailed", e.getMessage());
                    log.error(msg);
                    UserThread.execute(() -> new Popup<>().warning(msg)
                            .useShutDownButton()
                            .useReportBugButton()
                            .show());
                }
            }
        };
        checkCryptoThread.start();
    }

    private void checkIfOpenOffersMatchTradeProtocolVersion() {
        List<OpenOffer> outDatedOffers = openOfferManager.getObservableList()
                .stream()
                .filter(e -> e.getOffer().getProtocolVersion() != Version.TRADE_PROTOCOL_VERSION)
                .collect(Collectors.toList());
        if (!outDatedOffers.isEmpty()) {
            String offers = outDatedOffers.stream()
                    .map(e -> e.getId() + "\n")
                    .collect(Collectors.toList()).toString()
                    .replace("[", "").replace("]", "");
            new Popup<>()
                    .warning(Res.get("popup.warning.oldOffers.msg", offers))
                    .actionButtonText(Res.get("popup.warning.oldOffers.buttonText"))
                    .onAction(() -> openOfferManager.removeOpenOffers(outDatedOffers, null))
                    .useShutDownButton()
                    .show();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    // After showAppScreen is set and splash screen is faded out
    void onSplashScreenRemoved() {
        isSplashScreenRemoved.set(true);

        // Delay that as we want to know what is the current path of the navigation which is set
        // in MainView showAppScreen handler
        notificationCenter.onAllServicesAndViewsInitialized();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // States
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyTradePeriodState() {
        updateTradePeriodState();
        clock.addListener(new Clock.Listener() {
            @Override
            public void onSecondTick() {
            }

            @Override
            public void onMinuteTick() {
                updateTradePeriodState();
            }

            @Override
            public void onMissedSecondTick(long missed) {
            }
        });
    }

    private void updateTradePeriodState() {
        tradeManager.getTradableList().stream().forEach(trade -> {
            if (!trade.isPayoutPublished()) {
                Date maxTradePeriodDate = trade.getMaxTradePeriodDate();
                Date halfTradePeriodDate = trade.getHalfTradePeriodDate();
                if (maxTradePeriodDate != null && halfTradePeriodDate != null) {
                    Date now = new Date();
                    if (now.after(maxTradePeriodDate))
                        trade.setTradePeriodState(Trade.TradePeriodState.TRADE_PERIOD_OVER);
                    else if (now.after(halfTradePeriodDate))
                        trade.setTradePeriodState(Trade.TradePeriodState.SECOND_HALF);

                    String key;
                    switch (trade.getTradePeriodState()) {
                        case FIRST_HALF:
                            break;
                        case SECOND_HALF:
                            key = "displayHalfTradePeriodOver" + trade.getId();
                            if (DontShowAgainLookup.showAgain(key)) {
                                DontShowAgainLookup.dontShowAgain(key, true);
                                new Popup<>().warning(Res.get("popup.warning.tradePeriod.halfReached",
                                        trade.getShortId(),
                                        formatter.formatDateTime(maxTradePeriodDate)))
                                        .show();
                            }
                            break;
                        case TRADE_PERIOD_OVER:
                            key = "displayTradePeriodOver" + trade.getId();
                            if (DontShowAgainLookup.showAgain(key)) {
                                DontShowAgainLookup.dontShowAgain(key, true);
                                new Popup<>().warning(Res.get("popup.warning.tradePeriod.ended",
                                        trade.getShortId(),
                                        formatter.formatDateTime(maxTradePeriodDate)))
                                        .show();
                            }
                            break;
                    }
                }
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupP2PNumPeersWatcher() {
        p2PService.getNumConnectedPeers().addListener((observable, oldValue, newValue) -> {
            int numPeers = (int) newValue;
            if ((int) oldValue > 0 && numPeers == 0) {
                // give a bit of tolerance
                if (checkNumberOfP2pNetworkPeersTimer != null)
                    checkNumberOfP2pNetworkPeersTimer.stop();

                checkNumberOfP2pNetworkPeersTimer = UserThread.runAfter(() -> {
                    // check again numPeers
                    if (p2PService.getNumConnectedPeers().get() == 0) {
                        p2pNetworkWarnMsg.set(Res.get("mainView.networkWarning.allConnectionsLost", Res.get("shared.P2P")));
                        p2pNetworkLabelId.set("splash-error-state-msg");
                    } else {
                        p2pNetworkWarnMsg.set(null);
                        p2pNetworkLabelId.set("footer-pane");
                    }
                }, 5);
            } else if ((int) oldValue == 0 && numPeers > 0) {
                if (checkNumberOfP2pNetworkPeersTimer != null)
                    checkNumberOfP2pNetworkPeersTimer.stop();

                p2pNetworkWarnMsg.set(null);
                p2pNetworkLabelId.set("footer-pane");
            }
        });
    }

    private void setupBtcNumPeersWatcher() {
        walletsSetup.numPeersProperty().addListener((observable, oldValue, newValue) -> {
            int numPeers = (int) newValue;
            if ((int) oldValue > 0 && numPeers == 0) {
                if (checkNumberOfBtcPeersTimer != null)
                    checkNumberOfBtcPeersTimer.stop();

                checkNumberOfBtcPeersTimer = UserThread.runAfter(() -> {
                    // check again numPeers
                    if (walletsSetup.numPeersProperty().get() == 0) {
                        walletServiceErrorMsg.set(Res.get("mainView.networkWarning.allConnectionsLost", Res.getBaseCurrencyName().toLowerCase()));
                    } else {
                        walletServiceErrorMsg.set(null);
                    }
                }, 5);
            } else if ((int) oldValue == 0 && numPeers > 0) {
                if (checkNumberOfBtcPeersTimer != null)
                    checkNumberOfBtcPeersTimer.stop();
                walletServiceErrorMsg.set(null);
            }
        });
    }

    private void setupMarketPriceFeed() {
        priceFeedService.requestPriceFeed(price -> marketPrice.set(formatter.formatMarketPrice(price, priceFeedService.getCurrencyCode())),
                (errorMessage, throwable) -> marketPrice.set(Res.get("shared.na")));

        marketPriceBinding = EasyBind.combine(
                marketPriceCurrencyCode, marketPrice,
                (currencyCode, price) -> formatter.getCurrencyPair(currencyCode) + ": " + price);

        marketPriceBinding.subscribe((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals(oldValue)) {
                setMarketPriceInItems();

                String code = priceFeedService.currencyCodeProperty().get();
                Optional<PriceFeedComboBoxItem> itemOptional = findPriceFeedComboBoxItem(code);
                if (itemOptional.isPresent()) {
                    itemOptional.get().setDisplayString(newValue);
                    selectedPriceFeedComboBoxItemProperty.set(itemOptional.get());
                } else {
                    if (CurrencyUtil.isCryptoCurrency(code)) {
                        CurrencyUtil.getCryptoCurrency(code).ifPresent(cryptoCurrency -> {
                            preferences.addCryptoCurrency(cryptoCurrency);
                            fillPriceFeedComboBoxItems();
                        });
                    } else {
                        CurrencyUtil.getFiatCurrency(code).ifPresent(fiatCurrency -> {
                            preferences.addFiatCurrency(fiatCurrency);
                            fillPriceFeedComboBoxItems();
                        });
                    }
                }

                if (selectedPriceFeedComboBoxItemProperty.get() != null)
                    selectedPriceFeedComboBoxItemProperty.get().setDisplayString(newValue);
            }
        });

        marketPriceCurrencyCode.bind(priceFeedService.currencyCodeProperty());

        priceFeedAllLoadedSubscription = EasyBind.subscribe(priceFeedService.updateCounterProperty(), updateCounter -> setMarketPriceInItems());

        preferences.getTradeCurrenciesAsObservable().addListener((ListChangeListener<TradeCurrency>) c -> UserThread.runAfter(() -> {
            fillPriceFeedComboBoxItems();
            setMarketPriceInItems();
        }, 100, TimeUnit.MILLISECONDS));
    }

    private void setMarketPriceInItems() {
        priceFeedComboBoxItems.stream().forEach(item -> {
            String currencyCode = item.currencyCode;
            MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
            String priceString;
            if (marketPrice != null && marketPrice.isPriceAvailable()) {
                priceString = formatter.formatMarketPrice(marketPrice.getPrice(), currencyCode);
                item.setPriceAvailable(true);
                item.setExternallyProvidedPrice(marketPrice.isExternallyProvidedPrice());
            } else {
                priceString = Res.get("shared.na");
                item.setPriceAvailable(false);
            }
            item.setDisplayString(formatter.getCurrencyPair(currencyCode) + ": " + priceString);

            final String code = item.currencyCode;
            if (selectedPriceFeedComboBoxItemProperty.get() != null &&
                    selectedPriceFeedComboBoxItemProperty.get().currencyCode.equals(code)) {
                isFiatCurrencyPriceFeedSelected.set(CurrencyUtil.isFiatCurrency(code) && CurrencyUtil.getFiatCurrency(code).isPresent() && item.isPriceAvailable() && item.isExternallyProvidedPrice());
                isCryptoCurrencyPriceFeedSelected.set(CurrencyUtil.isCryptoCurrency(code) && CurrencyUtil.getCryptoCurrency(code).isPresent() && item.isPriceAvailable() && item.isExternallyProvidedPrice());
                isExternallyProvidedPrice.set(item.isExternallyProvidedPrice());
                isPriceAvailable.set(item.isPriceAvailable());
                marketPriceUpdated.set(marketPriceUpdated.get() + 1);
            }
        });
    }

    public void setPriceFeedComboBoxItem(PriceFeedComboBoxItem item) {
        if (item != null) {
            Optional<PriceFeedComboBoxItem> itemOptional = findPriceFeedComboBoxItem(priceFeedService.currencyCodeProperty().get());
            if (itemOptional.isPresent())
                selectedPriceFeedComboBoxItemProperty.set(itemOptional.get());
            else
                findPriceFeedComboBoxItem(preferences.getPreferredTradeCurrency().getCode())
                        .ifPresent(selectedPriceFeedComboBoxItemProperty::set);

            priceFeedService.setCurrencyCode(item.currencyCode);
        } else {
            findPriceFeedComboBoxItem(preferences.getPreferredTradeCurrency().getCode())
                    .ifPresent(selectedPriceFeedComboBoxItemProperty::set);
        }
    }

    private Optional<PriceFeedComboBoxItem> findPriceFeedComboBoxItem(String currencyCode) {
        return priceFeedComboBoxItems.stream()
                .filter(item -> item.currencyCode.equals(currencyCode))
                .findAny();
    }

    private void fillPriceFeedComboBoxItems() {
        List<PriceFeedComboBoxItem> currencyItems = preferences.getTradeCurrenciesAsObservable()
                .stream()
                .map(tradeCurrency -> new PriceFeedComboBoxItem(tradeCurrency.getCode()))
                .collect(Collectors.toList());
        priceFeedComboBoxItems.setAll(currencyItems);
    }

    private void displayAlertIfPresent(Alert alert) {
        boolean alreadyDisplayed = alert != null && alert.equals(user.getDisplayedAlert());
        user.setDisplayedAlert(alert);
        if (alert != null && !alreadyDisplayed) {
            if (alert.isUpdateInfo()) {
                if (alert.isNewVersion())
                    new DisplayUpdateDownloadWindow(alert).show();
            } else {
                new DisplayAlertMessageWindow().alertMessage(alert).show();
            }
        }
    }

    private void displayPrivateNotification(PrivateNotificationPayload privateNotification) {
        new Popup<>().headLine(Res.get("popup.privateNotification.headline"))
                .attention(privateNotification.getMessage())
                .setHeadlineStyle("-fx-text-fill: -bs-error-red;  -fx-font-weight: bold;  -fx-font-size: 16;")
                .onClose(privateNotificationManager::removePrivateNotification)
                .useIUnderstandButton()
                .show();
    }

    private void swapPendingOfferFundingEntries() {
        tradeManager.getAddressEntriesForAvailableBalanceStream()
                .filter(addressEntry -> addressEntry.getOfferId() != null)
                .forEach(addressEntry -> {
                    log.debug("swapPendingOfferFundingEntries, offerId={}, OFFER_FUNDING", addressEntry.getOfferId());
                    btcWalletService.swapTradeEntryToAvailableEntry(addressEntry.getOfferId(), AddressEntry.Context.OFFER_FUNDING);
                });
    }

    private void updateBalance() {
        // Without delaying to the next cycle it does not update.
        // Seems order of events we are listening on causes that...
        UserThread.execute(() -> {
            updateAvailableBalance();
            updateReservedBalance();
            updateLockedBalance();
        });
    }

    private void updateAvailableBalance() {
        Coin totalAvailableBalance = Coin.valueOf(tradeManager.getAddressEntriesForAvailableBalanceStream()
                .mapToLong(addressEntry -> btcWalletService.getBalanceForAddress(addressEntry.getAddress()).getValue())
                .sum());
        String value = formatter.formatCoinWithCode(totalAvailableBalance);
        // If we get full precision the BTC postfix breaks layout so we omit it
        if (value.length() > 11)
            value = formatter.formatCoin(totalAvailableBalance);
        availableBalance.set(value);
    }

    private void updateReservedBalance() {
        Coin sum = Coin.valueOf(openOfferManager.getObservableList().stream()
                .map(openOffer -> {
                    final Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(openOffer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE);
                    if (addressEntryOptional.isPresent()) {
                        Address address = addressEntryOptional.get().getAddress();
                        return btcWalletService.getBalanceForAddress(address);
                    } else {
                        return null;
                    }
                })
                .filter(e -> e != null)
                .mapToLong(Coin::getValue)
                .sum());

        reservedBalance.set(formatter.formatCoinWithCode(sum));
    }

    private void updateLockedBalance() {
        Stream<Trade> lockedTrades = Stream.concat(closedTradableManager.getLockedTradesStream(), failedTradesManager.getLockedTradesStream());
        lockedTrades = Stream.concat(lockedTrades, tradeManager.getLockedTradesStream());
        Coin sum = Coin.valueOf(lockedTrades
                .mapToLong(trade -> {
                    final Optional<AddressEntry> addressEntryOptional = btcWalletService.getAddressEntry(trade.getId(), AddressEntry.Context.MULTI_SIG);
                    if (addressEntryOptional.isPresent())
                        return addressEntryOptional.get().getCoinLockedInMultiSig().getValue();
                    else
                        return 0;
                })
                .sum());
        lockedBalance.set(formatter.formatCoinWithCode(sum));
    }

    private void checkForLockedUpFunds() {
        Set<String> tradesIdSet = tradeManager.getLockedTradesStream()
                .filter(Trade::hasFailed)
                .map(Trade::getId)
                .collect(Collectors.toSet());
        tradesIdSet.addAll(failedTradesManager.getLockedTradesStream()
                .map(Trade::getId)
                .collect(Collectors.toSet()));
        tradesIdSet.addAll(closedTradableManager.getLockedTradesStream()
                .map(e -> {
                    log.warn("We found a closed trade with locked up funds. " +
                            "That should never happen. trade ID=" + e.getId());
                    return e.getId();
                })
                .collect(Collectors.toSet()));

        btcWalletService.getAddressEntriesForTrade().stream()
                .filter(e -> tradesIdSet.contains(e.getOfferId()) && e.getContext() == AddressEntry.Context.MULTI_SIG)
                .forEach(e -> {
                    final Coin balance = e.getCoinLockedInMultiSig();
                    final String message = Res.get("popup.warning.lockedUpFunds",
                            formatter.formatCoinWithCode(balance), e.getAddressString(), e.getOfferId());
                    log.warn(message);
                    new Popup<>().warning(message).show();
                });
    }


    private void onDisputesChangeListener(List<? extends Dispute> addedList, @Nullable List<? extends Dispute> removedList) {
        if (removedList != null) {
            removedList.stream().forEach(dispute -> {
                String id = dispute.getId();
                if (disputeIsClosedSubscriptionsMap.containsKey(id)) {
                    disputeIsClosedSubscriptionsMap.get(id).unsubscribe();
                    disputeIsClosedSubscriptionsMap.remove(id);
                }
            });
        }
        addedList.stream().forEach(dispute -> {
            String id = dispute.getId();
            Subscription disputeStateSubscription = EasyBind.subscribe(dispute.isClosedProperty(),
                    isClosed -> {
                        // We get event before list gets updated, so we execute on next frame
                        UserThread.execute(() -> {
                            int openDisputes = disputeManager.getDisputesAsObservableList().stream()
                                    .filter(e -> !e.isClosed())
                                    .collect(Collectors.toList()).size();
                            if (openDisputes > 0)
                                numOpenDisputesAsString.set(String.valueOf(openDisputes));
                            if (openDisputes > 9)
                                numOpenDisputesAsString.set("★");

                            showOpenDisputesNotification.set(openDisputes > 0);
                        });
                    });
            disputeIsClosedSubscriptionsMap.put(id, disputeStateSubscription);
        });
    }

    private void onTradesChanged() {
        long numPendingTrades = tradeManager.getTradableList().size();
        if (numPendingTrades > 0)
            numPendingTradesAsString.set(String.valueOf(numPendingTrades));
        if (numPendingTrades > 9)
            numPendingTradesAsString.set("★");

        showPendingTradesNotification.set(numPendingTrades > 0);
    }

    private void removeOffersWithoutAccountAgeWitness() {
        if (new Date().after(AccountAgeWitnessService.FULL_ACTIVATION)) {
            openOfferManager.getObservableList().stream()
                    .filter(e -> CurrencyUtil.isFiatCurrency(e.getOffer().getCurrencyCode()))
                    .filter(e -> !e.getOffer().getAccountAgeWitnessHashAsHex().isPresent())
                    .forEach(e -> {
                        new Popup<>().warning(Res.get("popup.warning.offerWithoutAccountAgeWitness", e.getId()))
                                .actionButtonText(Res.get("popup.warning.offerWithoutAccountAgeWitness.confirm"))
                                .onAction(() -> {
                                    openOfferManager.removeOffer(e.getOffer(),
                                            () -> {
                                                log.info("Offer with ID {} is removed", e.getId());
                                            },
                                            log::error);
                                })
                                .hideCloseButton()
                                .show();
                    });
        }
    }

    private void setupDevDummyPaymentAccounts() {
        if (user.getPaymentAccounts() != null && user.getPaymentAccounts().isEmpty()) {
            PerfectMoneyAccount perfectMoneyAccount = new PerfectMoneyAccount();
            perfectMoneyAccount.init();
            perfectMoneyAccount.setAccountNr("dummy_" + new Random().nextInt(100));
            perfectMoneyAccount.setAccountName("PerfectMoney dummy");// Don't translate only for dev
            perfectMoneyAccount.setSelectedTradeCurrency(GlobalSettings.getDefaultTradeCurrency());
            user.addPaymentAccount(perfectMoneyAccount);

            if (p2PService.isBootstrapped()) {
                accountAgeWitnessService.publishMyAccountAgeWitness(perfectMoneyAccount.getPaymentAccountPayload());
            } else {
                p2PService.addP2PServiceListener(new BootstrapListener() {
                    @Override
                    public void onBootstrapComplete() {
                        accountAgeWitnessService.publishMyAccountAgeWitness(perfectMoneyAccount.getPaymentAccountPayload());
                    }
                });
            }

            CryptoCurrencyAccount cryptoCurrencyAccount = new CryptoCurrencyAccount();
            cryptoCurrencyAccount.init();
            cryptoCurrencyAccount.setAccountName("ETH dummy");// Don't translate only for dev
            cryptoCurrencyAccount.setAddress("0x" + new Random().nextInt(1000000));
            cryptoCurrencyAccount.setSingleTradeCurrency(CurrencyUtil.getCryptoCurrency("ETH").get());
            user.addPaymentAccount(cryptoCurrencyAccount);
        }
    }

    String getAppDateDir() {
        return bisqEnvironment.getProperty(AppOptionKeys.APP_DATA_DIR_KEY);
    }
}
