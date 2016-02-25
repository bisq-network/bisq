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
import io.bitsquare.app.BitsquareApp;
import io.bitsquare.app.Log;
import io.bitsquare.app.Version;
import io.bitsquare.arbitration.ArbitratorManager;
import io.bitsquare.arbitration.Dispute;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.pricefeed.MarketPriceFeed;
import io.bitsquare.common.Clock;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.components.BalanceTextField;
import io.bitsquare.gui.components.BalanceWithConfirmationTextField;
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.main.notifications.NotificationCenter;
import io.bitsquare.gui.main.popups.DisplayAlertMessagePopup;
import io.bitsquare.gui.main.popups.Popup;
import io.bitsquare.gui.main.popups.TacPopup;
import io.bitsquare.gui.main.popups.WalletPasswordPopup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
import io.bitsquare.p2p.network.CloseConnectionReason;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.ConnectionListener;
import io.bitsquare.payment.OKPayAccount;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.OpenOffer;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import javafx.beans.property.*;
import javafx.collections.ListChangeListener;
import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStoreException;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainViewModel implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(MainViewModel.class);

    private final WalletService walletService;
    private final TradeWalletService tradeWalletService;
    private final ArbitratorManager arbitratorManager;
    private final P2PService p2PService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final DisputeManager disputeManager;
    private final Preferences preferences;
    private final AlertManager alertManager;
    private final WalletPasswordPopup walletPasswordPopup;
    private final NotificationCenter notificationCenter;
    private final TacPopup tacPopup;
    private Clock clock;
    private final Navigation navigation;
    private final BSFormatter formatter;

    // BTC network
    final StringProperty btcInfo = new SimpleStringProperty("Initializing");
    final DoubleProperty btcSyncProgress = new SimpleDoubleProperty(-1);
    final StringProperty walletServiceErrorMsg = new SimpleStringProperty();
    final StringProperty btcSplashSyncIconId = new SimpleStringProperty();
    final StringProperty marketPrice = new SimpleStringProperty("N/A");
    final StringProperty marketPriceCurrency = new SimpleStringProperty("");
    final ObjectProperty<MarketPriceFeed.Type> typeProperty = new SimpleObjectProperty<>(MarketPriceFeed.Type.LAST);
    final StringProperty availableBalance = new SimpleStringProperty();
    final StringProperty reservedBalance = new SimpleStringProperty();
    final StringProperty lockedBalance = new SimpleStringProperty();
    private MonadicBinding<String> btcInfoBinding;

    // P2P network
    final StringProperty p2PNetworkInfo = new SimpleStringProperty();
    private MonadicBinding<String> p2PNetworkInfoBinding;
    final DoubleProperty splashP2PNetworkProgress = new SimpleDoubleProperty(-1);
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
    private final MarketPriceFeed marketPriceFeed;
    private final User user;
    private int numBtcPeers = 0;
    private Timer checkNumberOfBtcPeersTimer;
    private Timer checkNumberOfP2pNetworkPeersTimer;
    private Timer startupTimeout;
    private final Map<String, Subscription> disputeIsClosedSubscriptionsMap = new HashMap<>();
    private Subscription downloadPercentageSubscription;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MainViewModel(WalletService walletService, TradeWalletService tradeWalletService,
                         MarketPriceFeed marketPriceFeed,
                         ArbitratorManager arbitratorManager, P2PService p2PService, TradeManager tradeManager,
                         OpenOfferManager openOfferManager, DisputeManager disputeManager, Preferences preferences,
                         User user, AlertManager alertManager, WalletPasswordPopup walletPasswordPopup,
                         NotificationCenter notificationCenter, TacPopup tacPopup, Clock clock,
                         Navigation navigation, BSFormatter formatter) {
        this.marketPriceFeed = marketPriceFeed;
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
        this.walletPasswordPopup = walletPasswordPopup;
        this.notificationCenter = notificationCenter;
        this.tacPopup = tacPopup;
        this.clock = clock;
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

        BooleanProperty walletInitialized = initBitcoinWallet();
        BooleanProperty p2pNetWorkReady = initP2PNetwork();

        // need to store it to not get garbage collected
        allServicesDone = EasyBind.combine(walletInitialized, p2pNetWorkReady, (a, b) -> a && b);
        allServicesDone.subscribe((observable, oldValue, newValue) -> {
            if (newValue)
                onAllServicesInitialized();
        });

        startupTimeout = UserThread.runAfter(() -> {
            log.warn("startupTimeout called");
            MainView.blur();
            new Popup().warning("The application could not startup after 3 minutes.\n" +
                    "There might be some network connection problems or a unstable Tor path.\n\n" +
                    "Please restart and try again.")
                    .actionButtonText("Shut down and start again")
                    .onAction(BitsquareApp.shutDownHandler::run)
                    .show();
        }, 3, TimeUnit.MINUTES);
        
        /*startupTimeout = FxTimer.runLater(Duration.ofMinutes(3), () -> {
            log.warn("startupTimeout called");
            MainView.blur();
            new Popup().warning("The application could not startup after 3 minutes.\n" +
                    "There might be some network connection problems or a unstable Tor path.\n\n" +
                    "Please restart and try again.")
                    .closeButtonText("Shut down")
                    .onClose(BitsquareApp.shutDownHandler::run)
                    .show();
        });*/
    }

    public void shutDown() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BooleanProperty initP2PNetwork() {
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
                    new Popup()
                            .warning("You got disconnected from a seed node.\n\n" +
                                    "Reason for getting disconnected: " + connection.getRuleViolation().name() + "\n\n" +
                                    "It might be that your installed version is not compatible with " +
                                    "the network.\n\n" +
                                    "Please check if you run the latest software version.\n" +
                                    "You can download the latest version of Bitsquare at:\n" +
                                    "https://github.com/bitsquare/bitsquare/releases")
                            .show();
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
                bootstrapState.set("Tor node created");
                p2PNetworkIconId.set("image-connection-tor");
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
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoSeedNodeAvailable() {
                if (p2PService.getNumConnectedPeers().get() == 0)
                    bootstrapWarning.set("No seed nodes available");
                else
                    bootstrapWarning.set(null);

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
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onBootstrapComplete() {
                splashP2PNetworkProgress.set(1);
                bootstrapComplete.set(true);
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                p2pNetworkWarnMsg.set("Connecting to the P2P network failed (reported error: "
                        + throwable.getMessage() + ").\n" +
                        "Please check your internet connection or try to restart the application.");
                splashP2PNetworkProgress.set(0);
                bootstrapWarning.set("Bootstrapping to P2P network failed");
                p2pNetworkLabelId.set("splash-error-state-msg");
            }
        });

        return p2pNetworkInitialized;
    }

    private BooleanProperty initBitcoinWallet() {
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
                            result = numPeersString + " / synchronizing with " + btcNetworkAsString + ": " + formatter.formatToPercent(percentage);
                        } else {
                            result = numPeersString + " / connecting to " + btcNetworkAsString;
                        }
                    } else {
                        result = "Nr. of Bitcoin network peers: " + numBtcPeers + " / connecting to " + btcNetworkAsString + " failed";
                        if (exception instanceof TimeoutException) {
                            walletServiceErrorMsg.set("Connecting to the bitcoin network failed because of a timeout.");
                        } else if (exception.getCause() instanceof BlockStoreException) {
                            new Popup().warning("Bitsquare is already running. You cannot run 2 instances of Bitsquare.")
                                    .closeButtonText("Shut down")
                                    .onClose(BitsquareApp.shutDownHandler::run)
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

        final BooleanProperty walletInitialized = new SimpleBooleanProperty();
        walletService.initialize(null,
                () -> {
                    numBtcPeers = walletService.numPeersProperty().get();
                    walletInitialized.set(true);
                },
                walletServiceException::set);
        return walletInitialized;
    }

    private void onAllServicesInitialized() {
        Log.traceCall();

        clock.start();

        startupTimeout.stop();

        // disputeManager
        disputeManager.onAllServicesInitialized();
        disputeManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) change -> {
            change.next();
            onDisputesChangeListener(change.getAddedSubList(), change.getRemoved());
        });
        onDisputesChangeListener(disputeManager.getDisputesAsObservableList(), null);

        // tradeManager
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
        // In case we have any offers open or a pending trade we need to unlock our trading wallet so a trade can be executed automatically
        // Otherwise we delay the password request to create offer, or take offer.
        // When the password is set it will be stored to the tradeWalletService as well, so its only needed after a restart.
        if (walletService.getWallet().isEncrypted() &&
                (openOfferManager.getOpenOffers().size() > 0
                        || tradeManager.getTrades().size() > 0
                        || disputeManager.getDisputesAsObservableList().size() > 0)) {
            walletPasswordPopup.onAesKey(aesKey -> tradeWalletService.setAesKey(aesKey)).show();
        }
        walletService.addBalanceListener(new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance, Transaction tx) {
                updateBalance();
            }
        });

        openOfferManager.getOpenOffers().addListener((ListChangeListener<OpenOffer>) c -> updateBalance());
        openOfferManager.onAllServicesInitialized();
        arbitratorManager.onAllServicesInitialized();
        alertManager.alertMessageProperty().addListener((observable, oldValue, newValue) -> displayAlertIfPresent(newValue));
        displayAlertIfPresent(alertManager.alertMessageProperty().get());

        setupBtcNumPeersWatcher();
        setupP2PNumPeersWatcher();
        updateBalance();
        setupDevDummyPaymentAccount();
        setupMarketPriceFeed();

        tacPopup.showIfNeeded();

        showAppScreen.set(true);
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
        tradeWalletService.addBlockChainListener(new BlockChainListener() {
            @Override
            public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
                updateTradePeriodState();
            }

            @Override
            public void reorganize(StoredBlock splitPoint, List<StoredBlock> oldBlocks, List<StoredBlock> newBlocks)
                    throws VerificationException {
            }

            @Override
            public boolean isTransactionRelevant(Transaction tx) throws ScriptException {
                return false;
            }

            @Override
            public void receiveFromBlock(Transaction tx, StoredBlock block, AbstractBlockChain.NewBlockType blockType, int relativityOffset)
                    throws VerificationException {

            }

            @Override
            public boolean notifyTransactionIsInBlock(Sha256Hash txHash, StoredBlock block, AbstractBlockChain.NewBlockType blockType,
                                                      int relativityOffset) throws VerificationException {
                return false;
            }
        });
    }

    private void updateTradePeriodState() {
        tradeManager.getTrades().stream().forEach(trade -> {
            int bestChainHeight = tradeWalletService.getBestChainHeight();

            if (trade.getOpenDisputeTimeAsBlockHeight() > 0 && bestChainHeight >= trade.getOpenDisputeTimeAsBlockHeight())
                trade.setTradePeriodState(Trade.TradePeriodState.TRADE_PERIOD_OVER);
            else if (trade.getCheckPaymentTimeAsBlockHeight() > 0 && bestChainHeight >= trade.getCheckPaymentTimeAsBlockHeight())
                trade.setTradePeriodState(Trade.TradePeriodState.HALF_REACHED);

            String id;
            String limitDate = formatter.addBlocksToNowDateFormatted(trade.getOpenDisputeTimeAsBlockHeight() - tradeWalletService.getBestChainHeight());
            switch (trade.getTradePeriodState()) {
                case NORMAL:
                    break;
                case HALF_REACHED:
                    id = "displayHalfTradePeriodOver" + trade.getId();
                    if (preferences.showAgain(id) && !BitsquareApp.DEV_MODE) {
                        preferences.dontShowAgain(id);
                        new Popup().warning("Your trade with ID " + trade.getShortId() +
                                " has reached the half of the max. allowed trading period and " +
                                "is still not completed.\n\n" +
                                "The trade period ends on " + limitDate + "\n\n" +
                                "Please check your trade state at \"Portfolio/Open trades\" for further information.")
                                .show();
                    }
                    break;
                case TRADE_PERIOD_OVER:
                    id = "displayTradePeriodOver" + trade.getId();
                    if (preferences.showAgain(id) && !BitsquareApp.DEV_MODE) {
                        preferences.dontShowAgain(id);
                        new Popup().warning("Your trade with ID " + trade.getShortId() +
                                " has reached the max. allowed trading period and is " +
                                "not completed.\n\n" +
                                "The trade period ended on " + limitDate + "\n\n" +
                                "Please check your trade at \"Portfolio/Open trades\" for contacting " +
                                "the arbitrator.")
                                .show();
                    }
                    break;
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
        if (marketPriceFeed.getCurrencyCode() == null)
            marketPriceFeed.setCurrencyCode(preferences.getPreferredTradeCurrency().getCode());
        if (marketPriceFeed.getType() == null)
            marketPriceFeed.setType(MarketPriceFeed.Type.LAST);
        marketPriceFeed.init(price -> {
                    marketPrice.set(formatter.formatMarketPrice(price));
                },
                (errorMessage, throwable) -> {
                    marketPrice.set("N/A");
                });
        marketPriceCurrency.bind(marketPriceFeed.currencyCodeProperty());
        typeProperty.bind(marketPriceFeed.typeProperty());
    }

    private void displayAlertIfPresent(Alert alert) {
        boolean alreadyDisplayed = alert != null && alert.equals(user.getDisplayedAlert());
        user.setDisplayedAlert(alert);

        if (alert != null && !alreadyDisplayed) {
            new DisplayAlertMessagePopup().alertMessage(alert).show();
        }
    }

    private void updateBalance() {
        updateAvailableBalance();
        updateReservedBalance();
        updateLockedBalance();
    }

    private void updateReservedBalance() {
        Coin sum = Coin.valueOf(Stream.concat(openOfferManager.getOpenOffers().stream(), tradeManager.getTrades().stream())
                .map(tradable -> walletService.getAddressEntryByOfferId(tradable.getId()))
                .map(addressEntry -> walletService.getBalanceForAddress(addressEntry.getAddress()))
                .mapToLong(Coin::getValue)
                .sum());
        reservedBalance.set(formatter.formatCoinWithCode(sum));
    }

    private void updateLockedBalance() {
        Coin sum = Coin.valueOf(tradeManager.getTrades().stream()
                .map(trade -> {
                    switch (trade.getState().getPhase()) {
                        case DEPOSIT_REQUESTED:
                        case DEPOSIT_PAID:
                        case FIAT_SENT:
                        case FIAT_RECEIVED:
                            Coin balanceInDeposit = FeePolicy.getSecurityDeposit().add(FeePolicy.getFeePerKb());
                            if (trade.getContract() != null &&
                                    trade.getTradeAmount() != null &&
                                    trade.getContract().getSellerPayoutAddressString()
                                            .equals(walletService.getAddressEntryByOfferId(trade.getId()).getAddressString())) {
                                balanceInDeposit = balanceInDeposit.add(trade.getTradeAmount());
                            }
                            return balanceInDeposit;
                        default:
                            return Coin.ZERO;
                    }
                })
                .mapToLong(Coin::getValue)
                .sum());
        lockedBalance.set(formatter.formatCoinWithCode(sum));
    }

    private void updateAvailableBalance() {
        List<AddressEntry> result = new ArrayList<>();

        List<String> reservedTrades = Stream.concat(openOfferManager.getOpenOffers().stream(), tradeManager.getTrades().stream())
                .map(tradable -> tradable.getOffer().getId())
                .collect(Collectors.toList());

        result.addAll(walletService.getAddressEntryList().stream()
                .filter(e -> walletService.getBalanceForAddress(e.getAddress()).isPositive())
                .filter(e -> !reservedTrades.contains(e.getOfferId()))
                .collect(Collectors.toList()));

        Optional<Coin> totalAvailableOptional = result.stream().map(e -> walletService.getBalanceForAddress(e.getAddress())).reduce(Coin::add);
        if (totalAvailableOptional.isPresent())
            availableBalance.set(formatter.formatCoinWithCode(totalAvailableOptional.get()));
        else
            availableBalance.set(formatter.formatCoinWithCode(Coin.ZERO));
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
            if (disputeIsClosedSubscriptionsMap.containsKey(id)) {
                log.warn("We have already an entry in disputeStateSubscriptionsMap. That should never happen.");
            } else {
                Subscription disputeStateSubscription = EasyBind.subscribe(dispute.isClosedProperty(),
                        disputeState -> {
                            int openDisputes = disputeManager.getDisputesAsObservableList().stream()
                                    .filter(e -> !e.isClosed())
                                    .collect(Collectors.toList()).size();
                            if (openDisputes > 0)
                                numOpenDisputesAsString.set(String.valueOf(openDisputes));
                            if (openDisputes > 9)
                                numOpenDisputesAsString.set("*");

                            showOpenDisputesNotification.set(openDisputes > 0);
                        });
                disputeIsClosedSubscriptionsMap.put(id, disputeStateSubscription);
            }
        });
    }

    private void onTradesChanged() {
        long numPendingTrades = tradeManager.getTrades().size();
        if (numPendingTrades > 0)
            numPendingTradesAsString.set(String.valueOf(numPendingTrades));
        if (numPendingTrades > 9)
            numPendingTradesAsString.set("*");

        showPendingTradesNotification.set(numPendingTrades > 0);
    }

    private void setupDevDummyPaymentAccount() {
        if (BitsquareApp.DEV_MODE && user.getPaymentAccounts().isEmpty()) {
            OKPayAccount okPayAccount = new OKPayAccount();
            okPayAccount.setAccountNr("dummy");
            okPayAccount.setAccountName("OKPay dummy");
            okPayAccount.setSelectedTradeCurrency(CurrencyUtil.getDefaultTradeCurrency());
            okPayAccount.setCountry(CountryUtil.getDefaultCountry());
            user.addPaymentAccount(okPayAccount);
        }
    }
}
