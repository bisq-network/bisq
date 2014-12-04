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
import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.arbitrator.Reputation;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.BitcoinNetwork;
import io.bitsquare.btc.WalletService;
import io.bitsquare.gui.components.Popups;
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

import viewfx.model.ViewModel;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
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

    final DoubleProperty networkSyncProgress = new SimpleDoubleProperty(-1);
    final IntegerProperty numPendingTrades = new SimpleIntegerProperty(0);
    final StringProperty numPendingTradesAsString = new SimpleStringProperty();
    final ObjectProperty<BootstrapState> bootstrapState = new SimpleObjectProperty<>();
    final StringProperty bootstrapStateText = new SimpleStringProperty();
    final ObjectProperty walletServiceException = new SimpleObjectProperty<Throwable>();

    final StringProperty bankAccountsComboBoxPrompt = new SimpleStringProperty();
    final BooleanProperty bankAccountsComboBoxDisable = new SimpleBooleanProperty();

    final StringProperty blockchainSyncState = new SimpleStringProperty("Initializing");
    final DoubleProperty blockchainSyncProgress = new SimpleDoubleProperty();
    final BooleanProperty blockchainSyncIndicatorVisible = new SimpleBooleanProperty(true);
    final StringProperty blockchainSyncIconId = new SimpleStringProperty();
    final StringProperty walletServiceErrorMsg = new SimpleStringProperty();
    final BooleanProperty isReadyForMainScreen = new SimpleBooleanProperty();

    final DoubleProperty bootstrapProgress = new SimpleDoubleProperty(-1);
    final BooleanProperty bootstrapFailed = new SimpleBooleanProperty();
    final StringProperty bootstrapErrorMsg = new SimpleStringProperty();
    final StringProperty bootstrapIconId = new SimpleStringProperty();

    final StringProperty featureNotImplementedWarning = new SimpleStringProperty();
    final ObjectProperty<BankAccount> currentBankAccount = new SimpleObjectProperty<>();
    final String bitcoinNetworkAsString;

    private final User user;
    private final WalletService walletService;
    private final MessageService messageService;
    private final TradeManager tradeManager;
    private final BSFormatter formatter;
    private Persistence persistence;
    private AccountSettings accountSettings;

    @Inject
    public MainViewModel(User user, WalletService walletService, MessageService messageService,
                         TradeManager tradeManager, BitcoinNetwork bitcoinNetwork, BSFormatter formatter,
                         Persistence persistence, AccountSettings accountSettings) {
        this.user = user;
        this.walletService = walletService;
        this.messageService = messageService;
        this.tradeManager = tradeManager;
        this.formatter = formatter;
        this.persistence = persistence;
        this.accountSettings = accountSettings;

        bitcoinNetworkAsString = bitcoinNetwork.toString();

        user.getCurrentBankAccount().addListener((observable, oldValue, newValue) -> persistence.write(user));

        currentBankAccount.bind(user.currentBankAccountProperty());

        bootstrapState.addListener((ov, oldValue, newValue) -> {
                    if (newValue == BootstrapState.DISCOVERY_DIRECT_SUCCEEDED ||
                            newValue == BootstrapState.DISCOVERY_AUTO_PORT_FORWARDING_SUCCEEDED ||
                            newValue == BootstrapState.RELAY_SUCCEEDED) {
                        bootstrapStateText.set("Successfully connected to P2P network: " + newValue.getMessage());
                        bootstrapProgress.set(1);

                        if (newValue == BootstrapState.DISCOVERY_DIRECT_SUCCEEDED)
                            bootstrapIconId.set("image-connection-direct");
                        else if (newValue == BootstrapState.DISCOVERY_AUTO_PORT_FORWARDING_SUCCEEDED)
                            bootstrapIconId.set("image-connection-nat");
                        else if (newValue == BootstrapState.RELAY_SUCCEEDED)
                            bootstrapIconId.set("image-connection-relay");
                    }
                    else if (newValue == BootstrapState.PEER_CREATION_FAILED ||
                            newValue == BootstrapState.DISCOVERY_FAILED ||
                            newValue == BootstrapState.DISCOVERY_AUTO_PORT_FORWARDING_FAILED ||
                            newValue == BootstrapState.RELAY_FAILED) {

                        bootstrapErrorMsg.set(newValue.getMessage());
                        bootstrapStateText.set("Connection to P2P network failed.");
                        bootstrapProgress.set(0);
                        bootstrapFailed.set(true);
                    }
                    else {
                        bootstrapStateText.set("Connecting to P2P network: " + newValue.getMessage());
                    }
                }
        );

        walletServiceException.addListener((ov, oldValue, newValue) -> {
            blockchainSyncProgress.set(0);
            blockchainSyncIndicatorVisible.set(false);
            blockchainSyncState.set("Startup failed.");
            walletServiceErrorMsg.set(((Throwable) newValue).getMessage());
        });

        networkSyncProgress.addListener((ov, oldValue, newValue) -> {
            setNetworkSyncProgress((double) newValue);

            if ((double) newValue >= 1)
                blockchainSyncIconId.set("image-connection-synced");
        });
        setNetworkSyncProgress(networkSyncProgress.get());


        user.getBankAccounts().addListener((ListChangeListener<BankAccount>) change -> {
            bankAccountsComboBoxDisable.set(change.getList().isEmpty());
            bankAccountsComboBoxPrompt.set(change.getList().isEmpty() ? "No accounts" : "");
        });
        bankAccountsComboBoxDisable.set(user.getBankAccounts().isEmpty());
        bankAccountsComboBoxPrompt.set(user.getBankAccounts().isEmpty() ? "No accounts" : "");


        tradeManager.featureNotImplementedWarningProperty().addListener((ov, oldValue, newValue) -> {
            if (oldValue == null && newValue != null) {
                featureNotImplementedWarning.set(newValue);
                Popups.openWarningPopup(newValue);
                tradeManager.setFeatureNotImplementedWarning(null);
            }
        });
    }

    public void initBackend() {
        walletService.getDownloadProgress().subscribe(
                percentage -> Platform.runLater(() -> networkSyncProgress.set(percentage / 100.0)),
                error -> log.error(error.toString()),
                () -> Platform.runLater(() -> networkSyncProgress.set(1.0)));

        Observable<BootstrapState> message = messageService.init();
        message.publish();
        message.subscribe(
                state -> Platform.runLater(() -> bootstrapState.set(state)),
                error -> log.error(error.toString()),
                () -> log.trace("message completed"));

        Observable<Object> wallet = walletService.initialize(Platform::runLater);
        wallet.subscribe(
                next -> {
                },
                error -> Platform.runLater(() -> walletServiceException.set(error)),
                () -> log.trace("wallet completed"));

        Observable<?> backend = Observable.merge(message, wallet);
        backend.subscribe(
                next -> {
                },
                error -> log.error(error.toString()),
                () -> Platform.runLater(() -> backEndCompleted())
        );
    }

    private void backEndCompleted() {
        log.trace("backend completed");
        tradeManager.getPendingTrades().addListener(
                (MapChangeListener<String, Trade>) change -> updateNumPendingTrades());
        updateNumPendingTrades();
        isReadyForMainScreen.set(true);

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
        numPendingTrades.set(tradeManager.getPendingTrades().size());
        if (numPendingTrades.get() > 0)
            numPendingTradesAsString.set(String.valueOf(numPendingTrades.get()));
    }

    private void setNetworkSyncProgress(double value) {
        blockchainSyncProgress.set(value);
        if (value >= 1)
            blockchainSyncState.set("Synchronization completed.");
        else if (value > 0.0)
            blockchainSyncState.set("Synchronizing blockchain: " + formatter.formatToPercent(value));
        else
            blockchainSyncState.set("Connecting to bitcoin network...");

        blockchainSyncIndicatorVisible.set(value < 1);
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
