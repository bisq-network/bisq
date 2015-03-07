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

import io.bitsquare.account.AccountSettings;
import io.bitsquare.app.gui.UpdateProcess;
import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.arbitrator.Reputation;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.CountryUtil;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.msg.MessageService;
import io.bitsquare.network.BootstrapState;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.TradeManager;
import io.bitsquare.user.User;
import io.bitsquare.util.DSAKeyUtil;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Utils;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

import viewfx.model.ViewModel;

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
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.util.StringConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;

class MainViewModel implements ViewModel {
    private static final Logger log = LoggerFactory.getLogger(MainViewModel.class);

    // BTC network
    final StringProperty blockchainSyncInfo = new SimpleStringProperty("Initializing");
    final DoubleProperty blockchainSyncProgress = new SimpleDoubleProperty(-1);
    final StringProperty walletServiceErrorMsg = new SimpleStringProperty();
    final StringProperty blockchainSyncIconId = new SimpleStringProperty();

    // P2P network
    final StringProperty bootstrapInfo = new SimpleStringProperty();
    final DoubleProperty bootstrapProgress = new SimpleDoubleProperty(-1);
    final StringProperty bootstrapErrorMsg = new SimpleStringProperty();
    final StringProperty bootstrapIconId = new SimpleStringProperty();

    // software update
    final StringProperty updateInfo = new SimpleStringProperty();
    final BooleanProperty showRestartButton = new SimpleBooleanProperty(false);
    final StringProperty updateIconId = new SimpleStringProperty();

    final StringProperty bankAccountsComboBoxPrompt = new SimpleStringProperty();
    final BooleanProperty bankAccountsComboBoxDisable = new SimpleBooleanProperty();
    final ObjectProperty<BankAccount> currentBankAccount = new SimpleObjectProperty<>();

    final BooleanProperty showAppScreen = new SimpleBooleanProperty();
    final StringProperty featureNotImplementedWarning = new SimpleStringProperty();
    final StringProperty numPendingTradesAsString = new SimpleStringProperty();
    final BooleanProperty showPendingTradesNotification = new SimpleBooleanProperty();

    final String bitcoinNetworkAsString;

    private final User user;
    private final WalletService walletService;
    private final MessageService messageService;
    private final TradeManager tradeManager;
    private UpdateProcess updateProcess;
    private final BSFormatter formatter;
    private Persistence persistence;
    private AccountSettings accountSettings;

    @Inject
    public MainViewModel(User user, WalletService walletService, MessageService messageService,
                         TradeManager tradeManager, BitcoinNetwork bitcoinNetwork, UpdateProcess updateProcess,
                         BSFormatter formatter, Persistence persistence, AccountSettings accountSettings) {
        this.user = user;
        this.walletService = walletService;
        this.messageService = messageService;
        this.tradeManager = tradeManager;
        this.updateProcess = updateProcess;
        this.formatter = formatter;
        this.persistence = persistence;
        this.accountSettings = accountSettings;

        bitcoinNetworkAsString = bitcoinNetwork.toString();

        updateProcess.state.addListener((observableValue, oldValue, newValue) -> applyUpdateState(newValue));
        applyUpdateState(updateProcess.state.get());

        user.getCurrentBankAccount().addListener((observable, oldValue, newValue) -> persistence.write(user));
        currentBankAccount.bind(user.currentBankAccountProperty());
        user.getBankAccounts().addListener((ListChangeListener<BankAccount>) change -> {
            bankAccountsComboBoxDisable.set(change.getList().isEmpty());
            bankAccountsComboBoxPrompt.set(change.getList().isEmpty() ? "No accounts" : "");
        });
        bankAccountsComboBoxDisable.set(user.getBankAccounts().isEmpty());
        bankAccountsComboBoxPrompt.set(user.getBankAccounts().isEmpty() ? "No accounts" : "");

        tradeManager.featureNotImplementedWarningProperty().addListener((ov, oldValue, newValue) -> {
            if (oldValue == null && newValue != null) {
                featureNotImplementedWarning.set(newValue);
                tradeManager.setFeatureNotImplementedWarning(null);
            }
        });
    }

    public void restart() {
        updateProcess.restart();
    }

    public void initBackend() {
        Platform.runLater(() -> updateProcess.init());
        
        setBitcoinNetworkSyncProgress(-1);
        walletService.getDownloadProgress().subscribe(
                percentage -> Platform.runLater(() -> {
                    if (percentage > 0)
                        setBitcoinNetworkSyncProgress(percentage / 100.0);
                }),
                error -> log.error(error.toString()),
                () -> Platform.runLater(() -> setBitcoinNetworkSyncProgress(1.0)));

        Observable<BootstrapState> messageObservable = messageService.init();
        messageObservable.publish();
        messageObservable.subscribe(
                state -> Platform.runLater(() -> setBootstrapState(state)),
                error -> Platform.runLater(() -> {
                    log.error(error.toString());
                    bootstrapErrorMsg.set(error.getMessage());
                    bootstrapInfo.set("Connecting to the Bitsquare network failed.");
                    bootstrapProgress.set(0);

                }),
                () -> log.trace("message completed"));

        Observable<Object> walletServiceObservable = walletService.initialize(Platform::runLater);
        walletServiceObservable.subscribe(
                next -> {
                    log.trace("wallet next");
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
                    log.trace("updateProcess next");
                },
                error -> {
                    log.trace("updateProcess error");
                },
                () -> {
                    log.trace("updateProcess completed");
                });

        Observable<?> allTasks = Observable.merge(messageObservable, walletServiceObservable, updateProcessObservable);
        allTasks.subscribe(
                next -> {
                },
                error -> log.error(error.toString()),
                () -> Platform.runLater(() -> allTasksCompleted())
        );
    }

    private void allTasksCompleted() {
        log.trace("backend completed");

        tradeManager.getPendingTrades().addListener(
                (MapChangeListener<String, Trade>) change -> updateNumPendingTrades());
        updateNumPendingTrades();
        showAppScreen.set(true);

        // For alpha version
        // uses messageService, so don't call it before backend is ready
        if (accountSettings.getAcceptedArbitrators().isEmpty())
            addMockArbitrator();

        // For alpha version
        if (!user.isRegistered()) {
            BankAccount bankAccount = new BankAccount(BankAccountType.IRC,
                    Currency.getInstance("EUR"),
                    CountryUtil.getDefaultCountry(),
                    "Demo (Name of bank)",
                    "Demo (Account holder name)",
                    "Demo (E.g. IBAN) ",
                    "Demo (E.g. BIC) ");
            user.setBankAccount(bankAccount);
            persistence.write(user);

            user.setAccountID(walletService.getRegistrationAddressEntry().toString());
            persistence.write(user.getClass().getName(), user);
        }
    }

    private void applyUpdateState(UpdateProcess.State state) {
        switch (state) {
            case CHECK_FOR_UPDATES:
                updateInfo.set("Checking for updates...");
                updateIconId.set("image-update-in-progress");
                break;
            case UPDATE_AVAILABLE:
                updateInfo.set("New update available. Please restart!");
                updateIconId.set("image-update-available");
                showRestartButton.set(true);
                break;
            case UP_TO_DATE:
                updateInfo.set("Software is up to date.");
                updateIconId.set("image-update-up-to-date");
                break;
            case FAILURE:
                updateInfo.set(updateProcess.getErrorMessage());
                updateIconId.set("image-update-failed");
                break;
        }
    }

    private void setBootstrapState(BootstrapState state) {
        switch (state) {
            case DISCOVERY_DIRECT_SUCCEEDED:
                bootstrapIconId.set("image-connection-direct");
                break;
            case DISCOVERY_MANUAL_PORT_FORWARDING_SUCCEEDED:
            case DISCOVERY_AUTO_PORT_FORWARDING_SUCCEEDED:
                bootstrapIconId.set("image-connection-nat");
                break;
            case RELAY_SUCCEEDED:
                bootstrapIconId.set("image-connection-relay");
                break;
            default:
                bootstrapIconId.set(null);
                break;
        }

        switch (state) {
            case DISCOVERY_DIRECT_SUCCEEDED:
            case DISCOVERY_MANUAL_PORT_FORWARDING_SUCCEEDED:
            case DISCOVERY_AUTO_PORT_FORWARDING_SUCCEEDED:
            case RELAY_SUCCEEDED:
                bootstrapInfo.set("Successfully connected to P2P network: " + state.getMessage());
                bootstrapProgress.set(1);
                break;
            default:
                bootstrapInfo.set("Connecting to the Bitsquare network: " + state.getMessage());
                bootstrapProgress.set(-1);
                break;
        }
    }

    private void setWalletServiceException(Throwable error) {
        setBitcoinNetworkSyncProgress(0);
        blockchainSyncInfo.set("Connecting to the bitcoin network failed.");
        if (error instanceof TimeoutException) {
            walletServiceErrorMsg.set("Please check your network connection.\n\n" +
                    "You must allow outgoing TCP connections to port 18333 for the bitcoin testnet.\n\n" +
                    "See https://github.com/bitsquare/bitsquare/wiki for instructions.");
        }
        else if (error.getMessage() != null) {
            walletServiceErrorMsg.set(error.getMessage());
        }
        else {
            walletServiceErrorMsg.set(error.toString());
        }
    }


    public StringConverter<BankAccount> getBankAccountsConverter() {
        return new StringConverter<BankAccount>() {
            @Override
            public String toString(BankAccount bankAccount) {
                return bankAccount.getNameOfBank();
            }

            @Override
            public BankAccount fromString(String s) {
                return null;
            }
        };
    }

    public ObservableList<BankAccount> getBankAccounts() {
        return user.getBankAccounts();
    }

    public void setCurrentBankAccount(BankAccount currentBankAccount) {
        user.setCurrentBankAccount(currentBankAccount);
    }

    private void updateNumPendingTrades() {
        int numPendingTrades = tradeManager.getPendingTrades().size();
        if (numPendingTrades > 0)
            numPendingTradesAsString.set(String.valueOf(numPendingTrades));
        showPendingTradesNotification.set(numPendingTrades > 0);
    }

    private void setBitcoinNetworkSyncProgress(double value) {
        blockchainSyncProgress.set(value);
        if (value >= 1) {
            blockchainSyncInfo.set("Blockchain synchronization complete.");
            blockchainSyncIconId.set("image-connection-synced");
        }
        else if (value > 0.0) {
            blockchainSyncInfo.set("Synchronizing blockchain: " + formatter.formatToPercent(value));
        }
        else {
            blockchainSyncInfo.set("Connecting to the bitcoin network...");
        }
    }

    private void addMockArbitrator() {
        if (accountSettings.getAcceptedArbitrators().isEmpty() && user.getMessageKeyPair() != null) {
            String pubKeyAsHex = Utils.HEX.encode(new ECKey().getPubKey());
            String messagePubKeyAsHex = DSAKeyUtil.getHexStringFromPublicKey(user.getMessagePublicKey());
            List<Locale> languages = new ArrayList<>();
            languages.add(LanguageUtil.getDefaultLanguageLocale());
            List<Arbitrator.METHOD> arbitrationMethods = new ArrayList<>();
            arbitrationMethods.add(Arbitrator.METHOD.TLS_NOTARY);
            List<Arbitrator.ID_VERIFICATION> idVerifications = new ArrayList<>();
            idVerifications.add(Arbitrator.ID_VERIFICATION.PASSPORT);
            idVerifications.add(Arbitrator.ID_VERIFICATION.GOV_ID);

            Arbitrator arbitrator = new Arbitrator(pubKeyAsHex,
                    messagePubKeyAsHex,
                    "Manfred Karrer",
                    Arbitrator.ID_TYPE.REAL_LIFE_ID,
                    languages,
                    new Reputation(),
                    Coin.parseCoin("0.001"),
                    arbitrationMethods,
                    idVerifications,
                    "https://bitsquare.io/",
                    "Bla bla...");

            accountSettings.addAcceptedArbitrator(arbitrator);
            persistence.write(accountSettings);

            messageService.addArbitrator(arbitrator);
        }
    }
}
