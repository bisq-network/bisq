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
import io.bitsquare.gui.ActivatableViewAndModel;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.gui.Wizard;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.Popups;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.Country;
import io.bitsquare.locale.Region;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import javax.inject.Inject;

import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;

import static javafx.beans.binding.Bindings.createBooleanBinding;

public class FiatAccountView extends ActivatableViewAndModel<GridPane, FiatAccountViewModel> implements Wizard.Step {

    @FXML HBox buttonsHBox;
    @FXML ComboBox<Region> regionComboBox;
    @FXML ComboBox<Country> countryComboBox;
    @FXML InputTextField nameOfBankTextField, holderNameTextField, primaryIDTextField, secondaryIDTextField;
    @FXML Button saveButton, completedButton, removeBankAccountButton;
    @FXML ComboBox<BankAccount> selectionComboBox;
    @FXML ComboBox<BankAccountType> typesComboBox;
    @FXML ComboBox<Currency> currencyComboBox;

    private Wizard parent;

    private final OverlayManager overlayManager;

    @Inject
    public FiatAccountView(FiatAccountViewModel model, OverlayManager overlayManager) {
        super(model);
        this.overlayManager = overlayManager;
    }

    @Override
    public void initialize() {
        typesComboBox.setItems(model.getAllTypes());
        typesComboBox.setConverter(model.getTypesConverter());
        selectionComboBox.setConverter(model.getSelectionConverter());
        currencyComboBox.setItems(model.getAllCurrencies());
        currencyComboBox.setConverter(model.getCurrencyConverter());
        regionComboBox.setItems(model.getAllRegions());
        regionComboBox.setConverter(model.getRegionConverter());
        countryComboBox.setConverter(model.getCountryConverter());

        nameOfBankTextField.setValidator(model.getBankAccountNumberValidator());
        holderNameTextField.setValidator(model.getBankAccountNumberValidator());
        primaryIDTextField.setValidator(model.getBankAccountNumberValidator());
        secondaryIDTextField.setValidator(model.getBankAccountNumberValidator());
    }

    @Override
    public void doActivate() {
        setupListeners();
        setupBindings();

        selectionComboBox.setItems(model.getAllBankAccounts());
    }

    @Override
    public void setParent(Wizard parent) {
        this.parent = parent;
    }

    @Override
    public void hideWizardNavigation() {
        buttonsHBox.getChildren().remove(completedButton);
    }

    @FXML
    void onSelectAccount() {
        if (selectionComboBox.getSelectionModel().getSelectedItem() != null)
            model.selectBankAccount(selectionComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    void onSelectType() {
        model.setType(typesComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    void onSelectCurrency() {
        model.setCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
    }

    @FXML
    void onSelectRegion() {
        countryComboBox.setVisible(true);
        Region region = regionComboBox.getSelectionModel().getSelectedItem();
        if (region != null)
            countryComboBox.setItems(model.getAllCountriesFor(region));
    }

    @FXML
    void onSelectCountry() {
        Country country = countryComboBox.getSelectionModel().getSelectedItem();
        if (country != null)
            model.setCountry(country);
    }

    @FXML
    void onSave() {
        InputValidator.ValidationResult result = model.requestSaveBankAccount();
        if (result.isValid) {
            selectionComboBox.getSelectionModel().select(null);
            Popups.openInfoPopup("Your payments account has been saved.",
                    "You can add more accounts or continue to the next step.");
        }
    }

    @FXML
    void onCompleted() {
        parent.nextStep(this);
    }

    @FXML
    void onRemoveAccount() {
        model.removeBankAccount();
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


    private void setupListeners() {
        model.type.addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                typesComboBox.getSelectionModel().select(typesComboBox.getItems().indexOf(newValue));
            else
                typesComboBox.getSelectionModel().clearSelection();
        });

        model.currency.addListener((ov, oldValue, newValue) -> {
            if (newValue != null)
                currencyComboBox.getSelectionModel().select(currencyComboBox.getItems().indexOf(newValue));
            else
                currencyComboBox.getSelectionModel().clearSelection();
        });

        model.country.addListener((ov, oldValue, newValue) -> {
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

        model.getCountryNotInAcceptedCountriesList().addListener((ov, oldValue, newValue) -> {
            if (newValue) {
                overlayManager.blurContent();
                List<Action> actions = new ArrayList<>();
                actions.add(new AbstractAction(BSResources.get("shared.no")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        getProperties().put("type", "NO");
                        Dialog.Actions.NO.handle(actionEvent);
                        overlayManager.removeBlurContent();
                    }
                });

                actions.add(new AbstractAction(BSResources.get("shared.yes")) {
                    @Override
                    public void handle(ActionEvent actionEvent) {
                        getProperties().put("type", "YES");
                        Dialog.Actions.YES.handle(actionEvent);
                        overlayManager.removeBlurContent();
                    }
                });

                Action response = Popups.openConfirmPopup("Warning", null,
                        "The country of your payments account is not included in your list of accepted countries" +
                                ".\n\nDo you want to add it automatically?",
                        actions);

                if (Popups.isYes(response))
                    model.addCountryToAcceptedCountriesList();
            }
        });

        model.getAllBankAccounts().addListener((ListChangeListener<BankAccount>) change ->
                completedButton.setDisable(model.getAllBankAccounts().isEmpty()));
        completedButton.setDisable(model.getAllBankAccounts().isEmpty());
    }

    private void setupBindings() {
        // input
        nameOfBankTextField.textProperty().bindBidirectional(model.title);
        holderNameTextField.textProperty().bindBidirectional(model.holderName);
        primaryIDTextField.textProperty().bindBidirectional(model.primaryID);
        secondaryIDTextField.textProperty().bindBidirectional(model.secondaryID);

        primaryIDTextField.promptTextProperty().bind(model.primaryIDPrompt);
        secondaryIDTextField.promptTextProperty().bind(model.secondaryIDPrompt);
        selectionComboBox.promptTextProperty().bind(model.selectionPrompt);
        selectionComboBox.disableProperty().bind(model.selectionDisable);

        saveButton.disableProperty().bind(model.saveButtonDisable);

        removeBankAccountButton.disableProperty().bind(createBooleanBinding(() ->
                        (selectionComboBox.getSelectionModel().selectedIndexProperty().get() == -1),
                selectionComboBox.getSelectionModel().selectedIndexProperty()));
    }
}

