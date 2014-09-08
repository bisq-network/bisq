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

package io.bitsquare.gui.account.fiataccount;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.CachedCodeBehind;
import io.bitsquare.gui.account.setup.SetupCB;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.help.Help;
import io.bitsquare.gui.help.HelpId;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Region;

import java.net.URL;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javafx.beans.binding.Bindings.createBooleanBinding;

public class FiatAccountCB extends CachedCodeBehind<FiatAccountPm> {

    private static final Logger log = LoggerFactory.getLogger(FiatAccountCB.class);

    @FXML private ComboBox<Region> regionComboBox;
    @FXML private ComboBox<Country> countryComboBox;
    @FXML private InputTextField titleTextField, holderNameTextField, primaryIDTextField, secondaryIDTextField;
    @FXML private Button saveButton, doneButton, removeBankAccountButton;
    @FXML private ComboBox<BankAccount> selectionComboBox;
    @FXML private ComboBox<BankAccountType> typesComboBox;
    @FXML private ComboBox<Currency> currencyComboBox;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FiatAccountCB(FiatAccountPm presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        typesComboBox.setItems(presentationModel.getAllTypes());
        typesComboBox.setConverter(presentationModel.getTypesConverter());
        selectionComboBox.setConverter(presentationModel.getSelectionConverter());
        currencyComboBox.setItems(presentationModel.getAllCurrencies());
        currencyComboBox.setConverter(presentationModel.getCurrencyConverter());
        regionComboBox.setItems(presentationModel.getAllRegions());
        regionComboBox.setConverter(presentationModel.getRegionConverter());
        countryComboBox.setConverter(presentationModel.getCountryConverter());

        titleTextField.setValidator(presentationModel.getValidator());
        holderNameTextField.setValidator(presentationModel.getValidator());
        primaryIDTextField.setValidator(presentationModel.getValidator());
        secondaryIDTextField.setValidator(presentationModel.getValidator());
    }

    @Override
    public void activate() {
        super.activate();

        setupListeners();
        setupBindings();

        selectionComboBox.setItems(presentationModel.getAllBankAccounts());
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    public void onSelectAccount() {
        if (selectionComboBox.getSelectionModel().getSelectedItem() != null)
            presentationModel.selectBankAccount(selectionComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    public void onSelectType() {
        presentationModel.setType(typesComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    public void onSelectCurrency() {
        presentationModel.setCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onSelectRegion() {
        countryComboBox.setVisible(true);
        Region region = regionComboBox.getSelectionModel().getSelectedItem();
        countryComboBox.setItems(presentationModel.getAllCountriesFor(region));
    }

    @FXML
    private void onSelectCountry() {
        presentationModel.setCountry(countryComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    private void onSave() {
        InputValidator.ValidationResult result = presentationModel.saveBankAccount();
        if (result.isValid) {
            selectionComboBox.getSelectionModel().select(null);
            Popups.openInfo("You can add more accounts or continue to the next step.",
                    "Your payments account has been saved.");
        }
    }

    @FXML
    private void onDone() {
        if (parentController != null)
            ((SetupCB) parentController).onCompleted(this);
    }

    @FXML
    private void onRemoveAccount() {
        presentationModel.removeBankAccount();
        selectionComboBox.getSelectionModel().select(null);
    }

    @FXML
    private void onOpenSetupHelp() {
        Help.openWindow(HelpId.SETUP_FIAT_ACCOUNT);
    }

    @FXML
    private void onOpenManageAccountsHelp() {
        Help.openWindow(HelpId.MANAGE_FIAT_ACCOUNT);
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

        presentationModel.country.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                regionComboBox.getSelectionModel().select(regionComboBox.getItems().indexOf(newValue.getRegion()));
                countryComboBox.getSelectionModel().select(countryComboBox.getItems().indexOf(newValue));
            }
            else {
                regionComboBox.getSelectionModel().clearSelection();
                countryComboBox.getSelectionModel().clearSelection();
            }
        });

        presentationModel.getCountryNotInAcceptedCountriesList().addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                List<Action> actions = new ArrayList<>();
                actions.add(Dialog.Actions.YES);
                actions.add(Dialog.Actions.NO);

                Action response = Popups.openConfirmPopup("Warning",
                        "The country of your bank account is not included in the accepted countries in the " +
                                "general settings.\n\nDo you want to add it automatically?",
                        null,
                        actions);

                if (response == Dialog.Actions.YES)
                    presentationModel.addCountryToAcceptedCountriesList();
            }
        });

        presentationModel.getAllBankAccounts().addListener((ListChangeListener<BankAccount>) change ->
                doneButton.setDisable(change.getList().isEmpty()));
    }

    private void setupBindings() {
        // input
        titleTextField.textProperty().bindBidirectional(presentationModel.title);
        holderNameTextField.textProperty().bindBidirectional(presentationModel.holderName);
        primaryIDTextField.textProperty().bindBidirectional(presentationModel.primaryID);
        secondaryIDTextField.textProperty().bindBidirectional(presentationModel.secondaryID);

        primaryIDTextField.promptTextProperty().bind(presentationModel.primaryIDPrompt);
        secondaryIDTextField.promptTextProperty().bind(presentationModel.secondaryIDPrompt);
        selectionComboBox.promptTextProperty().bind(presentationModel.selectionPrompt);
        selectionComboBox.disableProperty().bind(presentationModel.selectionDisable);

        saveButton.disableProperty().bind(presentationModel.saveButtonDisable);

        removeBankAccountButton.disableProperty().bind(createBooleanBinding(() ->
                        (selectionComboBox.getSelectionModel().selectedIndexProperty().get() == -1),
                selectionComboBox.getSelectionModel().selectedIndexProperty()));

    }
}

