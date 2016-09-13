/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.main;

import com.google.inject.Inject;
import io.bitsquare.alert.Alert;
import io.bitsquare.alert.AlertManager;
import io.bitsquare.alert.PrivateNotification;
import io.bitsquare.alert.PrivateNotificationManager;
import io.bitsquare.app.BitsquareApp;
import io.bitsquare.app.DevFlags;
import io.bitsquare.app.Log;
import io.bitsquare.app.Version;
import io.bitsquare.arbitration.ArbitratorManager;
import io.bitsquare.arbitration.Dispute;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.pricefeed.MarketPrice;
import io.bitsquare.btc.pricefeed.PriceFeedService;
import io.bitsquare.common.Clock;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.*;
import io.bitsquare.filter.FilterManager;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.components.BalanceTextField;
import io.bitsquare.gui.components.BalanceWithConfirmationTextField;
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.main.overlays.notifications.NotificationCenter;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.main.overlays.windows.AddBridgeEntriesWindow;
import io.bitsquare.gui.main.overlays.windows.DisplayAlertMessageWindow;
import io.bitsquare.gui.main.overlays.windows.TacWindow;
import io.bitsquare.gui.main.overlays.windows.WalletPasswordWindow;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.GUIUtil;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.locale.TradeCurrency;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.p2p.network.CloseConnectionReason;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.ConnectionListener;
import io.bitsquare.p2p.peers.keepalive.messages.Ping;
import io.bitsquare.payment.CryptoCurrencyAccount;
import io.bitsquare.payment.OKPayAccount;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.SetChangeListener;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.store.BlockStoreException;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.security.Security;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class MainViewModel implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(MainViewModel.class);

    private final WalletService walletService;
    private final TradeWalletService tradeWalletService;
    private final ArbitratorManager arbitratorManager;
    private final P2PService p2PService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final DisputeManager disputeManager;
    final Preferences preferences;
    private final AlertManager alertManager;
    private PrivateNotificationManager privateNotificationManager;
    private FilterManager filterManager;
    private final WalletPasswordWindow walletPasswordWindow;
    private final NotificationCenter notificationCenter;
    private final TacWindow tacWindow;
    private Clock clock;
    private KeyRing keyRing;
    private final Navigation navigation;
    private final BSFormatter formatter;

    // BTC network
    final StringProperty btcInfo = new SimpleStringProperty("Initializing");
    final DoubleProperty btcSyncProgress = new SimpleDoubleProperty(DevFlags.STRESS_TEST_MODE ? 0 : -1);
    final StringProperty walletServiceErrorMsg = new SimpleStringProperty();
    final StringProperty btcSplashSyncIconId = new SimpleStringProperty();
    final StringProperty marketPriceCurrencyCode = new SimpleStringProperty("");
    final ObjectProperty<PriceFeedService.Type> typeProperty = new SimpleObjectProperty<>(PriceFeedService.Type.LAST);
    final ObjectProperty<PriceFeedComboBoxItem> selectedPriceFeedComboBoxItemProperty = new SimpleObjectProperty<>();
    final BooleanProperty isFiatCurrencyPriceFeedSelected = new SimpleBooleanProperty(true);
    final BooleanProperty isCryptoCurrencyPriceFeedSelected = new SimpleBooleanProperty(false);
    final StringProperty availableBalance = new SimpleStringProperty();
    final StringProperty reservedBalance = new SimpleStringProperty();
    final StringProperty lockedBalance = new SimpleStringProperty();
    private MonadicBinding<String> btcInfoBinding;

    final StringProperty marketPrice = new SimpleStringProperty("N/A");

    // P2P network
    final StringProperty p2PNetworkInfo = new SimpleStringProperty();
    private MonadicBinding<String> p2PNetworkInfoBinding;
    final BooleanProperty splashP2PNetworkAnimationVisible = new SimpleBooleanProperty(true);
    final StringProperty p2pNetworkWarnMsg = new SimpleStringProperty();
    final StringProperty p2PNetworkIconId = new SimpleStringProperty();
    final BooleanProperty bootstrapComplete = new SimpleBooleanProperty();

    // software update
    final String version = "v." + Version.VERSION;

    final BooleanProperty showAppScreen = new SimpleBooleanProperty();
    final StringProperty numPendingTradesAsString = new SimpleStringProperty();
    final BooleanProperty showPendingTradesNotification = new SimpleBooleanProperty();
    final StringProperty numOpenDisputesAsString = new SimpleStringProperty();
    final BooleanProperty showOpenDisputesNotification = new SimpleBooleanProperty();
    private final BooleanProperty isSplashScreenRemoved = new SimpleBooleanProperty();
    private final String btcNetworkAsString;
    final StringProperty p2pNetworkLabelId = new SimpleStringProperty("footer-pane");

    private MonadicBinding<Boolean> allServicesDone, tradesAndUIReady;
    final PriceFeedService priceFeedService;
    private final User user;
    private int numBtcPeers = 0;
    private Timer checkNumberOfBtcPeersTimer;
    private Timer checkNumberOfP2pNetworkPeersTimer;
    private final Map<String, Subscription> disputeIsClosedSubscriptionsMap = new HashMap<>();
    final ObservableList<PriceFeedComboBoxItem> priceFeedComboBoxItems = FXCollections.observableArrayList();
    private MonadicBinding<String> marketPriceBinding;
    private Subscription priceFeedAllLoadedSubscription;
    private Popup startupTimeoutPopup;
    private BooleanProperty p2pNetWorkReady;
    private final BooleanProperty walletInitialized = new SimpleBooleanProperty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MainViewModel(WalletService walletService, TradeWalletService tradeWalletService,
                         PriceFeedService priceFeedService,
                         ArbitratorManager arbitratorManager, P2PService p2PService, TradeManager tradeManager,
                         OpenOfferManager openOfferManager, DisputeManager disputeManager, Preferences preferences,
                         User user, AlertManager alertManager, PrivateNotificationManager privateNotificationManager,
                         FilterManager filterManager, WalletPasswordWindow walletPasswordWindow,
                         NotificationCenter notificationCenter, TacWindow tacWindow, Clock clock,
                         KeyRing keyRing, Navigation navigation, BSFormatter formatter) {
        this.priceFeedService = priceFeedService;
        this.user = user;
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.arbitratorManager = arbitratorManager;
        this.p2PService = p2PService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.disputeManager = disputeManager;
        this.preferences = preferences;
        this.alertManager = alertManager;
        this.privateNotificationManager = privateNotificationManager;
        this.filterManager = filterManager; // Needed to be referenced so we get it initialized and get the eventlistener registered
        this.walletPasswordWindow = walletPasswordWindow;
        this.notificationCenter = notificationCenter;
        this.tacWindow = tacWindow;
        this.clock = clock;
        this.keyRing = keyRing;
        this.navigation = navigation;
        this.formatter = formatter;

        btcNetworkAsString = formatter.formatBitcoinNetwork(preferences.getBitcoinNetwork()) +
                (preferences.getUseTorForBitcoinJ() ? " (using Tor)" : "");

        TxIdTextField.setPreferences(preferences);
        TxIdTextField.setWalletService(walletService);
        BalanceTextField.setWalletService(walletService);
        BalanceWithConfirmationTextField.setWalletService(walletService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void initializeAllServices() {
        Log.traceCall();

        UserThread.runAfter(tacWindow::showIfNeeded, 2);

        ChangeListener<Boolean> walletInitializedListener = (observable, oldValue, newValue) -> {
            if (newValue && !p2pNetWorkReady.get())
                showStartupTimeoutPopup();
        };

        Timer startupTimeout = UserThread.runAfter(() -> {
            log.warn("startupTimeout called");
            Wallet wallet = walletService.getWallet();
            if (wallet != null && wallet.isEncrypted())
                walletInitialized.addListener(walletInitializedListener);
            else
                showStartupTimeoutPopup();
        }, 4, TimeUnit.MINUTES);

        p2pNetWorkReady = initP2PNetwork();
        initBitcoinWallet();

        // need to store it to not get garbage collected
        allServicesDone = EasyBind.combine(walletInitialized, p2pNetWorkReady, (a, b) -> a && b);
        allServicesDone.subscribe((observable, oldValue, newValue) -> {
            if (newValue) {
                startupTimeout.stop();
                walletInitialized.removeListener(walletInitializedListener);
                onAllServicesInitialized();
                if (startupTimeoutPopup != null)
                    startupTimeoutPopup.hide();
            }
        });
    }

    private void showStartupTimeoutPopup() {
        MainView.blur();
        String details;
        if (!walletInitialized.get()) {
            details = "You still did not get connected to the bitcoin network.\n" +
                    "If you use Tor for Bitcoin it might be that you got an unstable Tor path.\n" +
                    "You can wait longer or try to restart.";
        } else if (!p2pNetWorkReady.get()) {
            details = "You still did not get connected to the P2P network.\n" +
                    "That can happen sometimes when you got an unstable Tor path.\n" +
                    "You can wait longer or try to restart.";
        } else {
            log.error("Startup timeout with unknown problem.");
            details = "There is an unknown problem at startup.\n" +
                    "Please restart and if the problem continues file a bug report.";
        }
        startupTimeoutPopup = new Popup();
        startupTimeoutPopup.warning("The application could not startup after 4 minutes.\n\n" +
                details)
                .actionButtonText("Shut down")
                .onAction(BitsquareApp.shutDownHandler::run)
                .show();
    }

    public void shutDown() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BooleanProperty initP2PNetwork() {
        Log.traceCall();
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
                        if (dataReceived && hiddenService)
                            result = "Nr. of P2P network peers: " + numPeers;
                        else if (peers == 0)
                            result = state;
                        else
                            result = state + " / Nr. of P2P network peers: " + numPeers;
                    }
                    return result;
                });
        p2PNetworkInfoBinding.subscribe((observable, oldValue, newValue) -> {
            p2PNetworkInfo.set(newValue);
        });

        bootstrapState.set("Connecting to Tor network...");

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
                    //TODO
                   /* new Popup()
                            .warning("You got disconnected from a seed node.\n\n" +
                                    "Reason for getting disconnected: " + connection.getRuleViolation().name() + "\n\n" +
                                    "It might be that your installed version is not compatible with " +
                                    "the network.\n\n" +
                                    "Please check if you run the latest software version.\n" +
                                    "You can download the latest version of Bitsquare at:\n" +
                                    "https://github.com/bitsquare/bitsquare/releases")
                            .show();*/
                }
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });

        final BooleanProperty p2pNetworkInitialized = new SimpleBooleanProperty();
        boolean useBridges = preferences.getBridgeAddresses() != null && !preferences.getBridgeAddresses().isEmpty();
        p2PService.start(useBridges, new P2PServiceListener() {
            @Override
            public void onTorNodeReady() {
                bootstrapState.set("Tor node created");
                p2PNetworkIconId.set("image-connection-tor");

                if (preferences.getUseTorForBitcoinJ())
                    initWalletService();
            }

            @Override
            public void onHiddenServicePublished() {
                hiddenServicePublished.set(true);
                bootstrapState.set("Hidden Service published");
            }

            @Override
            public void onRequestingDataCompleted() {
                initialP2PNetworkDataReceived.set(true);
                bootstrapState.set("Initial data received");
                splashP2PNetworkAnimationVisible.set(false);
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoSeedNodeAvailable() {
                if (p2PService.getNumConnectedPeers().get() == 0)
                    bootstrapWarning.set("No seed nodes available");
                else
                    bootstrapWarning.set(null);

                splashP2PNetworkAnimationVisible.set(false);
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoPeersAvailable() {
                if (p2PService.getNumConnectedPeers().get() == 0) {
                    p2pNetworkWarnMsg.set("There are no seed nodes or persisted peers available for requesting data.\n" +
                            "Please check your internet connection or try to restart the application.");
                    bootstrapWarning.set("No seed nodes and peers available");
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
                splashP2PNetworkAnimationVisible.set(false);
                bootstrapComplete.set(true);
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                p2pNetworkWarnMsg.set("Connecting to the P2P network failed (reported error: "
                        + throwable.getMessage() + ").\n" +
                        "Please check your internet connection or try to restart the application.");
                splashP2PNetworkAnimationVisible.set(false);
                bootstrapWarning.set("Bootstrapping to P2P network failed");
                p2pNetworkLabelId.set("splash-error-state-msg");
            }

            @Override
            public void onUseDefaultBridges() {
            }

            @Override
            public void onRequestCustomBridges(Runnable resultHandler) {
                new AddBridgeEntriesWindow()
                        .onAction(resultHandler::run)
                        .show();
            }
        });

        return p2pNetworkInitialized;
    }

    private void initBitcoinWallet() {
        Log.traceCall();

        // We only init wallet service here if not using Tor for bitcoinj.        
        // When using Tor, wallet init must be deferred until Tor is ready.
        if (!preferences.getUseTorForBitcoinJ())
            initWalletService();
    }

    private void initWalletService() {
        Log.traceCall();
        ObjectProperty<Throwable> walletServiceException = new SimpleObjectProperty<>();
        btcInfoBinding = EasyBind.combine(walletService.downloadPercentageProperty(), walletService.numPeersProperty(), walletServiceException,
                (downloadPercentage, numPeers, exception) -> {
                    String result = "";
                    if (exception == null) {
                        double percentage = (double) downloadPercentage;
                        int peers = (int) numPeers;
                        String numPeersString = "Nr. of Bitcoin network peers: " + peers;

                        btcSyncProgress.set(percentage);
                        if (percentage == 1) {
                            result = numPeersString + " / synchronized with " + btcNetworkAsString;
                            btcSplashSyncIconId.set("image-connection-synced");
                        } else if (percentage > 0.0) {
                            result = numPeersString + " / synchronizing with " + btcNetworkAsString + ": " + formatter.formatToPercentWithSymbol(percentage);
                        } else {
                            result = numPeersString + " / connecting to " + btcNetworkAsString;
                        }
                    } else {
                        result = "Nr. of Bitcoin network peers: " + numBtcPeers + " / connecting to " + btcNetworkAsString + " failed";
                        if (exception instanceof TimeoutException) {
                            walletServiceErrorMsg.set("Connecting to the bitcoin network failed because of a timeout.");
                        } else if (exception.getCause() instanceof BlockStoreException) {
                            log.error(exception.getMessage());
                            // Ugly, but no other way to cover that specific case
                            if (exception.getMessage().equals("Store file is already locked by another process"))
                                new Popup().warning("Bitsquare is already running. You cannot run 2 instances of Bitsquare.")
                                        .closeButtonText("Shut down")
                                        .onClose(BitsquareApp.shutDownHandler::run)
                                        .show();
                            else
                                new Popup().error("Cannot open wallet because of an exception:\n" + exception.getMessage())
                                        .show();
                        } else if (exception.getMessage() != null) {
                            walletServiceErrorMsg.set("Connection to the bitcoin network failed because of an error:" + exception.getMessage());
                        } else {
                            walletServiceErrorMsg.set("Connection to the bitcoin network failed because of an error:" + exception.toString());
                        }
                    }
                    return result;

                });
        btcInfoBinding.subscribe((observable, oldValue, newValue) -> {
            btcInfo.set(newValue);
        });

        walletService.initialize(null,
                () -> {
                    numBtcPeers = walletService.numPeersProperty().get();

                    if (walletService.getWallet().isEncrypted()) {
                        if (p2pNetWorkReady.get())
                            splashP2PNetworkAnimationVisible.set(false);

                        walletPasswordWindow
                                .onAesKey(aesKey -> {
                                    walletService.setAesKey(aesKey);
                                    tradeWalletService.setAesKey(aesKey);
                                    walletInitialized.set(true);
                                })
                                .hideCloseButton()
                                .show();
                    } else {
                        walletInitialized.set(true);
                    }
                },
                walletServiceException::set);
    }

    private void onAllServicesInitialized() {
        Log.traceCall();

        clock.start();

        // disputeManager
        disputeManager.onAllServicesInitialized();
        disputeManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) change -> {
            change.next();
            onDisputesChangeListener(change.getAddedSubList(), change.getRemoved());
        });
        onDisputesChangeListener(disputeManager.getDisputesAsObservableList(), null);

        // tradeManager
        tradeManager.onAllServicesInitialized();
        tradeManager.getTrades().addListener((ListChangeListener<Trade>) c -> updateBalance());
        tradeManager.getTrades().addListener((ListChangeListener<Trade>) change -> onTradesChanged());
        onTradesChanged();
        // We handle the trade period here as we display a global popup if we reached dispute time
        tradesAndUIReady = EasyBind.combine(isSplashScreenRemoved, tradeManager.pendingTradesInitializedProperty(), (a, b) -> a && b);
        tradesAndUIReady.subscribe((observable, oldValue, newValue) -> {
            if (newValue)
                applyTradePeriodState();
        });

        // walletService
        walletService.addBalanceListener(new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();
            }
        });

        openOfferManager.getOpenOffers().addListener((ListChangeListener<OpenOffer>) c -> updateBalance());
        tradeManager.getTrades().addListener((ListChangeListener<Trade>) c -> updateBalance());
        openOfferManager.onAllServicesInitialized();
        arbitratorManager.onAllServicesInitialized();
        alertManager.alertMessageProperty().addListener((observable, oldValue, newValue) -> displayAlertIfPresent(newValue));
        privateNotificationManager.privateNotificationProperty().addListener((observable, oldValue, newValue) -> displayPrivateNotification(newValue));
        displayAlertIfPresent(alertManager.alertMessageProperty().get());

        p2PService.onAllServicesInitialized();

        setupBtcNumPeersWatcher();
        setupP2PNumPeersWatcher();
        updateBalance();
        if (DevFlags.DEV_MODE) {
            preferences.setShowOwnOffersInOfferBook(true);
            if (user.getPaymentAccounts().isEmpty())
                setupDevDummyPaymentAccounts();
        }
        setupMarketPriceFeed();
        swapPendingOfferFundingEntries();
        fillPriceFeedComboBoxItems();

        showAppScreen.set(true);


        // We want to test if the client is compiled with the correct crypto provider (BountyCastle) 
        // and if the unlimited Strength for cryptographic keys is set.
        // If users compile themselves they might miss that step and then would get an exception in the trade.
        // To avoid that we add here at startup a sample encryption and signing to see if it don't causes an exception.
        // See: https://github.com/bitsquare/bitsquare/blob/master/doc/build.md#7-enable-unlimited-strength-for-cryptographic-keys
        Thread checkCryptoThread = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.currentThread().setName("checkCryptoThread");
                    log.trace("Run crypto test");
                    // just use any simple dummy msg
                    io.bitsquare.p2p.peers.keepalive.messages.Ping payload = new Ping(1, 1);
                    SealedAndSigned sealedAndSigned = Encryption.encryptHybridWithSignature(payload,
                            keyRing.getSignatureKeyPair(), keyRing.getPubKeyRing().getEncryptionPubKey());
                    DecryptedDataTuple tuple = Encryption.decryptHybridWithSignature(sealedAndSigned, keyRing.getEncryptionKeyPair().getPrivate());
                    if (tuple.payload instanceof Ping &&
                            ((Ping) tuple.payload).nonce == payload.nonce &&
                            ((Ping) tuple.payload).lastRoundTripTime == payload.lastRoundTripTime)
                        log.debug("Crypto test succeeded");
                    else
                        throw new CryptoException("Payload not correct after decryption");
                } catch (CryptoException e) {
                    e.printStackTrace();
                    String msg = "Seems that you use a self compiled binary and have not following the build " +
                            "instructions in https://github.com/bitsquare/bitsquare/blob/master/doc/build.md#7-enable-unlimited-strength-for-cryptographic-keys.\n\n" +
                            "If that is not the case and you use the official Bitsquare binary, " +
                            "please file a bug report to the Github page.\n" +
                            "Error=" + e.getMessage();
                    log.error(msg);
                    UserThread.execute(() -> new Popup<>().warning(msg)
                            .actionButtonText("Shut down")
                            .onAction(BitsquareApp.shutDownHandler::run)
                            .closeButtonText("Report bug at Github issues")
                            .onClose(() -> GUIUtil.openWebPage("https://github.com/bitsquare/bitsquare/issues"))
                            .show());
                }
            }
        };
        checkCryptoThread.start();

        if (Security.getProvider("BC") == null) {
            new Popup<>().warning("There is a problem with the crypto libraries. BountyCastle is not available.")
                    .actionButtonText("Shut down")
                    .onAction(BitsquareApp.shutDownHandler::run)
                    .closeButtonText("Report bug at Github issues")
                    .onClose(() -> GUIUtil.openWebPage("https://github.com/bitsquare/bitsquare/issues"))
                    .show();
        }

        String remindPasswordAndBackupKey = "remindPasswordAndBackup";
        user.getPaymentAccountsAsObservable().addListener((SetChangeListener<PaymentAccount>) change -> {
            if (preferences.showAgain(remindPasswordAndBackupKey) && change.wasAdded()) {
                new Popup<>().headLine("Important security recommendation")
                        .information("We would like to remind you to consider using password protection for your wallet if you have not already enabled that.\n\n" +
                                "It is also highly recommended to write down the wallet seed words. Those seed words are like a master password for recovering your Bitcoin wallet.\n" +
                                "At the \"Wallet Seed\" section you find more information.\n\n" +
                                "Additionally you can backup the complete application data folder at the \"Backup\" section.\n" +
                                "Please note, that this backup is not encrypted!")
                        .dontShowAgainId(remindPasswordAndBackupKey, preferences)
                        .show();
            }
        });
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
        tradeManager.getTrades().stream().forEach(trade -> {
            if (trade.getState().getPhase().ordinal() < Trade.Phase.PAYOUT_PAID.ordinal()) {
                Date maxTradePeriodDate = trade.getMaxTradePeriodDate();
                Date halfTradePeriodDate = trade.getHalfTradePeriodDate();
                if (maxTradePeriodDate != null && halfTradePeriodDate != null) {
                    Date now = new Date();
                    if (now.after(maxTradePeriodDate))
                        trade.setTradePeriodState(Trade.TradePeriodState.TRADE_PERIOD_OVER);
                    else if (now.after(halfTradePeriodDate))
                        trade.setTradePeriodState(Trade.TradePeriodState.HALF_REACHED);

                    String key;
                    switch (trade.getTradePeriodState()) {
                        case NORMAL:
                            break;
                        case HALF_REACHED:
                            key = "displayHalfTradePeriodOver" + trade.getId();
                            if (preferences.showAgain(key)) {
                                preferences.dontShowAgain(key, true);
                                new Popup().warning("Your trade with ID " + trade.getShortId() +
                                        " has reached the half of the max. allowed trading period and " +
                                        "is still not completed.\n\n" +
                                        "The trade period ends on " + formatter.formatDateTime(maxTradePeriodDate) + "\n\n" +
                                        "Please check your trade state at \"Portfolio/Open trades\" for further information.")
                                        .show();
                            }
                            break;
                        case TRADE_PERIOD_OVER:
                            key = "displayTradePeriodOver" + trade.getId();
                            if (preferences.showAgain(key)) {
                                preferences.dontShowAgain(key, true);
                                new Popup().warning("Your trade with ID " + trade.getShortId() +
                                        " has reached the max. allowed trading period and is " +
                                        "not completed.\n\n" +
                                        "The trade period ended on " + formatter.formatDateTime(maxTradePeriodDate) + "\n\n" +
                                        "Please check your trade at \"Portfolio/Open trades\" for contacting " +
                                        "the arbitrator.")
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
                        p2pNetworkWarnMsg.set("You lost the connection to all P2P network peers.\n" +
                                "Maybe you lost your internet connection or your computer was in standby mode.");
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
        walletService.numPeersProperty().addListener((observable, oldValue, newValue) -> {
            int numPeers = (int) newValue;
            if ((int) oldValue > 0 && numPeers == 0) {
                if (checkNumberOfBtcPeersTimer != null)
                    checkNumberOfBtcPeersTimer.stop();

                checkNumberOfBtcPeersTimer = UserThread.runAfter(() -> {
                    // check again numPeers
                    if (walletService.numPeersProperty().get() == 0) {
                        walletServiceErrorMsg.set("You lost the connection to all bitcoin network peers.\n" +
                                "Maybe you lost your internet connection or your computer was in standby mode.");
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
        if (priceFeedService.getCurrencyCode() == null)
            priceFeedService.setCurrencyCode(preferences.getPreferredTradeCurrency().getCode());
        if (priceFeedService.getType() == null)
            priceFeedService.setType(PriceFeedService.Type.LAST);
        priceFeedService.init(price -> marketPrice.set(formatter.formatMarketPrice(price, priceFeedService.getCurrencyCode())),
                (errorMessage, throwable) -> marketPrice.set("N/A"));
        marketPriceCurrencyCode.bind(priceFeedService.currencyCodeProperty());
        typeProperty.bind(priceFeedService.typeProperty());

        marketPriceBinding = EasyBind.combine(
                marketPriceCurrencyCode, marketPrice,
                (currencyCode, price) -> formatter.getCurrencyPair(currencyCode) + ": " + price);

        marketPriceBinding.subscribe((observable, oldValue, newValue) -> {
            if (newValue != null && !newValue.equals(oldValue)) {
                setMarketPriceInItems();

                String code = preferences.getUseStickyMarketPrice() ?
                        preferences.getPreferredTradeCurrency().getCode() :
                        priceFeedService.currencyCodeProperty().get();
                Optional<PriceFeedComboBoxItem> itemOptional = findPriceFeedComboBoxItem(code);
                if (itemOptional.isPresent()) {
                    if (selectedPriceFeedComboBoxItemProperty.get() == null || !preferences.getUseStickyMarketPrice()) {
                        itemOptional.get().setDisplayString(newValue);
                        selectedPriceFeedComboBoxItemProperty.set(itemOptional.get());
                    }
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

        priceFeedAllLoadedSubscription = EasyBind.subscribe(priceFeedService.currenciesUpdateFlagProperty(), newPriceUpdate -> setMarketPriceInItems());

        preferences.getTradeCurrenciesAsObservable().addListener((ListChangeListener<TradeCurrency>) c -> {
            UserThread.runAfter(() -> {
                fillPriceFeedComboBoxItems();
                setMarketPriceInItems();
            }, 100, TimeUnit.MILLISECONDS);
        });
    }

    private void setMarketPriceInItems() {
        priceFeedComboBoxItems.stream().forEach(item -> {
            String currencyCode = item.currencyCode;
            MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
            String priceString;
            if (marketPrice != null) {
                double price = marketPrice.getPrice(priceFeedService.getType());
                if (price != 0) {
                    priceString = formatter.formatMarketPrice(price, currencyCode);
                    item.setIsPriceAvailable(true);
                } else {
                    priceString = "N/A";
                    item.setIsPriceAvailable(false);
                }
            } else {
                priceString = "N/A";
                item.setIsPriceAvailable(false);
            }
            item.setDisplayString(formatter.getCurrencyPair(currencyCode) + ": " + priceString);
        });
    }

    public void setPriceFeedComboBoxItem(PriceFeedComboBoxItem item) {
        if (!preferences.getUseStickyMarketPrice() && item != null) {
            Optional<PriceFeedComboBoxItem> itemOptional = findPriceFeedComboBoxItem(priceFeedService.currencyCodeProperty().get());
            if (itemOptional.isPresent())
                selectedPriceFeedComboBoxItemProperty.set(itemOptional.get());
            else
                findPriceFeedComboBoxItem(preferences.getPreferredTradeCurrency().getCode())
                        .ifPresent(item2 -> selectedPriceFeedComboBoxItemProperty.set(item2));

            priceFeedService.setCurrencyCode(item.currencyCode);
        } else if (item != null) {
            selectedPriceFeedComboBoxItemProperty.set(item);
            priceFeedService.setCurrencyCode(item.currencyCode);
        } else {
            findPriceFeedComboBoxItem(preferences.getPreferredTradeCurrency().getCode())
                    .ifPresent(item2 -> selectedPriceFeedComboBoxItemProperty.set(item2));
        }

        // Need a delay a bit as we get item.isPriceAvailable() set after that call. 
        // (In case we add a new currency in settings)
        UserThread.runAfter(() -> {
            if (item != null) {
                String code = item.currencyCode;
                isFiatCurrencyPriceFeedSelected.set(CurrencyUtil.isFiatCurrency(code) && CurrencyUtil.getFiatCurrency(code).isPresent() && item.isPriceAvailable());
                isCryptoCurrencyPriceFeedSelected.set(CurrencyUtil.isCryptoCurrency(code) && CurrencyUtil.getCryptoCurrency(code).isPresent() && item.isPriceAvailable());
            }
        }, 100, TimeUnit.MILLISECONDS);
    }

    Optional<PriceFeedComboBoxItem> findPriceFeedComboBoxItem(String currencyCode) {
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
        if (alert != null &&
                !alreadyDisplayed &&
                (!alert.isUpdateInfo || alert.isNewVersion()))
            new DisplayAlertMessageWindow().alertMessage(alert).show();
    }

    private void displayPrivateNotification(PrivateNotification privateNotification) {
        new Popup<>().headLine("Important private notification!")
                .attention(privateNotification.message)
                .setHeadlineStyle("-fx-text-fill: -bs-error-red;  -fx-font-weight: bold;  -fx-font-size: 16;")
                .onClose(() -> privateNotificationManager.removePrivateNotification())
                .closeButtonText("I understand")
                .show();
    }

    private void swapPendingOfferFundingEntries() {
        tradeManager.getAddressEntriesForAvailableBalanceStream()
                .filter(addressEntry -> addressEntry.getOfferId() != null)
                .forEach(addressEntry -> walletService.swapTradeEntryToAvailableEntry(addressEntry.getOfferId(), AddressEntry.Context.OFFER_FUNDING));
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
                .mapToLong(addressEntry -> walletService.getBalanceForAddress(addressEntry.getAddress()).getValue())
                .sum());
        availableBalance.set(formatter.formatCoinWithCode(totalAvailableBalance));
    }

    private void updateReservedBalance() {
        Coin sum = Coin.valueOf(openOfferManager.getOpenOffers().stream()
                .map(openOffer -> {
                    Address address = walletService.getOrCreateAddressEntry(openOffer.getId(), AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
                    return walletService.getBalanceForAddress(address);
                })
                .mapToLong(Coin::getValue)
                .sum());

        reservedBalance.set(formatter.formatCoinWithCode(sum));
    }

    private void updateLockedBalance() {
        Coin sum = Coin.valueOf(tradeManager.getLockedTradeStream()
                .mapToLong(trade -> {
                    Coin lockedTradeAmount = walletService.getOrCreateAddressEntry(trade.getId(), AddressEntry.Context.MULTI_SIG).getLockedTradeAmount();
                    return lockedTradeAmount != null ? lockedTradeAmount.getValue() : 0;
                })
                .sum());
        lockedBalance.set(formatter.formatCoinWithCode(sum));
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
        long numPendingTrades = tradeManager.getTrades().size();
        if (numPendingTrades > 0)
            numPendingTradesAsString.set(String.valueOf(numPendingTrades));
        if (numPendingTrades > 9)
            numPendingTradesAsString.set("★");

        showPendingTradesNotification.set(numPendingTrades > 0);
    }

    private void setupDevDummyPaymentAccounts() {
        OKPayAccount okPayAccount = new OKPayAccount();
        okPayAccount.setAccountNr("dummy_" + new Random().nextInt(100));
        okPayAccount.setAccountName("OKPay dummy");
        okPayAccount.setSelectedTradeCurrency(CurrencyUtil.getDefaultTradeCurrency());
        user.addPaymentAccount(okPayAccount);

        CryptoCurrencyAccount cryptoCurrencyAccount = new CryptoCurrencyAccount();
        cryptoCurrencyAccount.setAccountName("ETH dummy");
        cryptoCurrencyAccount.setAddress("0x" + new Random().nextInt(1000000));
        cryptoCurrencyAccount.setSingleTradeCurrency(CurrencyUtil.getCryptoCurrency("ETH").get());
        user.addPaymentAccount(cryptoCurrencyAccount);
    }
}
