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
import io.bitsquare.app.UpdateProcess;
import io.bitsquare.app.Version;
import io.bitsquare.arbitration.ArbitratorManager;
import io.bitsquare.arbitration.Dispute;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.components.BalanceTextField;
import io.bitsquare.gui.components.BalanceWithConfirmationTextField;
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.popups.*;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.p2p.P2PService;
import io.bitsquare.p2p.P2PServiceListener;
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
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.jetbrains.annotations.NotNull;
import org.reactfx.util.FxTimer;
import org.reactfx.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class MainViewModel implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(MainViewModel.class);

    private static final long BLOCKCHAIN_SYNC_TIMEOUT = 60000;
    private static final long LOST_P2P_CONNECTION_TIMEOUT = 5000;
    // private static final long LOST_BTC_CONNECTION_TIMEOUT = 5000;

    private final WalletService walletService;
    private final TradeWalletService tradeWalletService;
    private final ArbitratorManager arbitratorManager;
    private final P2PService p2PService;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final DisputeManager disputeManager;
    private final Preferences preferences;
    private final KeyRing keyRing;
    private final AlertManager alertManager;
    private final WalletPasswordPopup walletPasswordPopup;
    private final UpdateProcess updateProcess;
    private final BSFormatter formatter;

    // BTC network
    final StringProperty blockchainSyncInfo = new SimpleStringProperty("Initializing");
    final StringProperty blockchainSyncInfoFooter = new SimpleStringProperty("Initializing");
    final DoubleProperty blockchainSyncProgress = new SimpleDoubleProperty(-1);
    final StringProperty walletServiceErrorMsg = new SimpleStringProperty();
    final StringProperty blockchainSyncIconId = new SimpleStringProperty();
    final StringProperty availableBalance = new SimpleStringProperty();
    final StringProperty lockedBalance = new SimpleStringProperty();
    private final StringProperty numBTCPeers = new SimpleStringProperty();

    // P2P network
    final StringProperty bootstrapInfo = new SimpleStringProperty("Connecting to P2P network...");
    final StringProperty p2pNetworkInfoFooter = new SimpleStringProperty("Setting up Tor hidden service...");
    final DoubleProperty bootstrapProgress = new SimpleDoubleProperty(-1);
    final StringProperty bootstrapErrorMsg = new SimpleStringProperty();
    final StringProperty bootstrapIconId = new SimpleStringProperty();
    final StringProperty numP2PNetworkPeers = new SimpleStringProperty();

    // software update
    final StringProperty updateInfo = new SimpleStringProperty();
    final String version = "v." + Version.VERSION;

    final BooleanProperty showRestartButton = new SimpleBooleanProperty(false);
    final BooleanProperty showDownloadButton = new SimpleBooleanProperty(false);
    final StringProperty newReleaseUrl = new SimpleStringProperty();
    final StringProperty updateIconId = new SimpleStringProperty();

    final BooleanProperty showAppScreen = new SimpleBooleanProperty();
    final StringProperty numPendingTradesAsString = new SimpleStringProperty();
    final BooleanProperty showPendingTradesNotification = new SimpleBooleanProperty();
    final StringProperty numOpenDisputesAsString = new SimpleStringProperty();
    final BooleanProperty showOpenDisputesNotification = new SimpleBooleanProperty();
    private final BooleanProperty isSplashScreenRemoved = new SimpleBooleanProperty();

    final String bitcoinNetworkAsString;

    private Timer blockchainSyncTimeoutTimer;
    private Timer lostP2PConnectionTimeoutTimer;
    private MonadicBinding<Boolean> allServicesDone;
    private User user;
    //private Timer lostBTCConnectionTimeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MainViewModel(WalletService walletService, TradeWalletService tradeWalletService,
                         ArbitratorManager arbitratorManager, P2PService p2PService, TradeManager tradeManager,
                         OpenOfferManager openOfferManager, DisputeManager disputeManager, Preferences preferences,
                         KeyRing keyRing, User user,
                         AlertManager alertManager,
                         WalletPasswordPopup walletPasswordPopup, UpdateProcess updateProcess, BSFormatter formatter) {
        this.user = user;
        log.debug("in");
        this.walletService = walletService;
        this.tradeWalletService = tradeWalletService;
        this.arbitratorManager = arbitratorManager;
        this.p2PService = p2PService;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.disputeManager = disputeManager;
        this.preferences = preferences;
        this.keyRing = keyRing;
        this.alertManager = alertManager;
        this.walletPasswordPopup = walletPasswordPopup;
        this.updateProcess = updateProcess;
        this.formatter = formatter;

        bitcoinNetworkAsString = formatter.formatBitcoinNetwork(preferences.getBitcoinNetwork());

        updateProcess.state.addListener((observableValue, oldValue, newValue) -> applyUpdateState(newValue));
        applyUpdateState(updateProcess.state.get());
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

    public void restart() {
        updateProcess.restart();
    }

    public void initializeAllServices() {
        log.trace("initializeAllServices");

        p2pNetworkInfoFooter.set("Connecting to tor network...");

        BooleanProperty updateProcessDone = initUpdateFx();
        BooleanProperty walletInitialized = initBitcoinWallet();
        BooleanProperty bootstrapDone = initP2PNetwork();

        // need to store it to not get garbage collected
        allServicesDone = EasyBind.combine(updateProcessDone, walletInitialized, bootstrapDone,
                (a, b, c) -> a.booleanValue() && b.booleanValue() && c.booleanValue());
        allServicesDone.subscribe((observable, oldValue, newValue) -> {
            if (newValue)
                onAllServicesInitialized();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BooleanProperty initP2PNetwork() {
        final BooleanProperty p2pNetworkReady = new SimpleBooleanProperty();
        p2PService.start(new P2PServiceListener() {
            @Override
            public void onTorNodeReady() {
                p2pNetworkInfoFooter.set("Tor node created.");
            }

            @Override
            public void onRequestingDataCompleted() {
                p2pNetworkInfoFooter.set("Data received from peer.");
                p2pNetworkReady.set(true);
            }

            @Override
            public void onAuthenticated() {
                p2pNetworkInfoFooter.set("Authenticated in P2P network.");
            }

            @Override
            public void onHiddenServicePublished() {
                p2pNetworkInfoFooter.set("Tor hidden service available.");
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                bootstrapErrorMsg.set("Error at starting P2P network. " + throwable.getMessage());
                bootstrapInfo.set("Connecting to the P2P network failed.");
                bootstrapProgress.set(0);
            }
        });
        return p2pNetworkReady;
    }

    private BooleanProperty initBitcoinWallet() {
        if (walletService.downloadPercentageProperty().get() > -1)
            startBlockchainSyncTimeout();

        EasyBind.subscribe(walletService.downloadPercentageProperty(), newValue -> setBitcoinNetworkSyncProgress((double) newValue));
        
       /* walletService.downloadPercentageProperty().addListener((ov, oldValue, newValue) -> {
            setBitcoinNetworkSyncProgress((double) newValue);
        });
        setBitcoinNetworkSyncProgress(walletService.downloadPercentageProperty().get());
        // Sometimes we don't get the updates, so add an additional setter after 2 seconds
        FxTimer.runLater(Duration.ofMillis(2000), () -> setBitcoinNetworkSyncProgress(walletService.downloadPercentageProperty().get()));*/

        walletService.numPeersProperty().addListener((observable, oldValue, newValue) -> {
            log.debug("Bitcoin peers " + newValue);
            numBTCPeers.set(String.valueOf(newValue) + " peers");
         /*   if ((int) newValue < 1) {
                if (lostBTCConnectionTimeoutTimer != null)
                    lostBTCConnectionTimeoutTimer.cancel();
                lostBTCConnectionTimeoutTimer = Utilities.setTimeout(LOST_BTC_CONNECTION_TIMEOUT, () -> {
                    log.trace("Connection lost timeout reached");
                    walletServiceErrorMsg.set("We lost connection to the last peer.");
                });
            }
            else {
                if (lostBTCConnectionTimeoutTimer != null) {
                    lostBTCConnectionTimeoutTimer.cancel();
                    lostBTCConnectionTimeoutTimer = null;
                }
                walletServiceErrorMsg.set(null);
            }*/
        });
        final BooleanProperty walletInitialized = new SimpleBooleanProperty();
        walletService.initialize(null,
                () -> {
                    log.trace("wallet initialized");
                    walletInitialized.set(true);
                },
                errorMessage -> setWalletServiceException(errorMessage));
        return walletInitialized;
    }

    @NotNull
    private BooleanProperty initUpdateFx() {
        updateProcess.init();
        final BooleanProperty updateProcessDone = new SimpleBooleanProperty();
        updateProcess.setResultHandler(() -> {
            log.trace("updateProcess completed");
            updateProcessDone.set(true);
        });
        return updateProcessDone;
    }

    private void onAllServicesInitialized() {
        log.trace("onAllServicesInitialized");


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
            pendingTradesChanged();
        });
        pendingTradesChanged();
        addDisputeStateListeners(tradeManager.getTrades());
        tradeManager.onAllServicesInitialized();


        // arbitratorManager
        arbitratorManager.onAllServicesInitialized();


        // walletService
        // In case we have any offers open or a pending trade we need to unlock our trading wallet so a trade can be executed automatically
        // Otherwise we delay the password request to create offer, or take offer.
        // When the password is set it will be stored to the tradeWalletService as well, so its only needed after a restart.
        if (walletService.getWallet().isEncrypted() &&
                (openOfferManager.getOpenOffers().size() > 0
                        || tradeManager.getTrades().size() > 0
                        || disputeManager.getDisputesAsObservableList().size() > 0))
            walletPasswordPopup.show().onAesKey(aesKey -> tradeWalletService.setAesKey(aesKey));

        if (tradeManager.pendingTradesInitializedProperty().get() && isSplashScreenRemoved.get())
            applyTradePeriodState();
        else {
            isSplashScreenRemoved.addListener((observable, oldValue, newValue) -> {
                if (tradeManager.pendingTradesInitializedProperty().get() && isSplashScreenRemoved.get())
                    applyTradePeriodState();
            });
            tradeManager.pendingTradesInitializedProperty().addListener((observable, oldValue, newValue) -> {
                if (tradeManager.pendingTradesInitializedProperty().get() && isSplashScreenRemoved.get())
                    applyTradePeriodState();
            });
        }
        walletService.addBalanceListener(new BalanceListener() {
            @Override
            public void onBalanceChanged(Coin balance) {
                updateBalance();
            }
        });
        updateBalance();
        setBitcoinNetworkSyncProgress(walletService.downloadPercentageProperty().get());


        // openOfferManager
        openOfferManager.getOpenOffers().addListener((ListChangeListener<OpenOffer>) c -> updateBalance());
        openOfferManager.onAllServicesInitialized();


        // alertManager
        alertManager.alertMessageProperty().addListener((observable, oldValue, newValue) -> displayAlertIfPresent(newValue));
        displayAlertIfPresent(alertManager.alertMessageProperty().get());


        // tac
        if (!preferences.getTacAccepted() && !BitsquareApp.DEV_MODE)
            new TacPopup().url(WebViewPopup.getLocalUrl("tac.html")).onAgree(() -> preferences.setTacAccepted(true)).show();


        // now show app
        showAppScreen.set(true);
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
        updateLockedBalance();
    }

    private void updateLockedBalance() {
        List<AddressEntry> result = new ArrayList<>();

        result.addAll(Stream.concat(openOfferManager.getOpenOffers().stream(), tradeManager.getTrades().stream())
                .map(tradable -> walletService.getAddressEntryByOfferId(tradable.getOffer().getId()))
                .collect(Collectors.toList()));

        Optional<Coin> totalLockedOptional = result.stream().map(e -> walletService.getBalanceForAddress(e.getAddress())).reduce((a, b) -> a.add(b));
        if (totalLockedOptional.isPresent())
            lockedBalance.set(formatter.formatCoinWithCode(totalLockedOptional.get()));
        else
            lockedBalance.set(formatter.formatCoinWithCode(Coin.ZERO));
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

    private void applyUpdateState(UpdateProcess.State state) {
        switch (state) {
            case INIT:
                updateInfo.set("");
                updateIconId.set(null);
                break;
            case CHECK_FOR_UPDATES:
                updateInfo.set("Check for updates...");
                updateIconId.set("image-update-in-progress");
                break;
            case UPDATE_AVAILABLE:
                updateInfo.set("New update available. Please restart!");
                updateIconId.set("image-update-available");
                showRestartButton.set(true);
                break;
            case UP_TO_DATE:
                updateInfo.set("Software is up to date. Version: " + Version.VERSION);
                updateIconId.set("image-update-up-to-date");
                break;
            case NEW_RELEASE:
                updateInfo.set("A new release is available.");
                updateIconId.set("image-update-available");
                newReleaseUrl.set(updateProcess.getReleaseUrl());
                showDownloadButton.setValue(true);
                break;
            case FAILURE:
                updateInfo.set("Check for updates failed.");
                updateIconId.set("image-update-failed");
                break;
        }
    }

  /*  private void setBootstrapState(TomP2PNetworkInfo.State state) {
        switch (state) {
            case DISCOVERY_DIRECT_SUCCEEDED:
                bootstrapIconId.set("image-connection-direct");
                bootstrapInfoFooter.set("Direct connection");
                break;
            case DISCOVERY_MANUAL_PORT_FORWARDING_SUCCEEDED:
            case DISCOVERY_AUTO_PORT_FORWARDING_SUCCEEDED:
                bootstrapIconId.set("image-connection-nat");
                bootstrapInfoFooter.set("Connected with port forwarding");
                break;
            case RELAY_SUCCEEDED:
                bootstrapIconId.set("image-connection-relay");
                bootstrapInfoFooter.set("Connected with relay node");
                break;
            default:
                break;
        }

        switch (state) {
            case DISCOVERY_DIRECT_SUCCEEDED:
            case DISCOVERY_MANUAL_PORT_FORWARDING_SUCCEEDED:
            case DISCOVERY_AUTO_PORT_FORWARDING_SUCCEEDED:
            case RELAY_SUCCEEDED:
                bootstrapInfo.set(state.getMessage());
                bootstrapProgress.set(-1);
                break;
            case BOOT_STRAP_SUCCEEDED:
                bootstrapProgress.set(1);
                break;
            default:
                bootstrapProgress.set(-1);
                break;
        }
    }*/

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
        blockchainSyncInfo.set("Connecting to the bitcoin network failed.");
        blockchainSyncInfoFooter.set("Connection failed.");
        if (error instanceof TimeoutException) {
            walletServiceErrorMsg.set("Please check your network connection.\n\n" +
                    "You must allow outgoing TCP connections to port 18333 for the bitcoin testnet.\n\n" +
                    "See https://github.com/bitsquare/bitsquare/wiki for instructions.");
        } else if (error.getCause() instanceof BlockStoreException) {
            walletServiceErrorMsg.set("You cannot run 2 instances of the program.");
        } else if (error.getMessage() != null) {
            walletServiceErrorMsg.set(error.getMessage());
        } else {
            walletServiceErrorMsg.set(error.toString());
        }
    }

    private void updateTradePeriodState() {
        tradeManager.getTrades().stream().forEach(trade -> {
            int bestChainHeight = tradeWalletService.getBestChainHeight();
            if (trade.getCheckPaymentTimeAsBlockHeight() > 0 && bestChainHeight >= trade.getCheckPaymentTimeAsBlockHeight())
                trade.setTradePeriodState(Trade.TradePeriodState.HALF_REACHED);

            if (trade.getOpenDisputeTimeAsBlockHeight() > 0 && bestChainHeight >= trade.getOpenDisputeTimeAsBlockHeight())
                trade.setTradePeriodState(Trade.TradePeriodState.TRADE_PERIOD_OVER);

            switch (trade.getTradePeriodState()) {
                case NORMAL:
                    break;
                case HALF_REACHED:
                    if (!trade.isHalfTradePeriodReachedWarningDisplayed()) {
                        new Popup().warning("Your trade with ID " + trade.getShortId() +
                                " has reached the half of the max. allowed trading period and " +
                                "is still not completed.\nPlease check your trade state at Portfolio/open trades for further information.").show();
                        trade.setHalfTradePeriodReachedWarningDisplayed(true);
                    }
                    break;
                case TRADE_PERIOD_OVER:
                    if (!trade.isTradePeriodOverWarningDisplayed()) {
                        new Popup().warning("Your trade with ID " + trade.getShortId() + " has reached the max. allowed trading period and is " +
                                "not completed.\nPlease check your trade at Portfolio/Open trades for contacting the arbitrator.").show();
                        trade.setTradePeriodOverWarningDisplayed(true);
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
        blockchainSyncProgress.set(value);
        if (value >= 1) {
            stopBlockchainSyncTimeout();

            blockchainSyncInfo.set("Blockchain synchronization complete.");
            blockchainSyncIconId.set("image-connection-synced");
        } else if (value > 0.0) {
            // We stop as soon the download started the timeout
            stopBlockchainSyncTimeout();

            blockchainSyncInfo.set("Synchronizing blockchain: " + formatter.formatToPercent(value));
            blockchainSyncInfoFooter.set("Synchronizing: " + formatter.formatToPercent(value));
        } else {
            blockchainSyncInfo.set("Connecting to the bitcoin network...");
            blockchainSyncInfoFooter.set("Connecting...");
        }
    }

    private void startBlockchainSyncTimeout() {
        log.trace("startBlockchainSyncTimeout");
        stopBlockchainSyncTimeout();

        blockchainSyncTimeoutTimer = FxTimer.runLater(Duration.ofMillis(BLOCKCHAIN_SYNC_TIMEOUT), () -> {
            log.trace("Timeout reached");
            setWalletServiceException(new TimeoutException());
        });
    }

    private void stopBlockchainSyncTimeout() {
        if (blockchainSyncTimeoutTimer != null) {
            log.trace("stopBlockchainSyncTimeout");
            blockchainSyncTimeoutTimer.stop();
            blockchainSyncTimeoutTimer = null;
        }
    }
}
