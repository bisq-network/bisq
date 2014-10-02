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

package io.bitsquare.gui.main.account.content.fiat;

import io.bitsquare.bank.BankAccount;
import io.bitsquare.bank.BankAccountType;
import io.bitsquare.gui.CachedViewCB;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.account.MultiStepNavigation;
import io.bitsquare.gui.main.account.content.ContextAware;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Region;

import java.net.URL;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javafx.beans.binding.Bindings.createBooleanBinding;

public class FiatAccountViewCB extends CachedViewCB<FiatAccountPm> implements ContextAware {

    private static final Logger log = LoggerFactory.getLogger(FiatAccountViewCB.class);

    @FXML HBox buttonsHBox;
    @FXML ComboBox<Region> regionComboBox;
    @FXML ComboBox<Country> countryComboBox;
    @FXML InputTextField nameOfBankTextField, holderNameTextField, primaryIDTextField, secondaryIDTextField;
    @FXML Button saveButton, completedButton, removeBankAccountButton;
    @FXML ComboBox<BankAccount> selectionComboBox;
    @FXML ComboBox<BankAccountType> typesComboBox;
    @FXML ComboBox<Currency> currencyComboBox;
    private OverlayManager overlayManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    FiatAccountViewCB(FiatAccountPm presentationModel, OverlayManager overlayManager) {
        super(presentationModel);

        this.overlayManager = overlayManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        typesComboBox.setItems(presentationModel.getAllTypes());
        typesComboBox.setConverter(presentationModel.getTypesConverter());
        selectionComboBox.setConverter(presentationModel.getSelectionConverter());
        currencyComboBox.setItems(presentationModel.getAllCurrencies());
        currencyComboBox.setConverter(presentationModel.getCurrencyConverter());
        regionComboBox.setItems(presentationModel.getAllRegions());
        regionComboBox.setConverter(presentationModel.getRegionConverter());
        countryComboBox.setConverter(presentationModel.getCountryConverter());

        nameOfBankTextField.setValidator(presentationModel.getBankAccountNumberValidator());
        holderNameTextField.setValidator(presentationModel.getBankAccountNumberValidator());
        primaryIDTextField.setValidator(presentationModel.getBankAccountNumberValidator());
        secondaryIDTextField.setValidator(presentationModel.getBankAccountNumberValidator());

        super.initialize(url, rb);
    }

    @Override
    public void activate() {
        super.activate();

        setupListeners();
        setupBindings();

        selectionComboBox.setItems(presentationModel.getAllBankAccounts());
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
        if (useSettingsContext)
            buttonsHBox.getChildren().remove(completedButton);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    void onSelectAccount() {
        if (selectionComboBox.getSelectionModel().getSelectedItem() != null)
            presentationModel.selectBankAccount(selectionComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    void onSelectType() {
        presentationModel.setType(typesComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    void onSelectCurrency() {
        presentationModel.setCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    void onSelectRegion() {
        countryComboBox.setVisible(true);
        Region region = regionComboBox.getSelectionModel().getSelectedItem();
        countryComboBox.setItems(presentationModel.getAllCountriesFor(region));
    }

    @FXML
    void onSelectCountry() {
        presentationModel.setCountry(countryComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    void onSave() {
        InputValidator.ValidationResult result = presentationModel.requestSaveBankAccount();
        if (result.isValid) {
            selectionComboBox.getSelectionModel().select(null);
            Popups.openInfo("Your payments account has been saved.",
                    "You can add more accounts or continue to the next step.");
        }
    }

    @FXML
    void onCompleted() {
        if (parent != null)
            ((MultiStepNavigation) parent).nextStep(this);
    }

    @FXML
    void onRemoveAccount() {
        presentationModel.removeBankAccount();
        selectionComboBox.getSelectionModel().select(null);
    }

    @FXML
    void onOpenSetupHelp() {
        Help.openWindow(HelpId.SETUP_FIAT_ACCOUNT);
    }

    @FXML
    void onOpenManageAccountsHelp() {
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
                int regionIndex = regionComboBox.getItems().indexOf(newValue.getRegion());
                if (regionIndex >= 0 && regionIndex < regionComboBox.getItems().size())
                    regionComboBox.getSelectionModel().select(regionComboBox.getItems().indexOf(newValue.getRegion()));

                int countryIndex = countryComboBox.getItems().indexOf(newValue);
                if (countryIndex >= 0 && countryIndex < countryComboBox.getItems().size())
                    countryComboBox.getSelectionModel().select(countryIndex);
            }
            else {
                regionComboBox.getSelectionModel().clearSelection();
                countryComboBox.getSelectionModel().clearSelection();
            }
        });

        presentationModel.getCountryNotInAcceptedCountriesList().addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                overlayManager.blurContent();
                List<Action> actions = new ArrayList<>();
                actions.add(new AbstractAction(BSResources.get("shared.no")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        Dialog.Actions.NO.handle(actionEvent);
                        overlayManager.removeBlurContent();
                    }
                });

                actions.add(new AbstractAction(BSResources.get("shared.yes")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        Dialog.Actions.YES.handle(actionEvent);
                        overlayManager.removeBlurContent();
                    }
                });

                Action response = Popups.openConfirmPopup("Warning", null,
                        "The country of your payments account is not included in your list of accepted countries" +
                                ".\n\nDo you want to add it automatically?",
                        actions);

                if (response == Dialog.Actions.YES)
                    presentationModel.addCountryToAcceptedCountriesList();
            }
        });

        presentationModel.getAllBankAccounts().addListener((ListChangeListener<BankAccount>) change ->
                completedButton.setDisable(presentationModel.getAllBankAccounts().isEmpty()));
        completedButton.setDisable(presentationModel.getAllBankAccounts().isEmpty());
    }

    private void setupBindings() {
        // input
        nameOfBankTextField.textProperty().bindBidirectional(presentationModel.title);
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

