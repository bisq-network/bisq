package io.bitsquare.gui.setup;

import com.google.bitcoin.core.InsufficientMoneyException;
import com.google.inject.Inject;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.crypto.CryptoFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.components.NetworkSyncPane;
import io.bitsquare.gui.components.processbar.ProcessStepBar;
import io.bitsquare.gui.components.processbar.ProcessStepItem;
import io.bitsquare.gui.util.*;
import io.bitsquare.gui.util.Formatter;
import io.bitsquare.settings.Settings;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.User;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
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
    private CryptoFacade cryptoFacade;
    private Settings settings;
    private Storage storage;

    private List<ProcessStepItem> processStepItems = new ArrayList();

    private NavigationController navigationController;
    private ImageView confirmIconImageView;
    private TextField balanceLabel, confirmationsLabel, accountHolderName, accountPrimaryID, accountSecondaryID;
    private ComboBox countryComboBox, bankTransferTypeComboBox, currencyComboBox;
    private Button addBankAccountButton;

    @FXML
    private AnchorPane rootContainer;
    @FXML
    private Label infoLabel;
    @FXML
    private ProcessStepBar<String> processStepBar;
    @FXML
    private GridPane formGridPane;
    @FXML
    private Button nextButton, skipButton;


    @Inject
    public SetupController(User user, WalletFacade walletFacade, CryptoFacade cryptoFacade, Settings settings, Storage storage)
    {
        this.user = user;
        this.walletFacade = walletFacade;
        this.cryptoFacade = cryptoFacade;
        this.settings = settings;
        this.storage = storage;
    }

    public void initialize(URL url, ResourceBundle rb)
    {
        processStepItems.add(new ProcessStepItem("Fund registration fee", Colors.BLUE));
        processStepItems.add(new ProcessStepItem("Add Bank account", Colors.BLUE));
        processStepItems.add(new ProcessStepItem("Complete", Colors.BLUE));
        processStepBar.setProcessStepItems(processStepItems);

        walletFacade.addRegistrationWalletListener(this);

        buildStep0();
    }

    // pass in NetworkSyncPane from parent view
    public void setNetworkSyncPane(NetworkSyncPane networkSyncPane)
    {
        rootContainer.getChildren().add(networkSyncPane);
    }

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
        this.navigationController = navigationController;
    }

    @Override
    public void onConfidenceChanged(int numBroadcastPeers, int depthInBlocks)
    {
        updateCreateAccountButton();
        confirmIconImageView.setImage(getConfirmIconImage(numBroadcastPeers, depthInBlocks));
        confirmationsLabel.setText(getConfirmationsText(numBroadcastPeers, depthInBlocks));
        log.info("onConfidenceChanged " + numBroadcastPeers + " / " + depthInBlocks);
    }

    @Override
    public void onCoinsReceived(BigInteger newBalance)
    {
        updateCreateAccountButton();
        balanceLabel.setText(Formatter.formatSatoshis(walletFacade.getAccountRegistrationBalance(), true));
        log.info("onCoinsReceived " + newBalance);
    }


    private void close()
    {
        walletFacade.removeRegistrationWalletListener(this);
        navigationController.navigateToView(NavigationController.HOME, "");
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
                && accountPrimaryID.getText().length() > 0
                && accountSecondaryID.getText().length() > 0
                && accountHolderName.getText().length() > 0
                && accountIDsByBankTransferTypeValid;
    }

    private Image getConfirmIconImage(int numBroadcastPeers, int depthInBlocks)
    {
        if (depthInBlocks > 0)
            return Icons.getIconImage(Icons.getIconIDForConfirmations(depthInBlocks));
        else
            return Icons.getIconImage(Icons.getIconIDForPeersSeenTx(numBroadcastPeers));
    }

    private void updateCreateAccountButton()
    {
        boolean funded = walletFacade.getAccountRegistrationBalance().compareTo(BigInteger.ZERO) > 0;
        nextButton.setDisable(!funded || walletFacade.getRegistrationConfirmationDepthInBlocks() == 0);
    }


    ///////////////////////////////////////////////////////////////////////////////////
    // GUI BUILDER
    ///////////////////////////////////////////////////////////////////////////////////

    private String getConfirmationsText(int registrationConfirmationNumBroadcastPeers, int registrationConfirmationDepthInBlocks)
    {
        return registrationConfirmationDepthInBlocks + " confirmation(s) / " + "Seen by " + registrationConfirmationNumBroadcastPeers + " peer(s)";
    }

    private void buildStep0()
    {
        infoLabel.setText("You need to pay 0.01 BTC to the registration address.\n\n" +
                "That payment will be used to create a unique account connected with your bank account number.\n" +
                "The privacy of your bank account number will be protected and only revealed to your trading partners.\n" +
                "The payment will be spent to miners and is needed to store data into the blockchain.\n" +
                "Your trading account will be the source for your reputation in the trading platform.\n\n" +
                "You need at least 1 confirmation for doing the registration payment.");

        int gridRow = -1;

        TextField addressLabel = FormBuilder.addInputField(formGridPane, "Registration address:", walletFacade.getAccountRegistrationAddress().toString(), ++gridRow);
        addressLabel.setEditable(false);

        Label copyIcon = new Label("");
        formGridPane.add(copyIcon, 2, gridRow);
        copyIcon.setId("copy-icon");
        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        Tooltip.install(copyIcon, new Tooltip("Copy address to clipboard"));

        balanceLabel = FormBuilder.addInputField(formGridPane, "Balance:", Formatter.formatSatoshis(walletFacade.getAccountRegistrationBalance(), true), ++gridRow);
        balanceLabel.setEditable(false);

        confirmationsLabel = FormBuilder.addInputField(formGridPane, "Confirmations:", getConfirmationsText(walletFacade.getRegistrationConfirmationNumBroadcastPeers(), walletFacade.getRegistrationConfirmationDepthInBlocks()), ++gridRow);
        confirmationsLabel.setEditable(false);

        confirmIconImageView = new ImageView(getConfirmIconImage(walletFacade.getRegistrationConfirmationNumBroadcastPeers(), walletFacade.getRegistrationConfirmationDepthInBlocks()));
        formGridPane.add(confirmIconImageView, 2, gridRow);

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

        formGridPane.getChildren().clear();
        int gridRow = -1;
        bankTransferTypeComboBox = FormBuilder.addComboBox(formGridPane, "Bank account type:", settings.getAllBankAccountTypes(), ++gridRow);
        bankTransferTypeComboBox.setPromptText("Select bank account type");
        accountHolderName = FormBuilder.addInputField(formGridPane, "Bank account holder name:", "", ++gridRow);
        accountPrimaryID = FormBuilder.addInputField(formGridPane, "Bank account primary ID", "", ++gridRow);
        accountSecondaryID = FormBuilder.addInputField(formGridPane, "Bank account secondary ID:", "", ++gridRow);

        currencyComboBox = FormBuilder.addComboBox(formGridPane, "Currency used for bank account:", settings.getAllCurrencies(), ++gridRow);
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

        countryComboBox = FormBuilder.addComboBox(formGridPane, "Country of bank account:", settings.getAllLocales(), ++gridRow);
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
        formGridPane.add(addBankAccountButton, 1, ++gridRow);

        nextButton.setText("Create account");
        checkCreateAccountButtonState();
        skipButton.setText("Register later");

        // handlers
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

                    storage.saveUser(user);

                    processStepBar.next();
                    buildStep2();
                } catch (InsufficientMoneyException e1)
                {
                    log.warn(e1.toString());
                }
            }
            else
            {
                log.warn("You need to add a bank account first!");
                //TODO warning popup
            }
        });

        skipButton.setOnAction(e -> close());
    }

    private void addBankAccount()
    {
        if (verifyBankAccountData())
        {
            BankAccount bankAccount = new BankAccount((BankAccountType) bankTransferTypeComboBox.getSelectionModel().getSelectedItem(),
                    accountPrimaryID.getText(),
                    accountSecondaryID.getText(),
                    accountHolderName.getText(),
                    (Locale) countryComboBox.getSelectionModel().getSelectedItem(),
                    (Currency) currencyComboBox.getSelectionModel().getSelectedItem());
            user.addBankAccount(bankAccount);
        }
    }

    private void checkCreateAccountButtonState()
    {
        nextButton.setDisable(!verifyBankAccountData());
        addBankAccountButton.setDisable(!verifyBankAccountData());
    }

    private void buildStep2()
    {
        infoLabel.setText("Summary:\n" +
                "You have saved following bank accounts with your trading account to the blockchain:");

        formGridPane.getChildren().clear();
        int gridRow = -1;
        List<BankAccount> bankAccounts = user.getBankAccounts();
        Iterator iterator = bankAccounts.iterator();
        int index = 0;
        while (iterator.hasNext())
        {
            FormBuilder.addHeaderLabel(formGridPane, "Bank account " + (index + 1), ++gridRow);
            Map.Entry<String, BankAccount> entry = (Map.Entry) iterator.next();
            // need to get updated gridRow from subroutine
            gridRow = buildBankAccountDetails(entry.getValue(), ++gridRow);
            FormBuilder.addVSpacer(formGridPane, ++gridRow);
            index++;
        }
        FormBuilder.addVSpacer(formGridPane, ++gridRow);
        FormBuilder.addInputField(formGridPane, "Registration address:", walletFacade.getAccountRegistrationAddress().toString(), ++gridRow).setMouseTransparent(true);
        FormBuilder.addInputField(formGridPane, "Balance:", Formatter.formatSatoshis(walletFacade.getAccountRegistrationBalance(), true), ++gridRow).setMouseTransparent(true);

        nextButton.setText("Done");
        skipButton.setOpacity(0);

        // handlers
        nextButton.setOnAction(e -> close());
    }

    // util
    private int buildBankAccountDetails(BankAccount bankAccount, int row)
    {
        FormBuilder.addInputField(formGridPane, "Bank account holder name:", bankAccount.getAccountHolderName(), ++row).setMouseTransparent(true);
        FormBuilder.addInputField(formGridPane, "Bank account type", bankAccount.getBankAccountType().toString(), ++row).setMouseTransparent(true);
        FormBuilder.addInputField(formGridPane, "Bank account primary ID", bankAccount.getAccountPrimaryID(), ++row).setMouseTransparent(true);
        FormBuilder.addInputField(formGridPane, "Bank account secondary ID:", bankAccount.getAccountSecondaryID(), ++row).setMouseTransparent(true);
        return row;
    }
}

