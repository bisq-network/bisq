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

import io.bitsquare.app.UpdateProcess;
import io.bitsquare.app.Version;
import io.bitsquare.arbitration.ArbitrationRepository;
import io.bitsquare.btc.WalletService;
import io.bitsquare.crypto.KeyRing;
import io.bitsquare.fiat.FiatAccount;
import io.bitsquare.gui.common.model.ViewModel;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.p2p.BaseP2PService;
import io.bitsquare.p2p.ClientNode;
import io.bitsquare.p2p.tomp2p.BootstrappedPeerBuilder;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.trade.offer.OpenOfferManager;
import io.bitsquare.user.Preferences;
import io.bitsquare.user.User;
import io.bitsquare.util.Utilities;

import org.bitcoinj.store.BlockStoreException;

import com.google.inject.Inject;

import java.util.Timer;
import java.util.concurrent.TimeoutException;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;

class MainViewModel implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(MainViewModel.class);

    private static final long BLOCKCHAIN_SYNC_TIMEOUT = 30000;
    private static final long LOST_CONNECTION_TIMEOUT = 10000;

    private final User user;
    private final KeyRing keyRing;
    private final WalletService walletService;
    private final ArbitrationRepository arbitrationRepository;
    private final ClientNode clientNode;
    private final TradeManager tradeManager;
    private final OpenOfferManager openOfferManager;
    private final UpdateProcess updateProcess;
    private final BSFormatter formatter;
    private final int networkId;

    // BTC network
    final StringProperty blockchainSyncInfo = new SimpleStringProperty("Initializing");
    final StringProperty blockchainSyncInfoFooter = new SimpleStringProperty("Initializing");
    final DoubleProperty blockchainSyncProgress = new SimpleDoubleProperty(-1);
    final StringProperty walletServiceErrorMsg = new SimpleStringProperty();
    final StringProperty blockchainSyncIconId = new SimpleStringProperty();
    final StringProperty numBTCPeers = new SimpleStringProperty();

    // P2P network
    final StringProperty bootstrapInfo = new SimpleStringProperty("Connecting to P2P network...");
    final StringProperty bootstrapInfoFooter = new SimpleStringProperty();
    final DoubleProperty bootstrapProgress = new SimpleDoubleProperty(-1);
    final StringProperty bootstrapErrorMsg = new SimpleStringProperty();
    final StringProperty bootstrapIconId = new SimpleStringProperty();
    final StringProperty numDHTPeers = new SimpleStringProperty();

    // software update
    final StringProperty updateInfo = new SimpleStringProperty();
    String version = "v." + Version.VERSION;

    final BooleanProperty showRestartButton = new SimpleBooleanProperty(false);
    final StringProperty updateIconId = new SimpleStringProperty();

    final StringProperty bankAccountsComboBoxPrompt = new SimpleStringProperty();
    final BooleanProperty bankAccountsComboBoxDisable = new SimpleBooleanProperty();
    final ObjectProperty<FiatAccount> currentBankAccount = new SimpleObjectProperty<>();

    final BooleanProperty showAppScreen = new SimpleBooleanProperty();
    final StringProperty numPendingTradesAsString = new SimpleStringProperty();
    final BooleanProperty showPendingTradesNotification = new SimpleBooleanProperty();

    final String bitcoinNetworkAsString;

    private Timer blockchainSyncTimeoutTimer;
    private Timer lostConnectionTimeoutTimer;


    @Inject
    public MainViewModel(User user, KeyRing keyRing, WalletService walletService, ArbitrationRepository arbitrationRepository, ClientNode clientNode,
                         TradeManager tradeManager, OpenOfferManager openOfferManager, Preferences preferences, UpdateProcess updateProcess,
                         BSFormatter formatter) {
        this.user = user;
        this.keyRing = keyRing;
        this.walletService = walletService;
        this.arbitrationRepository = arbitrationRepository;
        this.clientNode = clientNode;
        this.tradeManager = tradeManager;
        this.openOfferManager = openOfferManager;
        this.updateProcess = updateProcess;
        this.formatter = formatter;

        bitcoinNetworkAsString = formatter.formatBitcoinNetwork(preferences.getBitcoinNetwork());
        networkId = preferences.getBitcoinNetwork().ordinal();

        updateProcess.state.addListener((observableValue, oldValue, newValue) -> applyUpdateState(newValue));
        applyUpdateState(updateProcess.state.get());

        currentBankAccount.bind(user.currentFiatAccountProperty());
        user.fiatAccountsObservableList().addListener((ListChangeListener<FiatAccount>) change -> {
            bankAccountsComboBoxDisable.set(change.getList().isEmpty());
            bankAccountsComboBoxPrompt.set(change.getList().isEmpty() ? "No accounts" : "");
        });
        bankAccountsComboBoxDisable.set(user.fiatAccountsObservableList().isEmpty());
        bankAccountsComboBoxPrompt.set(user.fiatAccountsObservableList().isEmpty() ? "No accounts" : "");
    }

    public void restart() {
        updateProcess.restart();
    }

    public void initBackend() {
        Platform.runLater(updateProcess::init);

        if (walletService.downloadPercentageProperty().get() > -1)
            startBlockchainSyncTimeout();

        walletService.downloadPercentageProperty().addListener((ov, oldValue, newValue) -> {
            setBitcoinNetworkSyncProgress((double) newValue);
        });
        setBitcoinNetworkSyncProgress(walletService.downloadPercentageProperty().get());

        walletService.numPeersProperty().addListener((observable, oldValue, newValue) -> {
            numBTCPeers.set(String.valueOf(newValue) + " peers");
            if ((int) newValue < 1)
                walletServiceErrorMsg.set("We lost connection to the last peer.");
            else
                walletServiceErrorMsg.set(null);
        });

        // Set executor for all P2PServices
        BaseP2PService.setUserThread(Platform::runLater);

        clientNode.numPeersProperty().addListener((observable, oldValue, newValue) -> {
            numDHTPeers.set(String.valueOf(newValue) + " peers");
            if ((int) newValue == 0) {
                if (lostConnectionTimeoutTimer != null)
                    lostConnectionTimeoutTimer.cancel();
                lostConnectionTimeoutTimer = Utilities.setTimeout(LOST_CONNECTION_TIMEOUT, () -> {
                    log.trace("Connection lost timeout reached");
                    bootstrapErrorMsg.set("We lost connection to the last peer.");
                });
            }
            else if ((int) oldValue == 0 && (int) newValue > 0) {
                if (lostConnectionTimeoutTimer != null) {
                    lostConnectionTimeoutTimer.cancel();
                    lostConnectionTimeoutTimer = null;
                }
                bootstrapErrorMsg.set(null);
            }
        });

        clientNode.setExecutor(Platform::runLater);
        Observable<BootstrappedPeerBuilder.State> bootstrapStateAsObservable = clientNode.bootstrap(networkId, keyRing.getDhtSignatureKeyPair());
        bootstrapStateAsObservable.publish();
        bootstrapStateAsObservable.subscribe(
                state -> Platform.runLater(() -> setBootstrapState(state)),
                error -> Platform.runLater(() -> {
                    bootstrapErrorMsg.set(error.getMessage());
                    bootstrapInfo.set("Connecting to the P2P network failed.");
                    bootstrapProgress.set(0);

                }),
                () -> log.trace("message completed"));

        Observable<Object> walletServiceObservable = walletService.initialize(Platform::runLater);
        walletServiceObservable.subscribe(
                next -> {
                    //log.trace("wallet next");
                },
                error -> Platform.runLater(() -> {
                    log.trace("wallet error");
                    setWalletServiceException(error);
                }),
                () -> {
                    log.trace("wallet completed");
                });

        Observable<UpdateProcess.State> updateProcessObservable = this.updateProcess.getProcess();
        updateProcessObservable.subscribe(next -> {
                    //log.trace("updateProcess next");
                },
                error -> {
                },
                () -> {
                    log.trace("updateProcess completed");
                });

        Observable<?> allServices = Observable.merge(bootstrapStateAsObservable, walletServiceObservable, updateProcessObservable);
        allServices.subscribe(
                next -> {
                },
                error -> {
                },
                () -> Platform.runLater(this::onAllServicesInitialized)
        );
    }

    private void onAllServicesInitialized() {
        log.trace("backend completed");

        setBitcoinNetworkSyncProgress(walletService.downloadPercentageProperty().get());
        tradeManager.getPendingTrades().addListener((ListChangeListener<Trade>) change -> updateNumPendingTrades());
        updateNumPendingTrades();
        showAppScreen.set(true);

        // For alpha version
        if (!user.isRegistered()) {
            FiatAccount fiatAccount = new FiatAccount(FiatAccount.Type.IRC,
                    "EUR",
                    CountryUtil.getDefaultCountry(),
                    "Demo (Name of bank)",
                    "Demo (Account holder name)",
                    "Demo (E.g. IBAN) ",
                    "Demo (E.g. BIC) ");
            user.addFiatAccount(fiatAccount);
            user.setAccountID(walletService.getRegistrationAddressEntry().toString());
        }

        // Load all arbitrators in background. Any class requiring a loaded list of arbitrators need to register itself as listener to handle the async 
        // operation.
        log.debug("loadAllArbitrators");
        arbitrationRepository.loadAllArbitrators();
        tradeManager.onAllServicesInitialized();
        openOfferManager.onAllServicesInitialized();
    }

    private void applyUpdateState(UpdateProcess.State state) {
        switch (state) {
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
            case FAILURE:
                updateInfo.set("Check for updates failed. ");
                updateIconId.set("image-update-failed");
                break;
        }
    }

    private void setBootstrapState(BootstrappedPeerBuilder.State state) {
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
    }

    private void setWalletServiceException(Throwable error) {
        setBitcoinNetworkSyncProgress(0);
        blockchainSyncInfo.set("Connecting to the bitcoin network failed.");
        blockchainSyncInfoFooter.set("Connection failed.");
        if (error instanceof TimeoutException) {
            walletServiceErrorMsg.set("Please check your network connection.\n\n" +
                    "You must allow outgoing TCP connections to port 18333 for the bitcoin testnet.\n\n" +
                    "See https://github.com/bitsquare/bitsquare/wiki for instructions.");
        }
        else if (error.getCause() instanceof BlockStoreException) {
            walletServiceErrorMsg.set("You cannot run 2 instances of the program.");
        }
        else if (error.getMessage() != null) {
            walletServiceErrorMsg.set(error.getMessage());
        }
        else {
            walletServiceErrorMsg.set(error.toString());
        }
    }


    public StringConverter<FiatAccount> getBankAccountsConverter() {
        return new StringConverter<FiatAccount>() {
            @Override
            public String toString(FiatAccount fiatAccount) {
                return fiatAccount.nameOfBank;
            }

            @Override
            public FiatAccount fromString(String s) {
                return null;
            }
        };
    }

    public ObservableList<FiatAccount> getBankAccounts() {
        return user.fiatAccountsObservableList();
    }

    public void setCurrentBankAccount(FiatAccount currentFiatAccount) {
        user.setCurrentFiatAccountProperty(currentFiatAccount);
    }

    private void updateNumPendingTrades() {
        long numPendingTrades = tradeManager.getPendingTrades().size();
        if (numPendingTrades > 0)
            numPendingTradesAsString.set(String.valueOf(numPendingTrades));
        if (numPendingTrades > 9)
            numPendingTradesAsString.set("-");

        showPendingTradesNotification.set(numPendingTrades > 0);
    }

    private void setBitcoinNetworkSyncProgress(double value) {
        blockchainSyncProgress.set(value);
        if (value >= 1) {
            stopBlockchainSyncTimeout();

            blockchainSyncInfo.set("Blockchain synchronization complete.");
            blockchainSyncIconId.set("image-connection-synced");
        }
        else if (value > 0.0) {
            // We stop as soon the download started the timeout
            stopBlockchainSyncTimeout();

            blockchainSyncInfo.set("Synchronizing blockchain: " + formatter.formatToPercent(value));
            blockchainSyncInfoFooter.set("Synchronizing: " + formatter.formatToPercent(value));
        }
        else {
            blockchainSyncInfo.set("Connecting to the bitcoin network...");
            blockchainSyncInfoFooter.set("Connecting...");
        }
    }

    private void startBlockchainSyncTimeout() {
        log.trace("startBlockchainSyncTimeout");
        stopBlockchainSyncTimeout();


        blockchainSyncTimeoutTimer = Utilities.setTimeout(BLOCKCHAIN_SYNC_TIMEOUT, () -> {
            log.trace("Timeout reached");
            setWalletServiceException(new TimeoutException());
        });
    }

    private void stopBlockchainSyncTimeout() {
        log.trace("stopBlockchainSyncTimeout");
        if (blockchainSyncTimeoutTimer != null) {
            blockchainSyncTimeoutTimer.cancel();
            blockchainSyncTimeoutTimer = null;
        }
    }

}
