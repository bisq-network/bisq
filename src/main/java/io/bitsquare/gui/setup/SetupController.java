package io.bitsquare.gui.setup;

import com.google.bitcoin.core.*;
import com.google.bitcoin.script.Script;
import com.google.inject.Inject;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.components.NetworkSyncPane;
import io.bitsquare.gui.util.ConfidenceDisplay;
import io.bitsquare.gui.util.Localisation;
import io.bitsquare.gui.util.Popups;
import io.bitsquare.gui.util.Verification;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.User;
import io.bitsquare.util.DSAKeyUtil;
import io.bitsquare.util.Utilities;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
import javafx.util.StringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URL;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public class SetupController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(SetupController.class);

    private final User user;
    private final WalletFacade walletFacade;
    private NavigationController navigationController;
    private MessageFacade messageFacade;
    private final Storage storage;
    private ConfidenceDisplay confidenceDisplay;

    @FXML
    private AnchorPane rootContainer;
    @FXML
    private TitledPane payRegistrationFeePane, addBankAccountPane, settingsPane;
    @FXML
    private Label payRegFeeInfoLabel, addBankAccountInfoLabel, copyIcon, confirmationLabel;
    @FXML
    private TextField registrationAddressTextField, balanceTextField, accountTitle, accountHolderName, accountPrimaryID, accountSecondaryID;
    @FXML
    private Button createAccountButton, addBankAccountButton, paymentDoneButton;
    @FXML
    private Accordion accordion;
    @FXML
    private ComboBox<Locale> countryComboBox;
    @FXML
    private ComboBox<BankAccountType> bankAccountTypesComboBox;
    @FXML
    private ComboBox<Currency> currencyComboBox;
    @FXML
    private ProgressIndicator progressIndicator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SetupController(User user, WalletFacade walletFacade, MessageFacade messageFacade, Storage storage)
    {
        this.user = user;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    // pass in NetworkSyncPane from parent view
    public void setNetworkSyncPane(NetworkSyncPane networkSyncPane)
    {
        rootContainer.getChildren().add(networkSyncPane);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        setupRegistrationScreen();
        setupBankAccountScreen();
        setupSettingsScreen();

        accordion.setExpandedPane(payRegistrationFeePane);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    @Override
    public void cleanup()
    {

    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Button handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onPaymentDone(ActionEvent actionEvent)
    {
        accordion.setExpandedPane(addBankAccountPane);
    }

    public void onSkipPayment(ActionEvent actionEvent)
    {
        accordion.setExpandedPane(addBankAccountPane);
    }

    public void onAddBankAccount(ActionEvent actionEvent)
    {
        addBankAccount();
        storage.write(user.getClass().getName(), user);

        if (verifyBankAccountData())
        {
            bankAccountTypesComboBox.getSelectionModel().clearSelection();
            accountPrimaryID.setText("");
            accountPrimaryID.setPromptText("");
            accountSecondaryID.setText("");
            accountSecondaryID.setPromptText("");
        }
    }

    public void onCreateAccount(ActionEvent actionEvent)
    {
        addBankAccount();
        if (user.getBankAccounts().size() > 0)
        {
            try
            {
                walletFacade.publishRegistrationTxWithExtraData(user.getStringifiedBankAccounts());
                user.setAccountID(walletFacade.getRegistrationAddress().toString());
                user.setMessagePubKeyAsHex(DSAKeyUtil.getHexStringFromPublicKey(messageFacade.getPubKey()));

                storage.write(user.getClass().getName(), user);

                accordion.setExpandedPane(settingsPane);
            } catch (InsufficientMoneyException e1)
            {
                Popups.openErrorPopup("Not enough money available", "There is not enough money available. Please pay in first to your wallet.");
            }
        }
    }

    public void onSkipBankAccountSetup(ActionEvent actionEvent)
    {
        accordion.setExpandedPane(settingsPane);
    }

    public void onClose(ActionEvent actionEvent)
    {
        navigationController.navigateToView(NavigationController.FUNDS, "");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Screens setup
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupRegistrationScreen()
    {
        payRegFeeInfoLabel.setText("You need to pay 0.01 BTC to the registration address.\n\n" +
                "That payment will be used to create a unique account connected with your bank account number.\n" +
                "The privacy of your bank account number will be protected and only revealed to your trading partners.\n" +
                "The payment will be spent to miners and is needed to store data into the blockchain.\n" +
                "Your trading account will be the source for your reputation in the trading platform.\n\n" +
                "You need at least 1 confirmation for doing the registration payment.");

        String registrationAddress = walletFacade.getRegistrationAddress().toString();
        registrationAddressTextField.setText(registrationAddress);

        copyIcon.setId("copy-icon");
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));
        copyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(registrationAddress);
            clipboard.setContent(content);
        });

        confidenceDisplay = new ConfidenceDisplay(walletFacade.getWallet(), confirmationLabel, balanceTextField, progressIndicator);
        paymentDoneButton.setDisable(walletFacade.getRegistrationBalance().compareTo(BigInteger.ZERO) == 0);
        log.debug("getAccountRegistrationBalance " + walletFacade.getRegistrationBalance().toString());
        walletFacade.getWallet().addEventListener(new WalletEventListener()
        {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
            {
                paymentDoneButton.setDisable(newBalance.compareTo(BigInteger.ZERO) == 0);
            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx)
            {
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
            {
            }

            @Override
            public void onReorganize(Wallet wallet)
            {
            }

            @Override
            public void onWalletChanged(Wallet wallet)
            {
            }

            @Override
            public void onKeysAdded(Wallet wallet, List<ECKey> keys)
            {
            }

            @Override
            public void onScriptsAdded(Wallet wallet, List<Script> scripts)
            {
            }
        });
    }

    private void setupBankAccountScreen()
    {
        addBankAccountInfoLabel.setText("Add at least one Bank account to your trading account.\n" +
                "That data will be stored in the blockchain in a way that your privacy is protected.\n" +
                "Only your trading partners will be able to read those data, so your privacy will be protected.");

        bankAccountTypesComboBox.setItems(FXCollections.observableArrayList(Utilities.getAllBankAccountTypes()));
        currencyComboBox.setItems(FXCollections.observableArrayList(Utilities.getAllCurrencies()));
        countryComboBox.setItems(FXCollections.observableArrayList(Utilities.getAllLocales()));

        bankAccountTypesComboBox.setConverter(new StringConverter<BankAccountType>()
        {
            @Override
            public String toString(BankAccountType bankAccountType)
            {
                return Localisation.get(bankAccountType.toString());
            }

            @Override
            public BankAccountType fromString(String s)
            {
                return null;
            }
        });

        currencyComboBox.setConverter(new StringConverter<Currency>()
        {
            @Override
            public String toString(Currency currency)
            {
                return currency.getCurrencyCode() + " (" + currency.getDisplayName() + ")";
            }

            @Override
            public Currency fromString(String s)
            {
                return null;
            }
        });

        countryComboBox.setConverter(new StringConverter<Locale>()
        {
            @Override
            public String toString(Locale locale)
            {
                return locale.getDisplayCountry();
            }

            @Override
            public Locale fromString(String s)
            {
                return null;
            }
        });

        bankAccountTypesComboBox.valueProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue != null && newValue instanceof BankAccountType)
            {
                accountPrimaryID.setText("");
                accountPrimaryID.setPromptText(newValue.getPrimaryIDName());
                accountSecondaryID.setText("");
                accountSecondaryID.setPromptText(newValue.getSecondaryIDName());

                checkCreateAccountButtonState();
            }
        });

        currencyComboBox.valueProperty().addListener((ov, oldValue, newValue) -> checkCreateAccountButtonState());
        countryComboBox.valueProperty().addListener((ov, oldValue, newValue) -> checkCreateAccountButtonState());


        checkCreateAccountButtonState();
        // handlers
        accountTitle.textProperty().addListener((ov, oldValue, newValue) -> checkCreateAccountButtonState());
        accountHolderName.textProperty().addListener((ov, oldValue, newValue) -> checkCreateAccountButtonState());
        accountPrimaryID.textProperty().addListener((ov, oldValue, newValue) -> checkCreateAccountButtonState());
        accountSecondaryID.textProperty().addListener((ov, oldValue, newValue) -> checkCreateAccountButtonState());

        //todo
        bankAccountTypesComboBox.getSelectionModel().select(0);
        currencyComboBox.getSelectionModel().select(0);
        countryComboBox.getSelectionModel().select(0);
        accountTitle.setText("Sepa EUR Account");
        accountHolderName.setText("Alice");
        accountPrimaryID.setText("123456");
        accountSecondaryID.setText("7896541");
    }

    private void setupSettingsScreen()
    {
        //TODO
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBankAccount()
    {
        if (verifyBankAccountData())
        {
            BankAccount bankAccount = new BankAccount(
                    bankAccountTypesComboBox.getSelectionModel().getSelectedItem(),
                    currencyComboBox.getSelectionModel().getSelectedItem(),
                    countryComboBox.getSelectionModel().getSelectedItem(),
                    accountTitle.getText(),
                    accountHolderName.getText(),
                    accountPrimaryID.getText(),
                    accountSecondaryID.getText());
            user.addBankAccount(bankAccount);
        }
    }

    private void checkCreateAccountButtonState()
    {
        createAccountButton.setDisable(!verifyBankAccountData());
        addBankAccountButton.setDisable(!verifyBankAccountData());
    }

    private boolean verifyBankAccountData()
    {
        boolean accountIDsByBankTransferTypeValid = Verification.verifyAccountIDsByBankTransferType(bankAccountTypesComboBox.getSelectionModel().getSelectedItem(),
                accountPrimaryID.getText(),
                accountSecondaryID.getText());

        return bankAccountTypesComboBox.getSelectionModel().getSelectedItem() != null
                && countryComboBox.getSelectionModel().getSelectedItem() != null
                && currencyComboBox.getSelectionModel().getSelectedItem() != null
                && accountTitle.getText().length() > 0
                && accountHolderName.getText().length() > 0
                && accountPrimaryID.getText().length() > 0
                && accountSecondaryID.getText().length() > 0
                && accountIDsByBankTransferTypeValid;
    }

}

