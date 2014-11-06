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

package io.bitsquare.gui.main.account.arbitrator.registration;

import io.bitsquare.arbitrator.Arbitrator;
import io.bitsquare.arbitrator.Reputation;
import io.bitsquare.btc.WalletFacade;
import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.components.confidence.ConfidenceProgressIndicator;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.LanguageUtil;
import io.bitsquare.msg.MessageFacade;
import io.bitsquare.persistence.Persistence;
import io.bitsquare.user.User;
import io.bitsquare.util.DSAKeyUtil;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.core.WalletEventListener;
import org.bitcoinj.script.Script;

import java.net.URL;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO Arbitration is very basic yet
public class ArbitratorRegistrationViewCB extends CachedViewCB {
    private static final Logger log = LoggerFactory.getLogger(ArbitratorRegistrationViewCB.class);

    private final Persistence persistence;
    private final WalletFacade walletFacade;
    private final MessageFacade messageFacade;
    private final User user;
    private final BSFormatter formatter;
    private Arbitrator arbitrator = new Arbitrator();
    private boolean isEditMode;

    private List<Locale> languageList = new ArrayList<>();

    private List<Arbitrator.METHOD> methodList = new ArrayList<>();

    private List<Arbitrator.ID_VERIFICATION> idVerificationList = new ArrayList<>();
    private Arbitrator.ID_TYPE idType;

    @FXML Accordion accordion;
    @FXML TitledPane profileTitledPane, paySecurityDepositTitledPane;
    @FXML Button saveProfileButton, paymentDoneButton;
    @FXML Label nameLabel, infoLabel, copyIcon, confirmationLabel;
    @FXML ComboBox<Locale> languageComboBox;
    @FXML ComboBox<Arbitrator.ID_TYPE> idTypeComboBox;
    @FXML ComboBox<Arbitrator.METHOD> methodsComboBox;
    @FXML ComboBox<Arbitrator.ID_VERIFICATION> idVerificationsComboBox;
    @FXML TextField nameTextField, idTypeTextField, languagesTextField, maxTradeVolumeTextField,
            passiveServiceFeeTextField, minPassiveServiceFeeTextField, arbitrationFeeTextField,
            minArbitrationFeeTextField, methodsTextField, idVerificationsTextField, webPageTextField,
            securityDepositAddressTextField, balanceTextField;
    @FXML TextArea descriptionTextArea;
    @FXML ConfidenceProgressIndicator progressIndicator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ArbitratorRegistrationViewCB(Persistence persistence, WalletFacade walletFacade,
                                         MessageFacade messageFacade, User user, BSFormatter formatter) {
        this.persistence = persistence;
        this.walletFacade = walletFacade;
        this.messageFacade = messageFacade;
        this.user = user;
        this.formatter = formatter;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        accordion.setExpandedPane(profileTitledPane);

        Arbitrator persistedArbitrator = (Arbitrator) persistence.read(arbitrator);
        if (persistedArbitrator != null) {
            arbitrator.applyPersistedArbitrator(persistedArbitrator);
            applyArbitrator();
        }
        else {
            languageList.add(LanguageUtil.getDefaultLanguageLocale());
            languagesTextField.setText(formatter.languageLocalesToString(languageList));
        }

        languageComboBox.setItems(FXCollections.observableArrayList(LanguageUtil.getAllLanguageLocales()));
        languageComboBox.setConverter(new StringConverter<Locale>() {
            @Override
            public String toString(Locale locale) {
                return locale.getDisplayLanguage();
            }


            @Override
            public Locale fromString(String s) {
                return null;
            }
        });

        idTypeComboBox.setItems(FXCollections.observableArrayList(
                new ArrayList<>(EnumSet.allOf(Arbitrator.ID_TYPE.class))));
        idTypeComboBox.setConverter(new StringConverter<Arbitrator.ID_TYPE>() {

            @Override
            public String toString(Arbitrator.ID_TYPE item) {
                return BSResources.get(item.toString());
            }


            @Override
            public Arbitrator.ID_TYPE fromString(String s) {
                return null;
            }
        });

        methodsComboBox.setItems(FXCollections.observableArrayList(new ArrayList<>(EnumSet.allOf(Arbitrator.METHOD
                .class))));
        methodsComboBox.setConverter(new StringConverter<Arbitrator.METHOD>() {

            @Override
            public String toString(Arbitrator.METHOD item) {
                return BSResources.get(item.toString());
            }


            @Override
            public Arbitrator.METHOD fromString(String s) {
                return null;
            }
        });

        idVerificationsComboBox.setItems(
                FXCollections.observableArrayList(new ArrayList<>(EnumSet.allOf(Arbitrator.ID_VERIFICATION.class))));
        idVerificationsComboBox.setConverter(new StringConverter<Arbitrator.ID_VERIFICATION>() {

            @Override
            public String toString(Arbitrator.ID_VERIFICATION item) {
                return BSResources.get(item.toString());
            }


            @Override
            public Arbitrator.ID_VERIFICATION fromString(String s) {
                return null;
            }
        });
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setEditMode(boolean isEditMode) {
        this.isEditMode = isEditMode;

        if (isEditMode) {
            saveProfileButton.setText("Save");
            profileTitledPane.setCollapsible(false);
            paySecurityDepositTitledPane.setVisible(false);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onSelectIDType() {
        idType = idTypeComboBox.getSelectionModel().getSelectedItem();
        if (idType != null) {
            idTypeTextField.setText(BSResources.get(idType.toString()));

            String name = "";
            switch (idType) {
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
    public void onAddLanguage() {
        Locale item = languageComboBox.getSelectionModel().getSelectedItem();
        if (!languageList.contains(item) && item != null) {
            languageList.add(item);
            languagesTextField.setText(formatter.languageLocalesToString(languageList));
            languageComboBox.getSelectionModel().clearSelection();
        }
    }

    @FXML
    public void onClearLanguages() {
        languageList.clear();
        languagesTextField.setText("");
    }

    @FXML
    public void onAddMethod() {
        Arbitrator.METHOD item = methodsComboBox.getSelectionModel().getSelectedItem();
        if (!methodList.contains(item) && item != null) {
            methodList.add(item);
            methodsTextField.setText(formatter.arbitrationMethodsToString(methodList));
            methodsComboBox.getSelectionModel().clearSelection();
        }
    }

    @FXML
    public void onClearMethods() {
        methodList.clear();
        methodsTextField.setText("");
    }


    @FXML
    public void onAddIDVerification() {
        Arbitrator.ID_VERIFICATION idVerification = idVerificationsComboBox.getSelectionModel().getSelectedItem();
        if (idVerification != null) {
            if (!idVerificationList.contains(idVerification)) {
                idVerificationList.add(idVerification);
                idVerificationsTextField.setText(
                        formatter.arbitrationIDVerificationsToString(idVerificationList));
            }
        }

        idVerificationsComboBox.getSelectionModel().clearSelection();
    }

    @FXML
    public void onClearIDVerifications() {
        idVerificationList.clear();
        idVerificationsTextField.setText("");
    }

    @FXML
    public void onSaveProfile() {
        arbitrator = getEditedArbitrator();
        if (arbitrator != null) {
            persistence.write(arbitrator);

            if (isEditMode) {
                close();
            }
            else {
                setupPaySecurityDepositScreen();
                accordion.setExpandedPane(paySecurityDepositTitledPane);
            }
        }

        messageFacade.addArbitrator(arbitrator);
    }

    @FXML
    public void onPaymentDone() {
        //To change body of created methods use File | Settings | File Templates.
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupPaySecurityDepositScreen() {
        infoLabel.setText("You need to pay 2 x the max. trading volume as security deposit.\n\nThat payment will be " +
                "locked into a MultiSig fund and be refunded when you leave the arbitration pool.\nIn case of fraud " +
                "(collusion, not fulfilling the min. dispute quality requirements) you will lose your security " +
                "deposit.\n" +
                "If you have a negative feedback from your clients you will lose a part of the security deposit,\n" +
                "depending on the overall relation of negative to positive ratings you received after a dispute " +
                "resolution.\n\nPlease pay in 2 BTC");


        String securityDepositAddress = walletFacade.getRegistrationAddressEntry() != null ?
                walletFacade.getRegistrationAddressEntry().toString() : "";
        securityDepositAddressTextField.setText(securityDepositAddress);

        AwesomeDude.setIcon(copyIcon, AwesomeIcon.COPY);
        copyIcon.setOnMouseClicked(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putString(securityDepositAddress);
            clipboard.setContent(content);
        });

        paymentDoneButton.setDisable(walletFacade.getArbitratorDepositBalance().isZero());
        log.debug("getArbitratorDepositBalance " + walletFacade.getArbitratorDepositBalance());
        walletFacade.getWallet().addEventListener(new WalletEventListener() {
            @Override
            public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
                paymentDoneButton.setDisable(newBalance.isZero());
            }

            @Override
            public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {

            }

            @Override
            public void onReorganize(Wallet wallet) {

            }

            @Override
            public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {

            }

            @Override
            public void onWalletChanged(Wallet wallet) {

            }

            @Override
            public void onScriptsAdded(Wallet wallet, List<Script> scripts) {

            }

            @Override
            public void onKeysAdded(List<ECKey> keys) {

            }
        });
    }

    private void applyArbitrator() {
        if (arbitrator != null) {
            String name = "";
            switch (arbitrator.getIdType()) {
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
            idTypeTextField.setText(BSResources.get(arbitrator.getIdType().toString()));
            languagesTextField.setText(formatter.languageLocalesToString(arbitrator.getLanguages()));
            arbitrationFeeTextField.setText(String.valueOf(arbitrator.getFee()));
            methodsTextField.setText(formatter.arbitrationMethodsToString(arbitrator.getArbitrationMethods()));
            idVerificationsTextField.setText(
                    formatter.arbitrationIDVerificationsToString(arbitrator.getIdVerifications()));
            webPageTextField.setText(arbitrator.getWebUrl());
            descriptionTextArea.setText(arbitrator.getDescription());

            idType = arbitrator.getIdType();
            languageList = arbitrator.getLanguages();
            methodList = arbitrator.getArbitrationMethods();
            idVerificationList = arbitrator.getIdVerifications();
        }
    }


    private Arbitrator getEditedArbitrator() {
        String pubKeyAsHex = walletFacade.getArbitratorDepositAddressEntry().getPubKeyAsHexString();
        String messagePubKeyAsHex = DSAKeyUtil.getHexStringFromPublicKey(user.getMessagePublicKey());
        String name = nameTextField.getText();
        Coin fee = formatter.parseToCoin(arbitrationFeeTextField.getText());
        String webUrl = webPageTextField.getText();
        String description = descriptionTextArea.getText();

        return new Arbitrator(pubKeyAsHex,
                messagePubKeyAsHex,
                name,
                idType,
                languageList,
                new Reputation(),
                fee,
                methodList,
                idVerificationList,
                webUrl,
                description);
    }

    private void close() {
        Stage stage = (Stage) root.getScene().getWindow();
        stage.close();
    }
}

