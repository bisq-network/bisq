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
import io.bitsquare.btc.*;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.btc.pricefeed.MarketPriceFeed;
import io.bitsquare.common.UserThread;
import io.bitsquare.gui.Navigation;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.common.view.ViewPath;
import io.bitsquare.gui.components.BalanceTextField;
import io.bitsquare.gui.components.BalanceWithConfirmationTextField;
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.main.portfolio.PortfolioView;
import io.bitsquare.gui.main.portfolio.pendingtrades.PendingTradesView;
import io.bitsquare.gui.popups.DisplayAlertMessagePopup;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.popups.WalletPasswordPopup;
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
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import org.bitcoinj.core.*;
import org.bitcoinj.store.BlockStoreException;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.reactfx.util.FxTimer;
import org.reactfx.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private Navigation navigation;
    private final BSFormatter formatter;

    // BTC network
    final StringProperty btcSplashInfo = new SimpleStringProperty("Initializing");
    final StringProperty btcFooterInfo = new SimpleStringProperty("Initializing");
    final DoubleProperty btcSyncProgress = new SimpleDoubleProperty(-1);
    final StringProperty walletServiceErrorMsg = new SimpleStringProperty();
    final StringProperty btcSplashSyncIconId = new SimpleStringProperty();
    final StringProperty marketPrice = new SimpleStringProperty("N/A");
    final StringProperty marketPriceCurrency = new SimpleStringProperty("");
    final ObjectProperty<MarketPriceFeed.Type> typeProperty = new SimpleObjectProperty<>(MarketPriceFeed.Type.LAST);
    final StringProperty availableBalance = new SimpleStringProperty();
    final StringProperty reservedBalance = new SimpleStringProperty();
    final StringProperty lockedBalance = new SimpleStringProperty();

    // P2P network
    final StringProperty p2PNetworkInfo = new SimpleStringProperty();
    final DoubleProperty splashP2PNetworkProgress = new SimpleDoubleProperty(-1);
    final StringProperty p2PNetworkWarnMsg = new SimpleStringProperty();
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
    final StringProperty p2PNetworkLabelId = new SimpleStringProperty("footer-pane");

    private MonadicBinding<Boolean> allServicesDone, tradesAndUIReady;
    private MarketPriceFeed marketPriceFeed;
    private final User user;
    private int numBTCPeers = 0;
    private Timer checkForBtcSyncStateTimer;
    private ChangeListener<Number> numConnectedPeersListener, btcNumPeersListener;
    private java.util.Timer numberofBtcPeersTimer;
    private java.util.Timer numberofP2PNetworkPeersTimer;
    private Timer startupTimeout;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MainViewModel(WalletService walletService, TradeWalletService tradeWalletService,
                         MarketPriceFeed marketPriceFeed,
                         ArbitratorManager arbitratorManager, P2PService p2PService, TradeManager tradeManager,
                         OpenOfferManager openOfferManager, DisputeManager disputeManager, Preferences preferences,
                         User user, AlertManager alertManager, WalletPasswordPopup walletPasswordPopup,
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
        this.navigation = navigation;
        this.formatter = formatter;

        btcNetworkAsString = formatter.formatBitcoinNetwork(preferences.getBitcoinNetwork()) +
                (preferences.getUseTorForBitcoinJ() ? " (using Tor)" : "");

        TxIdTextField.setPreferences(preferences);
        TxIdTextField.setWalletService(walletService);
        BalanceTextField.setWalletService(walletService);
        BalanceWithConfirmationTextField.setWalletService(walletService);

        if (BitsquareApp.DEV_MODE) {
            preferences.setShowPlaceOfferConfirmation(false);
            preferences.setShowTakeOfferConfirmation(false);
            preferences.setUseAnimations(false);
            preferences.setUseEffects(false);
        }
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

        startupTimeout = FxTimer.runLater(Duration.ofMillis(60000), () -> {
            log.warn("startupTimeout called");
            MainView.blur();
            new Popup().warning("The application could not startup after 60 seconds.\n" +
                    "There might be some network connection problems.\n\n" +
                    "Please restart and try again.")
                    .closeButtonText("Shut down")
                    .onClose(BitsquareApp.shutDownHandler::run)
                    .show();
        });
    }

    public void shutDown() {
        if (numConnectedPeersListener != null)
            p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);

        if (btcNumPeersListener != null)
            walletService.numPeersProperty().removeListener(btcNumPeersListener);

        if (checkForBtcSyncStateTimer != null)
            checkForBtcSyncStateTimer.stop();

    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BooleanProperty initP2PNetwork() {
        final BooleanProperty p2pNetworkInitialized = new SimpleBooleanProperty();
        p2PNetworkInfo.set("Connecting to Tor network...");
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
                                    "https://github.com/bitsquare/bitsquare/releases/\n\n" +
                                    "")
                            .show();
                }
            }

            @Override
            public void onError(Throwable throwable) {
            }
        });
        p2PService.start(new P2PServiceListener() {
            @Override
            public void onTorNodeReady() {
                p2PNetworkInfo.set("Tor node created");
                p2PNetworkIconId.set("image-connection-tor");
            }

            @Override
            public void onHiddenServicePublished() {
                p2PNetworkInfo.set("Hidden Service published");
            }

            @Override
            public void onRequestingDataCompleted() {
                if (p2PService.getNumConnectedPeers().get() == 0) {
                    p2PNetworkInfo.set("Initial data received");
                } else {
                    updateP2pNetworkInfoWithPeersChanged(p2PService.getNumConnectedPeers().get());
                }
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoSeedNodeAvailable() {
                if (p2PService.getNumConnectedPeers().get() == 0) {
                    p2PNetworkInfo.set("No seed nodes available");
                }
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onNoPeersAvailable() {
                if (p2PService.getNumConnectedPeers().get() == 0) {
                    p2PNetworkWarnMsg.set("There are no seed nodes or persisted peers available for requesting data.\n" +
                            "Please check your internet connection or try to restart the application.");
                    p2PNetworkInfo.set("No seed nodes and peers available");
                    p2PNetworkLabelId.set("splash-error-state-msg");
                }
                p2pNetworkInitialized.set(true);
            }

            @Override
            public void onBootstrapComplete() {
                updateP2pNetworkInfoWithPeersChanged(p2PService.getNumConnectedPeers().get());
                splashP2PNetworkProgress.set(1);
                bootstrapComplete.set(true);
            }


            @Override
            public void onSetupFailed(Throwable throwable) {
                p2PNetworkWarnMsg.set("Connecting to the P2P network failed (reported error: "
                        + throwable.getMessage() + ").\n" +
                        "Please check your internet connection or try to restart the application.");
                splashP2PNetworkProgress.set(0);
                if (p2PService.getNumConnectedPeers().get() == 0)
                    p2PNetworkLabelId.set("splash-error-state-msg");
            }
        });

        return p2pNetworkInitialized;
    }

    private BooleanProperty initBitcoinWallet() {
        EasyBind.subscribe(walletService.downloadPercentageProperty(), newValue -> setBitcoinNetworkSyncProgress((double) newValue));

        btcNumPeersListener = (observable, oldValue, newValue) -> {
            if ((int) oldValue > 0 && (int) newValue == 0) {
                // give a bit of tolerance
                if (numberofBtcPeersTimer != null)
                    numberofBtcPeersTimer.cancel();
                numberofBtcPeersTimer = UserThread.runAfter(() -> {
                    if (walletService.numPeersProperty().get() == 0) {
                        walletServiceErrorMsg.set("You lost the connection to all bitcoin network peers.\n" +
                                "Maybe you lost your internet connection or your computer was in hibernate/sleep mode.");
                    } else {
                        walletServiceErrorMsg.set(null);
                    }
                }, 5);
            } else if ((int) oldValue == 0 && (int) newValue > 0) {
                walletServiceErrorMsg.set(null);
            }

            numBTCPeers = (int) newValue;
            setBitcoinNetworkSyncProgress(walletService.downloadPercentageProperty().get());
        };
        walletService.numPeersProperty().addListener(btcNumPeersListener);

        final BooleanProperty walletInitialized = new SimpleBooleanProperty();
        walletService.initialize(null,
                () -> {
                    log.trace("wallet initialized");
                    walletInitialized.set(true);
                },
                errorMessage -> setWalletServiceException(errorMessage));
        return walletInitialized;
    }

    private void onAllServicesInitialized() {
        Log.traceCall();

        startupTimeout.stop();

        // disputeManager
        disputeManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) change -> {
            change.next();
            addDisputeClosedChangeListener(change.getAddedSubList());
            updateDisputeStates();
        });
        addDisputeClosedChangeListener(disputeManager.getDisputesAsObservableList());
        updateDisputeStates();
        disputeManager.onAllServicesInitialized();


        // tradeManager
        tradeManager.getTrades().addListener((ListChangeListener<Trade>) c -> updateBalance());

        tradeManager.getTrades().addListener((ListChangeListener<Trade>) change -> {
            change.next();
            addDisputeStateListeners(change.getAddedSubList());
            addTradeStateListeners(change.getAddedSubList());
            pendingTradesChanged();
        });
        pendingTradesChanged();
        addDisputeStateListeners(tradeManager.getTrades());
        addTradeStateListeners(tradeManager.getTrades());


        // arbitratorManager
        arbitratorManager.onAllServicesInitialized();


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

        // We handle the trade period here as we display a global popup if we reached dispute time
        tradesAndUIReady = EasyBind.combine(isSplashScreenRemoved, tradeManager.pendingTradesInitializedProperty(), (a, b) -> a && b);
        tradesAndUIReady.subscribe((observable, oldValue, newValue) -> {
            if (newValue)
                applyTradePeriodState();
        });

        walletService.addBalanceListener(new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance) {
                updateBalance();
            }
        });
        updateBalance();

        setBitcoinNetworkSyncProgress(walletService.downloadPercentageProperty().get());
        checkPeriodicallyForBtcSyncState();

        // openOfferManager
        openOfferManager.getOpenOffers().addListener((ListChangeListener<OpenOffer>) c -> updateBalance());
        openOfferManager.onAllServicesInitialized();


        // alertManager
        alertManager.alertMessageProperty().addListener((observable, oldValue, newValue) -> displayAlertIfPresent(newValue));
        displayAlertIfPresent(alertManager.alertMessageProperty().get());


        // tac
        // TODO add link: https://bitsquare.io/arbitration_system.pdf
        String text = "1. This software is experimental and provided \"as is\", without warranty of any kind, " +
                "express or implied, including but not limited to the warranties of " +
                "merchantability, fitness for a particular purpose and non-infringement.\n" +
                "In no event shall the authors or copyright holders be liable for any claim, damages or other " +
                "liability, whether in an action of contract, tort or otherwise, " +
                "arising from, out of or in connection with the software or the use or other dealings in the software.\n\n" +
                "2. The user is responsible to use the software in compliance with local laws.\n\n" +
                "3. The user confirms that he has read and agreed to the rules defined in our " +
                "Wiki regrading the dispute process\n" +
                "(https://github.com/bitsquare/bitsquare/wiki/Arbitration-system).";
        if (!preferences.getTacAccepted() && !BitsquareApp.DEV_MODE) {
            new Popup().headLine("USER AGREEMENT")
                    .message(text)
                    .actionButtonText("I agree")
                    .closeButtonText("I disagree and quit")
                    .onAction(() -> {
                        preferences.setTacAccepted(true);
                        if (preferences.getBitcoinNetwork() == BitcoinNetwork.MAINNET)
                            UserThread.runAfter(() -> new Popup()
                                    .warning("This software is still in alpha version.\n" +
                                            "Please be aware that using Mainnet comes with the risk to lose funds " +
                                            "in case of software bugs.\n" +
                                            "To limit the possible losses the maximum allowed trading amount and the " +
                                            "security deposit have been reduced to 0.01 BTC for the alpha version " +
                                            "when using Mainnet.")
                                    .headLine("Important information!")
                                    .actionButtonText("I understand and want to use Mainnet")
                                    .closeButtonText("Restart and use Testnet")
                                    .onClose(() -> {
                                        UserThread.execute(() -> preferences.setBitcoinNetwork(BitcoinNetwork.TESTNET));
                                        UserThread.runAfter(BitsquareApp.shutDownHandler::run, 300, TimeUnit.MILLISECONDS);
                                    })
                                    .width(600)
                                    .show(), 300, TimeUnit.MILLISECONDS);
                    })
                    .onClose(BitsquareApp.shutDownHandler::run)
                    .show();
        }

        // update nr of peers in footer
        numConnectedPeersListener = (observable, oldValue, newValue) -> {
            if ((int) oldValue > 0 && (int) newValue == 0) {
                // give a bit of tolerance
                if (numberofP2PNetworkPeersTimer != null)
                    numberofP2PNetworkPeersTimer.cancel();
                numberofP2PNetworkPeersTimer = UserThread.runAfter(() -> {
                    if (p2PService.getNumConnectedPeers().get() == 0) {
                        p2PNetworkWarnMsg.set("You lost the connection to all P2P network peers.\n" +
                                "Maybe you lost your internet connection or your computer was in hibernate/sleep mode.");
                        p2PNetworkLabelId.set("splash-error-state-msg");
                    } else {
                        p2PNetworkWarnMsg.set(null);
                        p2PNetworkLabelId.set("footer-pane");
                    }
                }, 5);
            } else if ((int) oldValue == 0 && (int) newValue > 0) {
                p2PNetworkWarnMsg.set(null);
                p2PNetworkLabelId.set("footer-pane");
            }

            updateP2pNetworkInfoWithPeersChanged((int) newValue);
        };
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);

        // now show app
        showAppScreen.set(true);

        if (BitsquareApp.DEV_MODE && user.getPaymentAccounts().isEmpty()) {
            OKPayAccount okPayAccount = new OKPayAccount();
            okPayAccount.setAccountNr("dummy");
            okPayAccount.setAccountName("OKPay dummy");
            okPayAccount.setSelectedTradeCurrency(CurrencyUtil.getDefaultTradeCurrency());
            okPayAccount.setCountry(CountryUtil.getDefaultCountry());
            user.addPaymentAccount(okPayAccount);
        }

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

    private void checkPeriodicallyForBtcSyncState() {
        if (walletService.downloadPercentageProperty().get() == -1) {
            checkForBtcSyncStateTimer = FxTimer.runPeriodically(Duration.ofSeconds(10),
                    () -> {
                        log.info("Bitcoin blockchain sync still not started.");
                        setBitcoinNetworkSyncProgress(walletService.downloadPercentageProperty().get());
                    }
            );
        } else {
            stopCheckForBtcSyncStateTimer();
        }
    }

    private void updateP2pNetworkInfoWithPeersChanged(int numPeers) {
        p2PNetworkInfo.set("Nr. of connections: " + numPeers);
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

        Optional<Coin> totalAvailableOptional = result.stream().map(e -> walletService.getBalanceForAddress(e.getAddress())).reduce((a, b) -> a.add(b));
        if (totalAvailableOptional.isPresent())
            availableBalance.set(formatter.formatCoinWithCode(totalAvailableOptional.get()));
        else
            availableBalance.set(formatter.formatCoinWithCode(Coin.ZERO));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI callbacks
    ///////////////////////////////////////////////////////////////////////////////////////////

    void onSplashScreenRemoved() {
        isSplashScreenRemoved.set(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Apply states
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

    private void setWalletServiceException(Throwable error) {
        setBitcoinNetworkSyncProgress(0);
        btcSplashInfo.set("Nr. of peers: " + numBTCPeers + " / connecting to " + btcNetworkAsString + " failed");
        btcFooterInfo.set("Nr. of eers: " + numBTCPeers + " / connecting to " + btcNetworkAsString + " failed");
        if (error instanceof TimeoutException) {
            walletServiceErrorMsg.set("Connecting to the bitcoin network failed because of a timeout.");
        } else if (error.getCause() instanceof BlockStoreException) {
            new Popup().warning("Bitsquare is already running. You cannot run 2 instances of Bitsquare.")
                    .closeButtonText("Shut down")
                    .onClose(BitsquareApp.shutDownHandler::run)
                    .show();
        } else if (error.getMessage() != null) {
            walletServiceErrorMsg.set("Connection to the bitcoin network failed because of an error:" + error.getMessage());
        } else {
            walletServiceErrorMsg.set("Connection to the bitcoin network failed because of an error:" + error.toString());
        }
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
                    if (preferences.showAgain(id)) {
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
                    if (preferences.showAgain(id)) {
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

    private void addDisputeClosedChangeListener(List<? extends Dispute> list) {
        list.stream().forEach(e -> e.isClosedProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue)
                updateDisputeStates();
        }));
    }

    private void updateDisputeStates() {
        int openDisputes = disputeManager.getDisputesAsObservableList().stream().filter(e -> !e.isClosed()).collect(Collectors.toList()).size();
        if (openDisputes > 0)
            numOpenDisputesAsString.set(String.valueOf(openDisputes));
        if (openDisputes > 9)
            numOpenDisputesAsString.set("?");

        showOpenDisputesNotification.set(openDisputes > 0);
    }

    private void pendingTradesChanged() {
        long numPendingTrades = tradeManager.getTrades().size();
        if (numPendingTrades > 0)
            numPendingTradesAsString.set(String.valueOf(numPendingTrades));
        if (numPendingTrades > 9)
            numPendingTradesAsString.set("?");

        showPendingTradesNotification.set(numPendingTrades > 0);
    }

    private void addTradeStateListeners(List<? extends Trade> addedTrades) {
        addedTrades.stream().forEach(trade -> {
            Subscription tradeStateSubscription = EasyBind.subscribe(trade.stateProperty(), newValue -> {
                if (newValue != null) {
                    applyState(trade);
                }
            });
        });
        
                
        
       /* addedTrades.stream()
                .forEach(trade -> trade.stateProperty().addListener((observable, oldValue, newValue) -> {
                    String msg = "";
                    log.debug("addTradeStateListeners " + newValue);
                    switch (newValue) {
                        case PREPARATION:
                        case TAKER_FEE_PAID:
                        case DEPOSIT_PUBLISH_REQUESTED:
                        case DEPOSIT_PUBLISHED:
                        case DEPOSIT_SEEN_IN_NETWORK:
                        case DEPOSIT_PUBLISHED_MSG_SENT:
                        case DEPOSIT_PUBLISHED_MSG_RECEIVED:
                            break;
                        case DEPOSIT_CONFIRMED:
                            msg = newValue.name();
                            break;
                        case FIAT_PAYMENT_STARTED:
                            break;
                        case FIAT_PAYMENT_STARTED_MSG_SENT:
                            break;
                        case FIAT_PAYMENT_STARTED_MSG_RECEIVED:
                            break;

                        case FIAT_PAYMENT_RECEIPT:
                            break;
                        case FIAT_PAYMENT_RECEIPT_MSG_SENT:
                            break;
                        case FIAT_PAYMENT_RECEIPT_MSG_RECEIVED:
                            break;


                        case PAYOUT_TX_SENT:
                            break;
                        case PAYOUT_TX_RECEIVED:
                            break;
                        case PAYOUT_TX_COMMITTED:
                            break;
                        case PAYOUT_BROAD_CASTED:
                            break;

                        case WITHDRAW_COMPLETED:
                            break;

                        default:
                            log.warn("unhandled processState " + newValue);
                            break;
                    }

                    //new Popup().information(msg).show();

                }));*/
    }

    private void applyState(Trade trade) {
        Trade.State state = trade.getState();
        log.debug("addTradeStateListeners " + state);
        boolean isBtcBuyer = tradeManager.isMyOfferInBtcBuyerRole(trade.getOffer());
        String headLine = "Notification for trade with ID " + trade.getShortId();
        String message = null;
        String id = "notificationPopup_" + state + trade.getId();
        if (isBtcBuyer) {
            switch (state) {
                case DEPOSIT_PUBLISHED_MSG_RECEIVED:
                    message = "Your offer has been accepted by a seller.\n" +
                            "You need to wait for one blockchain confirmation before starting the payment.";
                    break;
                case DEPOSIT_CONFIRMED:
                    message = "The deposit transaction of your trade has got the first blockchain confirmation.\n" +
                            "You have to start the payment to the bitcoin seller now.";
                    break;
               /* case FIAT_PAYMENT_RECEIPT_MSG_RECEIVED:
                case PAYOUT_TX_COMMITTED:
                case PAYOUT_TX_SENT:*/
                case PAYOUT_BROAD_CASTED:
                    message = "The bitcoin seller has confirmed the receipt of your payment and the payout transaction has been published.\n" +
                            "The trade is now completed and you can withdraw your funds.";
                    break;
            }
        } else {
            switch (state) {
                case DEPOSIT_PUBLISHED_MSG_RECEIVED:
                    message = "Your offer has been accepted by a buyer.\n" +
                            "You need to wait for one blockchain confirmation before starting the payment.";
                    break;
                case FIAT_PAYMENT_STARTED_MSG_RECEIVED:
                    message = "The bitcoin buyer has started the payment.\n" +
                            "Please check your payment account if you have received his payment.";
                    break;
               /* case FIAT_PAYMENT_RECEIPT_MSG_SENT:
                case PAYOUT_TX_RECEIVED:
                case PAYOUT_TX_COMMITTED:*/
                case PAYOUT_BROAD_CASTED:
                    message = "The payout transaction has been published.\n" +
                            "The trade is now completed and you can withdraw your funds.";
            }
        }

        ViewPath currentPath = navigation.getCurrentPath();
        boolean isPendingTradesViewCurrentView = currentPath != null &&
                currentPath.size() == 3 &&
                currentPath.get(2).equals(PendingTradesView.class);
        if (message != null) {
            //TODO we get that called initially before the navigation is inited
            if (isPendingTradesViewCurrentView || currentPath == null) {
                if (preferences.showAgain(id))
                    new Popup().headLine(headLine)
                            .message(message)
                            .show();
                preferences.dontShowAgain(id);
            } else {
                if (preferences.showAgain(id))
                    new Popup().headLine(headLine)
                            .message(message)
                            .actionButtonText("Go to \"Portfolio/Open trades\"")
                            .onAction(() -> {
                                FxTimer.runLater(Duration.ofMillis(100),
                                        () -> navigation.navigateTo(MainView.class, PortfolioView.class, PendingTradesView.class)
                                );
                            })
                            .show();
                preferences.dontShowAgain(id);
            }
        }
    }

    private void addDisputeStateListeners(List<? extends Trade> addedTrades) {
        addedTrades.stream().forEach(trade -> trade.disputeStateProperty().addListener((observable, oldValue, newValue) -> {
            switch (newValue) {
                case NONE:
                    break;
                case DISPUTE_REQUESTED:
                    break;
                case DISPUTE_STARTED_BY_PEER:
                    disputeManager.findOwnDispute(trade.getId()).ifPresent(dispute -> {
                        String msg;
                        if (dispute.isSupportTicket())
                            msg = "Your trading peer has encountered technical problems and requested support for trade with ID " + trade.getShortId() + ".\n" +
                                    "Please await further instructions from the arbitrator.\n" +
                                    "Your funds are safe and will be refunded as soon the problem is resolved.";
                        else
                            msg = "Your trading peer has requested a dispute for trade with ID " + trade.getShortId() + ".";

                        new Popup().information(msg).show();
                    });
                    break;
                case DISPUTE_CLOSED:
                    new Popup().information("A support ticket for trade with ID " + trade.getShortId() + " has been closed.").show();
                    break;
            }
        }));
    }

    private void setBitcoinNetworkSyncProgress(double value) {
        btcSyncProgress.set(value);
        String numPeers = "Nr. of peers: " + numBTCPeers;
        if (value == 1) {
            btcSplashInfo.set(numPeers + " / synchronized with " + btcNetworkAsString);
            btcFooterInfo.set(numPeers + " / synchronized with " + btcNetworkAsString);
            btcSplashSyncIconId.set("image-connection-synced");
            stopCheckForBtcSyncStateTimer();
        } else if (value > 0.0) {
            String percentage = formatter.formatToPercent(value);
            btcSplashInfo.set(numPeers + " / synchronizing with " + btcNetworkAsString + ": " + percentage);
            btcFooterInfo.set(numPeers + " / synchronizing " + btcNetworkAsString + ": " + percentage);
            stopCheckForBtcSyncStateTimer();
        } else if (value == -1) {
            btcSplashInfo.set(numPeers + " / connecting to " + btcNetworkAsString);
            btcFooterInfo.set(numPeers + " / connecting to " + btcNetworkAsString);
        } else {
            log.error("Not allowed value at setBitcoinNetworkSyncProgress: " + value);
        }
    }

    private void stopCheckForBtcSyncStateTimer() {
        if (checkForBtcSyncStateTimer != null) {
            checkForBtcSyncStateTimer.stop();
            checkForBtcSyncStateTimer = null;
        }
    }
}
