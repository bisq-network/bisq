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

package io.bitsquare.gui.main.account.content.irc;

import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.account.MultiStepNavigation;
import io.bitsquare.gui.main.account.content.ContextAware;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.util.Utilities;

import java.net.URL;

import java.util.Currency;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Callback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
Just temporary for giving the user a possibility to test the app via simulating the bank transfer in a IRC chat.
 */
public class IrcAccountViewCB extends CachedViewCB<IrcAccountPm> implements ContextAware {

    private static final Logger log = LoggerFactory.getLogger(IrcAccountViewCB.class);

    @FXML HBox buttonsHBox;
    @FXML InputTextField ircNickNameTextField;
    @FXML Button saveButton;
    @FXML ComboBox<BankAccountType> typesComboBox;
    @FXML ComboBox<Currency> currencyComboBox;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    IrcAccountViewCB(IrcAccountPm presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        ircNickNameTextField.setValidator(presentationModel.getNickNameValidator());

        typesComboBox.setItems(presentationModel.getAllTypes());
        typesComboBox.setConverter(presentationModel.getTypesConverter());
        // we use a custom cell for deactivating non IRC items, later we use the standard cell and the StringConverter
        typesComboBox.setCellFactory(new Callback<ListView<BankAccountType>, ListCell<BankAccountType>>() {
            @Override
            public ListCell<BankAccountType> call(ListView<BankAccountType> p) {
                return new ListCell<BankAccountType>() {

                    @Override
                    protected void updateItem(BankAccountType item, boolean empty) {
                        super.updateItem(item, empty);

                        setText(presentationModel.getBankAccountType(item));

                        if (item == null || empty) {
                            setGraphic(null);
                        }
                        else if (item != BankAccountType.IRC) {
                            setOpacity(0.3);
                            setDisable(true);
                        }
                    }
                };
            }
        });
        typesComboBox.getSelectionModel().select(0);

        currencyComboBox.setItems(presentationModel.getAllCurrencies());
        currencyComboBox.setConverter(presentationModel.getCurrencyConverter());
        // we use a custom cell for deactivating non EUR items, later we use the standard cell and the StringConverter
        currencyComboBox.setCellFactory(new Callback<ListView<Currency>, ListCell<Currency>>() {
            @Override
            public ListCell<Currency> call(ListView<Currency> p) {
                return new ListCell<Currency>() {

                    @Override
                    protected void updateItem(Currency currency, boolean empty) {
                        super.updateItem(currency, empty);

                        if (currency == null || empty) {
                            setGraphic(null);
                        }
                        else {
                            setText(currency.getCurrencyCode() + " (" + currency.getDisplayName() + ")");

                            if (!currency.getCurrencyCode().equals("EUR")) {
                                setOpacity(0.3);
                                setDisable(true);
                            }
                        }
                    }
                };
            }
        });
        currencyComboBox.getSelectionModel().select(0);

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        setupListeners();
        setupBindings();

        Platform.runLater(() -> Popups.openInfoPopup("Demo setup for simulating the banking transfer",
                "For demo purposes we use a special setup so that users can simulate the banking transfer when " +
                        "meeting in an IRC chat room.\n" +
                        "You need to define your IRC nickname and later in the trade process you can find your " +
                        "trading partner with his IRC nickname in the chat room and simulate the bank transfer " +
                        "activities, which are:\n\n" +
                        "1. Bitcoin buyer indicates that he has started the bank transfer.\n\n" +
                        "2. Bitcoin seller confirms that he has received the national currency from the " +
                        "bank transfer."));
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
    // ContextAware implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void useSettingsContext(boolean useSettingsContext) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////


    @FXML
    void onSelectType() {
        presentationModel.setType(typesComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    void onSelectCurrency() {
        presentationModel.setCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    void onSave() {
        InputValidator.ValidationResult result = presentationModel.requestSaveBankAccount();
        if (result.isValid && parent instanceof MultiStepNavigation)
            ((MultiStepNavigation) parent).nextStep(this);
    }

    @FXML
    void onOpenSetupHelp() {
        Help.openWindow(HelpId.SETUP_FIAT_ACCOUNT);
    }

    @FXML
    void onOpenIRC() {
        try {
            Utilities.openURL("https://webchat.freenode.net/?channels=bitsquare-trading");
        } catch (Exception e) {
            log.error("Cannot open browser. " + e.getMessage());
        }
    }
    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void setupListeners() {
        presentationModel.type.addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                typesComboBox.getSelectionModel().select(typesComboBox.getItems().indexOf(newValue));
            else
                typesComboBox.getSelectionModel().clearSelection();
        });

        presentationModel.currency.addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                currencyComboBox.getSelectionModel().select(currencyComboBox.getItems().indexOf(newValue));
            else
                currencyComboBox.getSelectionModel().clearSelection();
        });
    }

    private void setupBindings() {
        // input
        ircNickNameTextField.textProperty().bindBidirectional(presentationModel.ircNickName);
        saveButton.disableProperty().bind(presentationModel.saveButtonDisable);
    }


}

