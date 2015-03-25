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

import io.bitsquare.common.viewfx.view.ActivatableViewAndModel;
import io.bitsquare.common.viewfx.view.FxmlView;
import io.bitsquare.common.viewfx.view.Wizard;
import io.bitsquare.fiat.FiatAccountType;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.util.Utilities;

import javax.inject.Inject;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Callback;

/**
 * Just temporary for giving the user a possibility to test the app via simulating the bank transfer in a IRC chat.
 */
@FxmlView
public class IrcAccountView extends ActivatableViewAndModel<GridPane, IrcAccountViewModel> implements Wizard.Step {

    @FXML HBox buttonsHBox;
    @FXML InputTextField ircNickNameTextField;
    @FXML Button saveButton;
    @FXML ComboBox<FiatAccountType> typesComboBox;
    @FXML ComboBox<String> currencyComboBox;

    private Wizard wizard;

    @Inject
    public IrcAccountView(IrcAccountViewModel model) {
        super(model);
    }

    @Override
    public void doActivate() {
        ircNickNameTextField.setValidator(model.getNickNameValidator());

        typesComboBox.setItems(model.getAllTypes());
        typesComboBox.setConverter(model.getTypesConverter());
        // we use a custom cell for deactivating non IRC items, later we use the standard cell and the StringConverter
        typesComboBox.setCellFactory(new Callback<ListView<FiatAccountType>, ListCell<FiatAccountType>>() {
            @Override
            public ListCell<FiatAccountType> call(ListView<FiatAccountType> p) {
                return new ListCell<FiatAccountType>() {

                    @Override
                    protected void updateItem(FiatAccountType item, boolean empty) {
                        super.updateItem(item, empty);

                        setText(model.getBankAccountType(item));

                        if (item == null || empty) {
                            setGraphic(null);
                        }
                        else if (item != FiatAccountType.IRC) {
                            setOpacity(0.3);
                            setDisable(true);
                        }
                    }
                };
            }
        });
        typesComboBox.getSelectionModel().select(0);

        currencyComboBox.setItems(model.getAllCurrencyCodes());
        currencyComboBox.setConverter(model.getCurrencyConverter());
        // we use a custom cell for deactivating non EUR items, later we use the standard cell and the StringConverter
        currencyComboBox.setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
            @Override
            public ListCell<String> call(ListView<String> p) {
                return new ListCell<String>() {

                    @Override
                    protected void updateItem(String currencyCode, boolean empty) {
                        super.updateItem(currencyCode, empty);

                        if (currencyCode == null || empty) {
                            setGraphic(null);
                        }
                        else {
                            setText(currencyCode + " (" + CurrencyUtil.getDisplayName(currencyCode) + ")");

                            if (!currencyCode.equals("EUR")) {
                                setOpacity(0.3);
                                setDisable(true);
                            }
                        }
                    }
                };
            }
        });
        currencyComboBox.getSelectionModel().select(0);

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

    @Override
    public void setWizard(Wizard wizard) {
        this.wizard = wizard;
    }

    @Override
    public void hideWizardNavigation() {
    }

    @FXML
    void onSelectType() {
        model.setType(typesComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    void onSelectCurrency() {
        model.setCurrencyCode(currencyComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    void onSave() {
        boolean isValid = model.requestSaveBankAccount().isValid;
        if (wizard != null && isValid)
            wizard.nextStep(this);
    }

    @FXML
    void onOpenSetupHelp() {
        Help.openWindow(HelpId.SETUP_FIAT_ACCOUNT);
    }

    @FXML
    void onOpenIRC() {
        try {
            Utilities.openWebPage("https://webchat.freenode.net/?channels=bitsquare-trading");
        } catch (Exception e) {
            log.error(e.getMessage());
            Popups.openWarningPopup("Warning", "Opening browser failed. Please check your internet " +
                    "connection.");
        }
    }

    private void setupListeners() {
        model.type.addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                typesComboBox.getSelectionModel().select(typesComboBox.getItems().indexOf(newValue));
            else
                typesComboBox.getSelectionModel().clearSelection();
        });

        model.currencyCode.addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                currencyComboBox.getSelectionModel().select(currencyComboBox.getItems().indexOf(newValue));
            else
                currencyComboBox.getSelectionModel().clearSelection();
        });
    }

    private void setupBindings() {
        // input
        ircNickNameTextField.textProperty().bindBidirectional(model.ircNickName);
        saveButton.disableProperty().bind(model.saveButtonDisable);
    }
}

