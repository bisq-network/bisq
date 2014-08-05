package io.bitsquare.gui.arbitrators.registration;

import com.google.bitcoin.core.*;
import com.google.bitcoin.script.Script;
import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.ChildController;
import io.bitsquare.gui.NavigationController;
import io.bitsquare.gui.arbitrators.profile.ArbitratorProfileController;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.util.BitSquareConverter;
import io.bitsquare.gui.util.BitSquareFormatter;
import io.bitsquare.gui.util.BitSquareValidator;
import io.bitsquare.gui.util.ConfidenceDisplay;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.locale.Localisation;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.storage.Persistence;
import io.bitsquare.user.Arbitrator;
import io.bitsquare.user.Reputation;
import io.bitsquare.user.User;
import io.bitsquare.util.DSAKeyUtil;
import java.net.URL;
import java.util.*;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javax.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({"ALL", "EmptyMethod", "UnusedParameters"})
public class ArbitratorRegistrationController implements Initializable, ChildController
{
    private static final Logger log = LoggerFactory.getLogger(ArbitratorRegistrationController.class);

    private final Persistence persistence;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;
    private User user;
    private Arbitrator arbitrator = new Arbitrator();
    private ArbitratorProfileController arbitratorProfileController;
    private boolean isEditMode;

    private List<Locale> languageList = new ArrayList<>();

    private List<Arbitrator.METHOD> methodList = new ArrayList<>();

    private List<Arbitrator.ID_VERIFICATION> idVerificationList = new ArrayList<>();
    private Arbitrator.ID_TYPE idType;
    private ConfidenceDisplay confidenceDisplay;

    @FXML
    private AnchorPane rootContainer;
    @FXML
    private Accordion accordion;
    @FXML
    private TitledPane profileTitledPane, payCollateralTitledPane;
    @FXML
    private Button saveProfileButton, paymentDoneButton;
    @FXML
    private Label nameLabel, infoLabel, copyIcon, confirmationLabel;
    @FXML
    private ComboBox<Locale> languageComboBox;
    @FXML
    private ComboBox<Arbitrator.ID_TYPE> idTypeComboBox;
    @FXML
    private ComboBox<Arbitrator.METHOD> methodsComboBox;
    @FXML
    private ComboBox<Arbitrator.ID_VERIFICATION> idVerificationsComboBox;
    @FXML
    private TextField nameTextField, idTypeTextField, languagesTextField, maxTradeVolumeTextField, passiveServiceFeeTextField, minPassiveServiceFeeTextField, arbitrationFeeTextField,
            minArbitrationFeeTextField, methodsTextField, idVerificationsTextField, webPageTextField, collateralAddressTextField, balanceTextField;
    @FXML
    private TextArea descriptionTextArea;
    @FXML
    private ConfidenceProgressIndicator progressIndicator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ArbitratorRegistrationController(Persistence persistence, WalletFacade walletFacade, MessageFacade messageFacade, User user)
    {
        this.persistence = persistence;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
        this.user = user;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setEditMode(@SuppressWarnings("SameParameterValue") boolean isEditMode)
    {
        this.isEditMode = isEditMode;

        if (isEditMode)
        {
            saveProfileButton.setText("Save");
            profileTitledPane.setCollapsible(false);
            payCollateralTitledPane.setVisible(false);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: Initializable
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb)
    {
        accordion.setExpandedPane(profileTitledPane);

        Arbitrator persistedArbitrator = (Arbitrator) persistence.read(arbitrator);
        if (persistedArbitrator != null)
        {
            arbitrator.applyPersistedArbitrator(persistedArbitrator);
            applyArbitrator();
        }
        else
        {
            languageList.add(LanguageUtil.getDefaultLanguageLocale());
            languagesTextField.setText(BitSquareFormatter.languageLocalesToString(languageList));
        }

        languageComboBox.setItems(FXCollections.observableArrayList(LanguageUtil.getAllLanguageLocales()));
        languageComboBox.setConverter(new StringConverter<Locale>()
        {
            @Override
            public String toString(Locale locale)
            {
                return locale.getDisplayLanguage();
            }


            @Override
            public Locale fromString(String s)
            {
                return null;
            }
        });

        idTypeComboBox.setItems(FXCollections.observableArrayList(new ArrayList<>(EnumSet.allOf(Arbitrator.ID_TYPE.class))));
        idTypeComboBox.setConverter(new StringConverter<Arbitrator.ID_TYPE>()
        {

            @Override
            public String toString(Arbitrator.ID_TYPE item)
            {
                return Localisation.get(item.toString());
            }


            @Override
            public Arbitrator.ID_TYPE fromString(String s)
            {
                return null;
            }
        });

        methodsComboBox.setItems(FXCollections.observableArrayList(new ArrayList<>(EnumSet.allOf(Arbitrator.METHOD.class))));
        methodsComboBox.setConverter(new StringConverter<Arbitrator.METHOD>()
        {

            @Override
            public String toString(Arbitrator.METHOD item)
            {
                return Localisation.get(item.toString());
            }


            @Override
            public Arbitrator.METHOD fromString(String s)
            {
                return null;
            }
        });

        idVerificationsComboBox.setItems(FXCollections.observableArrayList(new ArrayList<>(EnumSet.allOf(Arbitrator.ID_VERIFICATION.class))));
        idVerificationsComboBox.setConverter(new StringConverter<Arbitrator.ID_VERIFICATION>()
        {

            @Override
            public String toString(Arbitrator.ID_VERIFICATION item)
            {
                return Localisation.get(item.toString());
            }


            @Override
            public Arbitrator.ID_VERIFICATION fromString(String s)
            {
                return null;
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Interface implementation: ChildController
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setNavigationController(NavigationController navigationController)
    {
    }

    @Override
    public void cleanup()
    {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onSelectIDType()
    {
        idType = idTypeComboBox.getSelectionModel().getSelectedItem();
        if (idType != null)
        {
            idTypeTextField.setText(Localisation.get(idType.toString()));

            String name = "";
            switch (idType)
            {
                case REAL_LIFE_ID:
                    name = "Name:";
                    break;
                case NICKNAME:
                    name = "Nickname:";
                    break;
                case COMPANY:
                    name = "Company:";
                    break;
            }
            nameLabel.setText(name);

            idTypeComboBox.getSelectionModel().clearSelection();
        }
    }

    @FXML
    public void onAddLanguage()
    {
        Locale item = languageComboBox.getSelectionModel().getSelectedItem();
        if (!languageList.contains(item) && item != null)
        {
            languageList.add(item);
            languagesTextField.setText(BitSquareFormatter.languageLocalesToString(languageList));
            languageComboBox.getSelectionModel().clearSelection();
        }
    }

    @FXML
    public void onClearLanguages()
    {
        languageList.clear();
        languagesTextField.setText("");
    }

    @FXML
    public void onAddMethod()
    {
        Arbitrator.METHOD item = methodsComboBox.getSelectionModel().getSelectedItem();
        if (!methodList.contains(item) && item != null)
        {
            methodList.add(item);
            methodsTextField.setText(BitSquareFormatter.arbitrationMethodsToString(methodList));
            methodsComboBox.getSelectionModel().clearSelection();
        }
    }

    @FXML
    public void onClearMethods()
    {
        methodList.clear();
        methodsTextField.setText("");
    }


    @FXML
    public void onAddIDVerification()
    {
        Arbitrator.ID_VERIFICATION idVerification = idVerificationsComboBox.getSelectionModel().getSelectedItem();
        if (idVerification != null)
        {
            if (!idVerificationList.contains(idVerification))
            {
                idVerificationList.add(idVerification);
                idVerificationsTextField.setText(BitSquareFormatter.arbitrationIDVerificationsToString(idVerificationList));
            }
        }

        idVerificationsComboBox.getSelectionModel().clearSelection();
    }

    @FXML
    public void onClearIDVerifications()
    {
        idVerificationList.clear();
        idVerificationsTextField.setText("");
    }

    @FXML
    public void onSaveProfile()
    {
        arbitrator = getEditedArbitrator();
        if (arbitrator != null)
        {
            persistence.write(arbitrator);

            if (isEditMode)
            {
                close();
            }
            else
            {
                setupPayCollateralScreen();
                accordion.setExpandedPane(payCollateralTitledPane);
            }
        }

        messageFacade.addArbitrator(arbitrator);
    }

    @FXML
    public void onPaymentDone()
    {
        //To change body of created methods use File | Settings | File Templates.
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupPayCollateralScreen()
    {
        infoLabel.setText("You need to pay 10 x the max. trading volume as collateral.\n\n" +
                                  "That payment will be locked into a MultiSig fund and be refunded when you leave the arbitration pool.\n" +
                                  "In case of fraud (collusion, not fulfilling the min. dispute quality requirements) you will lose your collateral.\n" +
                                  "If you have a negative feedback from your clients you will lose a part of the collateral,\n" +
                                  "depending on the overall relation of negative to positive ratings you received after a dispute resolution.\n\n" +
                                  "Please pay in " + arbitrator.getMaxTradeVolume() * 10 + " BTC");


        String collateralAddress = walletFacade.getRegistrationAddressInfo() != null ? walletFacade.getRegistrationAddressInfo().toString() : "";
        collateralAddressTextField.setText(collateralAddress);

        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        copyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(collateralAddress);
            clipboard.setContent(content);
        });

        confidenceDisplay = new ConfidenceDisplay(walletFacade.getWallet(), confirmationLabel, balanceTextField, progressIndicator);
        paymentDoneButton.setDisable(walletFacade.getArbitratorDepositBalance().isZero());
        log.debug("getArbitratorDepositBalance " + walletFacade.getArbitratorDepositBalance());
        walletFacade.getWallet().addEventListener(new WalletEventListener()
        {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance)
            {
                paymentDoneButton.setDisable(newBalance.isZero());
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance)
            {

            }

            @Override
            public void onReorganize(Wallet wallet)
            {

            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx)
            {

            }

            @Override
            public void onWalletChanged(Wallet wallet)
            {

            }

            @Override
            public void onScriptsAdded(Wallet wallet, List<Script> scripts)
            {

            }

            @Override
            public void onKeysAdded(List<ECKey> keys)
            {

            }
        });
    }

    private void applyArbitrator()
    {
        if (arbitrator != null)
        {
            String name = "";
            switch (arbitrator.getIdType())
            {
                case REAL_LIFE_ID:
                    name = "Name:";
                    break;
                case NICKNAME:
                    name = "Nickname:";
                    break;
                case COMPANY:
                    name = "Company:";
                    break;
            }
            nameLabel.setText(name);

            nameTextField.setText(arbitrator.getName());
            idTypeTextField.setText(Localisation.get(arbitrator.getIdType().toString()));
            languagesTextField.setText(BitSquareFormatter.languageLocalesToString(arbitrator.getLanguages()));
            maxTradeVolumeTextField.setText(String.valueOf(arbitrator.getMaxTradeVolume()));
            passiveServiceFeeTextField.setText(String.valueOf(arbitrator.getPassiveServiceFee()));
            minPassiveServiceFeeTextField.setText(String.valueOf(arbitrator.getMinPassiveServiceFee()));
            arbitrationFeeTextField.setText(String.valueOf(arbitrator.getArbitrationFee()));
            minArbitrationFeeTextField.setText(String.valueOf(arbitrator.getMinArbitrationFee()));
            methodsTextField.setText(BitSquareFormatter.arbitrationMethodsToString(arbitrator.getArbitrationMethods()));
            idVerificationsTextField.setText(BitSquareFormatter.arbitrationIDVerificationsToString(arbitrator.getIdVerifications()));
            webPageTextField.setText(arbitrator.getWebUrl());
            descriptionTextArea.setText(arbitrator.getDescription());

            idType = arbitrator.getIdType();
            languageList = arbitrator.getLanguages();
            methodList = arbitrator.getArbitrationMethods();
            idVerificationList = arbitrator.getIdVerifications();
        }
    }


    private Arbitrator getEditedArbitrator()
    {
        try
        {
            BitSquareValidator.textFieldsNotEmptyWithReset(nameTextField, idTypeTextField, languagesTextField, methodsTextField, idVerificationsTextField);
            BitSquareValidator.textFieldsHasDoubleValueWithReset(maxTradeVolumeTextField,
                                                                 passiveServiceFeeTextField,
                                                                 minPassiveServiceFeeTextField,
                                                                 arbitrationFeeTextField,
                                                                 minArbitrationFeeTextField);

            String pubKeyAsHex = walletFacade.getArbitratorDepositAddressInfo().getPubKeyAsHexString();
            String messagePubKeyAsHex = DSAKeyUtil.getHexStringFromPublicKey(user.getMessagePublicKey());
            String name = nameTextField.getText();

            double maxTradeVolume = BitSquareConverter.stringToDouble(maxTradeVolumeTextField.getText());
            double passiveServiceFee = BitSquareConverter.stringToDouble(passiveServiceFeeTextField.getText());
            double minPassiveServiceFee = BitSquareConverter.stringToDouble(minPassiveServiceFeeTextField.getText());
            double arbitrationFee = BitSquareConverter.stringToDouble(arbitrationFeeTextField.getText());
            double minArbitrationFee = BitSquareConverter.stringToDouble(minArbitrationFeeTextField.getText());

            String webUrl = webPageTextField.getText();
            String description = descriptionTextArea.getText();

            return new Arbitrator(pubKeyAsHex,
                                  messagePubKeyAsHex,
                                  name,
                                  idType,
                                  languageList,
                                  new Reputation(),
                                  maxTradeVolume,
                                  passiveServiceFee,
                                  minPassiveServiceFee,
                                  arbitrationFee,
                                  minArbitrationFee,
                                  methodList,
                                  idVerificationList,
                                  webUrl,
                                  description);
        } catch (BitSquareValidator.ValidationException e)
        {
            return null;
        }
    }

    private void close()
    {
        Stage stage = (Stage) rootContainer.getScene().getWindow();
        stage.close();
    }


}

