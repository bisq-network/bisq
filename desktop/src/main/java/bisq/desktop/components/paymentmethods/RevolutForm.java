/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.components.paymentmethods;

import bisq.desktop.components.InputTextField;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.RevolutValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.RevolutAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.RevolutAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import com.jfoenix.controls.JFXComboBox;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addTopLabelFlowPane;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addTopLabelWithVBox;

public class RevolutForm extends PaymentMethodForm {
    private final RevolutAccount account;
    private RevolutValidator validator;
    private InputTextField accountIdInputTextField;
    private Country selectedCountry;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        String accountId = ((RevolutAccountPayload) paymentAccountPayload).getAccountId();
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, getTitle(accountId), accountId);

        return gridRow;
    }

    private static String getTitle(String accountId) {
        // From 0.9.4 on we only allow phone nr. as with emails we got too many disputes as users used an email which was
        // not registered at Revolut. It seems that phone numbers need to be registered at least we have no reports from
        // arbitrators with such cases. Thought email is still supported for backward compatibility.
        // We might still get emails from users who have registered when email was supported
        return accountId.contains("@") ? Res.get("payment.revolut.email") : Res.get("payment.revolut.phoneNr");
    }

    public RevolutForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService,
                       RevolutValidator revolutValidator, InputValidator inputValidator, GridPane gridPane,
                       int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.account = (RevolutAccount) paymentAccount;
        this.validator = revolutValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        // country selection is added only to prevent anymore email id input and
        // solely to validate the given phone number
        ComboBox<Country> countryComboBox = addCountrySelection();
        setCountryComboBoxAction(countryComboBox);
        countryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllRevolutCountries()));

        accountIdInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.revolut.phoneNr"));
        accountIdInputTextField.setValidator(validator);
        accountIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setAccountId(newValue.trim());
            updateFromInputs();
        });

        addCurrenciesGrid(true);
        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();

        //set default country as selected
        selectedCountry = CountryUtil.getDefaultCountry();
        if (CountryUtil.getAllRevolutCountries().contains(selectedCountry)) {
            countryComboBox.getSelectionModel().select(selectedCountry);
        }
    }

    ComboBox<Country> addCountrySelection() {
        HBox hBox = new HBox();

        hBox.setSpacing(5);
        ComboBox<Country> countryComboBox = new JFXComboBox<>();
        hBox.getChildren().add(countryComboBox);

        addTopLabelWithVBox(gridPane, ++gridRow, Res.get("payment.bank.country"), hBox, 0);

        countryComboBox.setPromptText(Res.get("payment.select.bank.country"));
        countryComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(Country country) {
                return country.name + " (" + country.code + ")";
            }

            @Override
            public Country fromString(String s) {
                return null;
            }
        });
        return countryComboBox;
    }

    void setCountryComboBoxAction(ComboBox<Country> countryComboBox) {
        countryComboBox.setOnAction(e -> {
            selectedCountry = countryComboBox.getSelectionModel().getSelectedItem();
            updateFromInputs();
            accountIdInputTextField.resetValidation();
            accountIdInputTextField.validate();
            accountIdInputTextField.requestFocus();
            countryComboBox.requestFocus();
        });
    }

    private void addCurrenciesGrid(boolean isEditable) {
        FlowPane flowPane = addTopLabelFlowPane(gridPane, ++gridRow,
                Res.get("payment.supportedCurrencies"), Layout.FLOATING_LABEL_DISTANCE,
                Layout.FLOATING_LABEL_DISTANCE).second;

        if (isEditable)
            flowPane.setId("flow-pane-checkboxes-bg");
        else
            flowPane.setId("flow-pane-checkboxes-non-editable-bg");

        CurrencyUtil.getAllRevolutCurrencies().forEach(e ->
                fillUpFlowPaneWithCurrencies(isEditable, flowPane, e, account));
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(accountIdInputTextField.getText());
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                account.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(account.getPaymentMethod().getId()));
        String accountId = account.getAccountId();
        TextField field = addCompactTopLabelTextField(gridPane, ++gridRow, getTitle(accountId), accountId).second;
        field.setMouseTransparent(false);
        addLimitations(true);
        addCurrenciesGrid(false);
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && validator.validate(account.getAccountId(), selectedCountry.code).isValid
                && account.getTradeCurrencies().size() > 0);
    }
}
