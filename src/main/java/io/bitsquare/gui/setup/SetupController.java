package io.bitsquare.gui.setup;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.inject.Inject;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.components.ConfirmationComponent;
import io.bitsquare.gui.components.NetworkSyncPane;
import io.bitsquare.gui.components.processbar.ProcessStepBar;
import io.bitsquare.gui.components.processbar.ProcessStepItem;
import io.bitsquare.gui.util.FormBuilder;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.gui.util.Localisation;
import io.bitsquare.gui.util.Verification;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.User;
import io.bitsquare.util.Utils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import org.controlsfx.dialog.Dialogs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.URL;
import java.util.*;

public class SetupController implements Initializable, ChildController, WalletFacade.WalletListener
{
    private static final Logger log = LoggerFactory.getLogger(SetupController.class);

    private User user;
    private final WalletFacade walletFacade;
    private Storage storage;
    private List<ProcessStepItem> processStepItems = new ArrayList();
    private NavigationController navigationController;
    private TextField balanceLabel, accountTitle, accountHolderName, accountPrimaryID, accountSecondaryID;
    private ComboBox countryComboBox, bankTransferTypeComboBox, currencyComboBox;
    private Button addBankAccountButton;

    @FXML
    private AnchorPane rootContainer;
    @FXML
    private Label infoLabel;
    @FXML
    private ProcessStepBar<String> processStepBar;
    @FXML
    private GridPane gridPane;
    @FXML
    private Button nextButton, skipButton;
    @FXML
    private VBox vBox;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor(s)
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public SetupController(User user, WalletFacade walletFacade, Storage storage)
    {
        this.user = user;
        this.walletFacade = walletFacade;
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
        processStepItems.add(new ProcessStepItem("Fund registration fee"));
        processStepItems.add(new ProcessStepItem("Add Bank account"));
        processStepItems.add(new ProcessStepItem("Complete"));
        processStepBar.setProcessStepItems(processStepItems);

        walletFacade.addRegistrationWalletListener(this);

        buildStep0();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: WalletFacade.WalletListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConfidenceChanged(int numBroadcastPeers, int depthInBlocks)
    {
        updateCreateAccountButton();

        log.info("onConfidenceChanged " + numBroadcastPeers + " / " + depthInBlocks);
    }

    @Override
    public void onCoinsReceived(BigInteger newBalance)
    {
        updateCreateAccountButton();
        balanceLabel.setText(Formatter.formatSatoshis(walletFacade.getAccountRegistrationBalance(), true));
        log.info("onCoinsReceived " + newBalance);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void buildStep0()
    {
        infoLabel.setText("You need to pay 0.01 BTC to the registration address.\n\n" +
                "That payment will be used to create a unique account connected with your bank account number.\n" +
                "The privacy of your bank account number will be protected and only revealed to your trading partners.\n" +
                "The payment will be spent to miners and is needed to store data into the blockchain.\n" +
                "Your trading account will be the source for your reputation in the trading platform.\n\n" +
                "You need at least 1 confirmation for doing the registration payment.");

        int row = -1;

        TextField addressLabel = FormBuilder.addTextField(gridPane, "Registration address:", walletFacade.getAccountRegistrationAddress().toString(), ++row, false, true);

        Label copyIcon = new Label("");
        gridPane.add(copyIcon, 2, row);
        copyIcon.setId("copy-icon");
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));

        balanceLabel = FormBuilder.addTextField(gridPane, "Balance:", Formatter.formatSatoshis(walletFacade.getAccountRegistrationBalance(), true), ++row);

        new ConfirmationComponent(walletFacade, gridPane, ++row);

        nextButton.setText("Payment done");
        updateCreateAccountButton();

        skipButton.setText("Register later");

        // handlers
        copyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(addressLabel.getText());
            clipboard.setContent(content);
        });

        nextButton.setOnAction(e -> {
            processStepBar.next();
            buildStep1();
        });

        skipButton.setOnAction(e -> close());
    }

    private void buildStep1()
    {
        infoLabel.setText("Add at least one Bank account to your trading account.\n" +
                "That data will be stored in the blockchain in a way that your privacy is protected.\n" +
                "Only your trading partners will be able to read those data, so your privacy will be protected.");

        gridPane.getChildren().clear();
        int row = -1;
        bankTransferTypeComboBox = FormBuilder.addComboBox(gridPane, "Bank account type:", Utils.getAllBankAccountTypes(), ++row);
        bankTransferTypeComboBox.setConverter(new StringConverter<BankAccountType>()
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

        bankTransferTypeComboBox.setPromptText("Select bank account type");
        accountTitle = FormBuilder.addInputField(gridPane, "Bank account title:", "", ++row);
        accountHolderName = FormBuilder.addInputField(gridPane, "Bank account holder name:", "", ++row);
        accountPrimaryID = FormBuilder.addInputField(gridPane, "Bank account primary ID", "", ++row);
        accountSecondaryID = FormBuilder.addInputField(gridPane, "Bank account secondary ID:", "", ++row);

        currencyComboBox = FormBuilder.addComboBox(gridPane, "Currency used for bank account:", Utils.getAllCurrencies(), ++row);
        currencyComboBox.setPromptText("Select currency");
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

        countryComboBox = FormBuilder.addComboBox(gridPane, "Country of bank account:", Utils.getAllLocales(), ++row);
        countryComboBox.setPromptText("Select country");
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


        addBankAccountButton = new Button("Add other Bank account");
        gridPane.add(addBankAccountButton, 1, ++row);

        nextButton.setText("Create account");
        checkCreateAccountButtonState();
        skipButton.setText("Register later");

        // handlers
        accountTitle.textProperty().addListener((ov, oldValue, newValue) -> checkCreateAccountButtonState());
        accountHolderName.textProperty().addListener((ov, oldValue, newValue) -> checkCreateAccountButtonState());
        accountPrimaryID.textProperty().addListener((ov, oldValue, newValue) -> checkCreateAccountButtonState());
        accountSecondaryID.textProperty().addListener((ov, oldValue, newValue) -> checkCreateAccountButtonState());

        bankTransferTypeComboBox.valueProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue != null && newValue instanceof BankAccountType)
            {
                BankAccountType bankAccountType = (BankAccountType) newValue;
                accountPrimaryID.setText("");
                accountPrimaryID.setPromptText(bankAccountType.getPrimaryIDName());
                accountSecondaryID.setText("");
                accountSecondaryID.setPromptText(bankAccountType.getSecondaryIDName());

                checkCreateAccountButtonState();
            }
        });

        currencyComboBox.valueProperty().addListener((ov, oldValue, newValue) -> checkCreateAccountButtonState());
        countryComboBox.valueProperty().addListener((ov, oldValue, newValue) -> checkCreateAccountButtonState());

        addBankAccountButton.setOnAction(e -> {
            addBankAccount();
            storage.write(user.getClass().getName(), user);

            if (verifyBankAccountData())
            {
                bankTransferTypeComboBox.getSelectionModel().clearSelection();
                accountPrimaryID.setText("");
                accountPrimaryID.setPromptText("");
                accountSecondaryID.setText("");
                accountSecondaryID.setPromptText("");
            }
        });

        nextButton.setOnAction(e -> {
            addBankAccount();

            if (user.getBankAccounts().size() > 0)
            {
                try
                {
                    walletFacade.sendRegistrationTx(user.getStringifiedBankAccounts());
                    user.setAccountID(walletFacade.getAccountRegistrationAddress().toString());
                    user.setMessageID(walletFacade.getAccountRegistrationPubKey().toString());

                    storage.write(user.getClass().getName(), user);
                    processStepBar.next();
                    buildStep2();
                } catch (InsufficientMoneyException e1)
                {
                    Dialogs.create()
                            .title("Not enough money available")
                            .message("There is not enough money available. Please pay in first to your wallet.")
                            .nativeTitleBar()
                            .lightweight()
                            .showError();
                }
            }
        });

        skipButton.setOnAction(e -> close());
    }

    private void buildStep2()
    {
        vBox.getChildren().remove(infoLabel);
        vBox.getChildren().remove(nextButton);
        vBox.getChildren().remove(skipButton);

        gridPane.getChildren().clear();
        int row = -1;

        FormBuilder.addHeaderLabel(gridPane, "Registration complete", ++row);
        FormBuilder.addTextField(gridPane, "Registration address:", walletFacade.getAccountRegistrationAddress().toString(), ++row);
        FormBuilder.addTextField(gridPane, "Balance:", Formatter.formatSatoshis(walletFacade.getAccountRegistrationBalance(), true), ++row);

        Button closeButton = FormBuilder.addButton(gridPane, "Close", ++row);
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(e -> close());

        FormBuilder.addVSpacer(gridPane, ++row);

        FormBuilder.addHeaderLabel(gridPane, "Summary", ++row);
        Label info = new Label("You have saved following bank accounts with your trading account to the blockchain:");
        gridPane.add(info, 0, ++row);
        GridPane.setColumnSpan(info, 3);

        FormBuilder.addVSpacer(gridPane, ++row);

        List<BankAccount> bankAccounts = user.getBankAccounts();
        Iterator<BankAccount> iterator = bankAccounts.iterator();
        int index = 0;
        while (iterator.hasNext())
        {
            FormBuilder.addHeaderLabel(gridPane, "Bank account " + (index + 1), ++row);
            BankAccount bankAccount = iterator.next();
            // need to get updated row from subroutine
            row = buildBankAccountDetails(bankAccount, ++row);
            FormBuilder.addVSpacer(gridPane, ++row);
            index++;
        }
    }

    private void close()
    {
        walletFacade.removeRegistrationWalletListener(this);
        navigationController.navigateToView(NavigationController.HOME, "");
    }


    // util
    private int buildBankAccountDetails(BankAccount bankAccount, int row)
    {
        FormBuilder.addTextField(gridPane, "Bank account holder name:", bankAccount.getAccountHolderName(), ++row);
        FormBuilder.addTextField(gridPane, "Bank account type", bankAccount.getBankAccountType().toString(), ++row);
        FormBuilder.addTextField(gridPane, "Bank account primary ID", bankAccount.getAccountPrimaryID(), ++row);
        FormBuilder.addTextField(gridPane, "Bank account secondary ID:", bankAccount.getAccountSecondaryID(), ++row);
        return row;
    }

    // TODO need checks per bankTransferType
    private boolean verifyBankAccountData()
    {
        boolean accountIDsByBankTransferTypeValid = Verification.verifyAccountIDsByBankTransferType(bankTransferTypeComboBox.getSelectionModel().getSelectedItem(),
                accountPrimaryID.getText(),
                accountSecondaryID.getText());

        return bankTransferTypeComboBox.getSelectionModel().getSelectedItem() != null
                && countryComboBox.getSelectionModel().getSelectedItem() != null
                && currencyComboBox.getSelectionModel().getSelectedItem() != null
                && accountTitle.getText().length() > 0
                && accountHolderName.getText().length() > 0
                && accountPrimaryID.getText().length() > 0
                && accountSecondaryID.getText().length() > 0
                && accountIDsByBankTransferTypeValid;
    }

    private void updateCreateAccountButton()
    {
        boolean funded = walletFacade.getAccountRegistrationBalance().compareTo(BigInteger.ZERO) > 0;
        nextButton.setDisable(!funded || walletFacade.getRegConfDepthInBlocks() == 0);
    }


    private void addBankAccount()
    {
        if (verifyBankAccountData())
        {
            BankAccount bankAccount = new BankAccount(
                    (BankAccountType) bankTransferTypeComboBox.getSelectionModel().getSelectedItem(),
                    (Currency) currencyComboBox.getSelectionModel().getSelectedItem(),
                    (Locale) countryComboBox.getSelectionModel().getSelectedItem(),
                    accountTitle.getText(),
                    accountHolderName.getText(),
                    accountPrimaryID.getText(),
                    accountSecondaryID.getText());
            user.addBankAccount(bankAccount);
        }
    }

    private void checkCreateAccountButtonState()
    {
        nextButton.setDisable(!verifyBankAccountData());
        addBankAccountButton.setDisable(!verifyBankAccountData());
    }
}

