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
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
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
    private int depthInBlocks = 0;

    private NavigationController navigationController;
    private ImageView confirmIconImageView;
    private TextField balanceLabel, confirmationsLabel;

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
        this.depthInBlocks = depthInBlocks;

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
    private boolean verifyBankAccountData(Object bankTransferTypeSelectedItem, String accountPrimaryID, String accountSecondaryID, String accountHolderName)
    {
        boolean result = bankTransferTypeSelectedItem != null;
        result &= bankTransferTypeSelectedItem.toString().length() > 0;
        result &= accountPrimaryID.length() > 0;
        result &= accountSecondaryID.length() > 0;
        result &= accountHolderName.length() > 0;
        result &= Verification.verifyAccountIDsByBankTransferType(bankTransferTypeSelectedItem, accountPrimaryID, accountSecondaryID);
        return result;
    }

    private Image getConfirmIconImage(int numBroadcastPeers, int depthInBlocks)
    {
        if (depthInBlocks > 0)
            return Icons.getIconImage(Icons.getIconIDForConfirmations(depthInBlocks));
        else
            return Icons.getIconImage(Icons.getIconIDForPeersSeenTx(numBroadcastPeers));
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

        skipButton.setOnAction(e -> {
            close();
        });
    }

    private void buildStep1()
    {
        infoLabel.setText("Add at least one Bank account to your trading account.\n" +
                "That data will be stored in the blockchain in a way that your privacy is protected.\n" +
                "Only your trading partners will be able to read those data, so your privacy will be protected.");

        formGridPane.getChildren().clear();
        int gridRow = -1;
        ComboBox bankTransferTypes = FormBuilder.addComboBox(formGridPane, "Bank account type:", settings.getAllBankAccountTypes(), ++gridRow);
        bankTransferTypes.setPromptText("Select");
        //TODO dev
        bankTransferTypes.getSelectionModel().select(1);
        TextField accountHolderName = FormBuilder.addInputField(formGridPane, "Bank account holder name:", "Bob Brown", ++gridRow);
        TextField accountPrimaryID = FormBuilder.addInputField(formGridPane, "Bank account primary ID", "dummy IBAN", ++gridRow);
        TextField accountSecondaryID = FormBuilder.addInputField(formGridPane, "Bank account secondary ID:", "dummy BIC", ++gridRow);
        Button addButton = new Button("Add other Bank account");
        formGridPane.add(addButton, 1, ++gridRow);

        nextButton.setText("Create account");
        nextButton.setDisable(true);
        skipButton.setText("Register later");

        // handlers
        bankTransferTypes.valueProperty().addListener(new ChangeListener<Object>()
        {
            @Override
            public void changed(ObservableValue ov, Object oldValue, Object newValue)
            {
                if (newValue != null && newValue instanceof BankAccountType)
                {
                    BankAccountType bankAccountType = (BankAccountType) newValue;
                    accountPrimaryID.setText("");
                    accountPrimaryID.setPromptText(bankAccountType.getPrimaryIDName());
                    accountSecondaryID.setText("");
                    accountSecondaryID.setPromptText(bankAccountType.getSecondaryIDName());

                    nextButton.setDisable(false);
                }
            }
        });

        addButton.setOnAction(e -> {
            if (bankTransferTypes.getSelectionModel() != null && verifyBankAccountData(bankTransferTypes.getSelectionModel().getSelectedItem(), accountPrimaryID.getText(), accountSecondaryID.getText(), accountHolderName.getText()))
            {
                user.addBankAccount(new BankAccount((BankAccountType) bankTransferTypes.getSelectionModel().getSelectedItem(), accountPrimaryID.getText(), accountSecondaryID.getText(), accountHolderName.getText()));

                bankTransferTypes.getSelectionModel().clearSelection();
                accountPrimaryID.setText("");
                accountPrimaryID.setPromptText("");
                accountSecondaryID.setText("");
                accountSecondaryID.setPromptText("");
            }
        });

        nextButton.setOnAction(e -> {
            if (bankTransferTypes.getSelectionModel() != null && verifyBankAccountData(bankTransferTypes.getSelectionModel().getSelectedItem(), accountPrimaryID.getText(), accountSecondaryID.getText(), accountHolderName.getText()))
                user.addBankAccount(new BankAccount((BankAccountType) bankTransferTypes.getSelectionModel().getSelectedItem(), accountPrimaryID.getText(), accountSecondaryID.getText(), accountHolderName.getText()));

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
                    //e1.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                }
            }
            else
            {
                log.warn("You need to add a bank account first!");
                //TODO warning popup
            }
        });

        skipButton.setOnAction(e -> {
            close();
        });
    }

    private void buildStep2()
    {
        infoLabel.setText("Summary:\n" +
                "You have saved following bank accounts with your trading account to the blockchain:");

        formGridPane.getChildren().clear();
        int gridRow = -1;
        Map<String, BankAccount> bankAccounts = user.getBankAccounts();
        Iterator iterator = bankAccounts.entrySet().iterator();
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
        nextButton.setOnAction(e -> {
            close();
        });
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

    private void updateCreateAccountButton()
    {
        boolean funded = walletFacade.getAccountRegistrationBalance().compareTo(BigInteger.ZERO) > 0;
        nextButton.setDisable(!funded || depthInBlocks == 0);
    }
}

