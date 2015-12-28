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
import io.bitsquare.app.Version;
import io.bitsquare.arbitration.ArbitratorManager;
import io.bitsquare.arbitration.Dispute;
import io.bitsquare.arbitration.DisputeManager;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.TradeWalletService;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.listeners.BalanceListener;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.components.BalanceTextField;
import io.bitsquare.gui.components.BalanceWithConfirmationTextField;
import io.bitsquare.gui.components.TxIdTextField;
import io.bitsquare.gui.popups.DisplayAlertMessagePopup;
import io.bitsquare.gui.popups.Popup;
import io.bitsquare.gui.popups.WalletPasswordPopup;
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
    private final AlertManager alertManager;
    private final WalletPasswordPopup walletPasswordPopup;
    private final BSFormatter formatter;

    // BTC network
    final StringProperty blockchainSyncInfo = new SimpleStringProperty("Initializing");
    final StringProperty blockchainSyncInfoFooter = new SimpleStringProperty("Initializing");
    final DoubleProperty blockchainSyncProgress = new SimpleDoubleProperty(-1);
    final StringProperty walletServiceErrorMsg = new SimpleStringProperty();
    final StringProperty blockchainSyncIconId = new SimpleStringProperty();
    final StringProperty availableBalance = new SimpleStringProperty();
    final StringProperty lockedBalance = new SimpleStringProperty();
    private final StringProperty numBTCPeersAsString = new SimpleStringProperty();

    // P2P network
    final StringProperty splashP2PNetworkInfo = new SimpleStringProperty();
    final StringProperty p2PNetworkInfo = new SimpleStringProperty();
    final DoubleProperty splashP2PNetworkProgress = new SimpleDoubleProperty(-1);
    final StringProperty p2PNetworkErrorMsg = new SimpleStringProperty();
    final StringProperty p2PNetworkIconId = new SimpleStringProperty();

    // software update
    final String version = "v." + Version.VERSION;

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
    private int numBTCPeers = 0;
    //private Timer lostBTCConnectionTimeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MainViewModel(WalletService walletService, TradeWalletService tradeWalletService,
                         ArbitratorManager arbitratorManager, P2PService p2PService, TradeManager tradeManager,
                         OpenOfferManager openOfferManager, DisputeManager disputeManager, Preferences preferences,
                         User user, AlertManager alertManager, WalletPasswordPopup walletPasswordPopup,
                         BSFormatter formatter) {
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
        this.alertManager = alertManager;
        this.walletPasswordPopup = walletPasswordPopup;
        this.formatter = formatter;

        bitcoinNetworkAsString = formatter.formatBitcoinNetwork(preferences.getBitcoinNetwork());

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
        log.trace("initializeAllServices");

        BooleanProperty walletInitialized = initBitcoinWallet();
        BooleanProperty p2pNetWorkReady = initP2PNetwork();

        // need to store it to not get garbage collected
        allServicesDone = EasyBind.combine(walletInitialized, p2pNetWorkReady, (a, b) -> a && b);
        allServicesDone.subscribe((observable, oldValue, newValue) -> {
            if (newValue)
                onAllServicesInitialized();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialisation
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BooleanProperty initP2PNetwork() {
        final BooleanProperty p2pNetWorkReady = new SimpleBooleanProperty();
        splashP2PNetworkInfo.set("Connecting to Tor network...");
        p2PService.start(new P2PServiceListener() {
            @Override
            public void onTorNodeReady() {
                splashP2PNetworkInfo.set("Tor node created.");
                p2PNetworkInfo.set(splashP2PNetworkInfo.get());
                p2PNetworkIconId.set("image-connection-tor");
            }

            @Override
            public void onHiddenServicePublished() {
                splashP2PNetworkInfo.set("Hidden Service published.");
                p2PNetworkInfo.set(splashP2PNetworkInfo.get());
            }

            @Override
            public void onRequestingDataCompleted() {
                if (p2PService.getNumAuthenticatedPeers().get() == 0) {
                    splashP2PNetworkInfo.set("Initial data received.");
                    p2PNetworkInfo.set(splashP2PNetworkInfo.get());
                } else {
                    updateP2pNetworkInfo();
                }
                p2pNetWorkReady.set(true);
            }

            @Override
            public void onNoSeedNodeAvailable() {
                splashP2PNetworkInfo.set("No seed node available.");
                p2PNetworkInfo.set(splashP2PNetworkInfo.get());
                p2pNetWorkReady.set(true);
            }

            @Override
            public void onFirstPeerAuthenticated() {
                updateP2pNetworkInfo();
                splashP2PNetworkProgress.set(1);
            }


            @Override
            public void onSetupFailed(Throwable throwable) {
                p2PNetworkErrorMsg.set("Connecting to the P2P network failed. " + throwable.getMessage());
                splashP2PNetworkProgress.set(0);
            }
        });

        return p2pNetWorkReady;
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
            numBTCPeers = (int) newValue;
            numBTCPeersAsString.set(String.valueOf(newValue) + " peers");
            if (blockchainSyncProgress.get() >= 1 && numBTCPeers > 1)
                blockchainSyncInfoFooter.set("Bitcoin network: Peers: " + numBTCPeers);

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
        String text = "1. This software is experimental and provided \"as is\", without warranty of any kind, " +
                "express or implied, including but not limited to the warranties of " +
                "merchantability, fitness for a particular purpose and non-infringement.\n" +
                "In no event shall the authors or copyright holders be liable for any claim, damages or other " +
                "liability, whether in an action of contract, tort or otherwise, " +
                "arising from, out of or in connection with the software or the use or other dealings in the software.\n\n" +
                "2. The user is responsible to use the software in compliance with local laws.\n\n" +
                "3. The user confirms that he has read and agreed to the rules defined in our " +
                "Wiki regrading the dispute process.";
        if (!preferences.getTacAccepted() && !BitsquareApp.DEV_MODE)
            new Popup().headLine("USER AGREEMENT")
                    .message(text)
                    .actionButtonText("I agree")
                    .closeButtonText("I disagree and quit")
                    .onAction(() -> preferences.setTacAccepted(true))
                    .onClose(() -> BitsquareApp.shutDownHandler.run())
                    .show();

        // update nr of peers in footer
        p2PService.getNumAuthenticatedPeers().addListener((observable, oldValue, newValue) -> updateP2pNetworkInfo());

        // now show app
        showAppScreen.set(true);
    }

    private void updateP2pNetworkInfo() {
        p2PNetworkInfo.set("Nr. of authenticated connections: " + p2PService.getNumAuthenticatedPeers().get());
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
        String nrOfPeers = "";
        if (numBTCPeers > 1)
            nrOfPeers = ", Peers: " + numBTCPeers;

        if (value >= 1) {
            stopBlockchainSyncTimeout();

            blockchainSyncInfo.set("Blockchain synchronization complete." + nrOfPeers);
            blockchainSyncIconId.set("image-connection-synced");
        } else if (value > 0.0) {
            // We stop as soon the download started the timeout
            stopBlockchainSyncTimeout();

            blockchainSyncInfo.set("Synchronizing blockchain: " + formatter.formatToPercent(value) + nrOfPeers);
            blockchainSyncInfoFooter.set("Synchronizing: " + formatter.formatToPercent(value) + nrOfPeers);

        } else {
            blockchainSyncInfo.set("Connecting to the bitcoin network..." + nrOfPeers);
            blockchainSyncInfoFooter.set("Connecting..." + nrOfPeers);
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
