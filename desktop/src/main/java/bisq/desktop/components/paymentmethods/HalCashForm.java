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
import bisq.desktop.util.validation.HalCashValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.HalCashAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.HalCashAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import com.jfoenix.controls.JFXComboBox;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addTopLabelWithVBox;

public class HalCashForm extends PaymentMethodForm {
    private final HalCashAccount halCashAccount;
    private final HalCashValidator halCashValidator;
    private InputTextField mobileNrInputTextField;

    public HalCashForm(PaymentAccount paymentAccount,
                       AccountAgeWitnessService accountAgeWitnessService,
                       HalCashValidator halCashValidator,
                       InputValidator inputValidator,
                       GridPane gridPane,
                       int gridRow,
                       BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.halCashAccount = (HalCashAccount) paymentAccount;
        this.halCashValidator = halCashValidator;
    }

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        HalCashAccountPayload halCashAccountPayload = (HalCashAccountPayload) paymentAccountPayload;
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1, Res.get("payment.bank.country"), CountryUtil.getNameAndCode(halCashAccountPayload.getCountryCode()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.mobile"), ((HalCashAccountPayload) paymentAccountPayload).getMobileNr());
        return gridRow;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        ComboBox<Country> countryComboBox = addCountrySelection();
        setCountryComboBoxAction(countryComboBox, halCashAccount);
        countryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllHalCashCountries()));
        Country country = CountryUtil.getDefaultCountry();
        if (CountryUtil.getAllHalCashCountries().contains(country)) {
            countryComboBox.getSelectionModel().select(country);
            halCashAccount.setCountry(country);
            updateFromInputs();
        }

        mobileNrInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.mobile"));
        mobileNrInputTextField.setValidator(halCashValidator);
        mobileNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            halCashAccount.setMobileNr(newValue);
            updateFromInputs();
        });

        TradeCurrency singleTradeCurrency = halCashAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);
        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
        updateFromInputs();
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(mobileNrInputTextField.getText());
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), halCashAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"), Res.get(halCashAccount.getPaymentMethod().getId()));
        TextField field = addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.mobile"), halCashAccount.getMobileNr()).second;
        field.setMouseTransparent(false);
        TradeCurrency singleTradeCurrency = halCashAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);
        addLimitations(true);
    }

    @Override
    public void updateAllInputsValid() {
        if (halCashValidator.validate(halCashAccount.getMobileNr()).isValid) {
            halCashAccount.setMobileNr(halCashValidator.getNormalizedPhoneNumber());
        }
        allInputsValid.set(isAccountNameValid() && halCashValidator.validate(halCashAccount.getMobileNr()).isValid && halCashAccount.getTradeCurrencies().size() > 0);
    }

    private void setCountryComboBoxAction(ComboBox<Country> countryComboBox,
                                          CountryBasedPaymentAccount paymentAccount) {
        countryComboBox.setOnAction(e -> {
            Country selectedItem = countryComboBox.getSelectionModel().getSelectedItem();
            paymentAccount.setCountry(selectedItem);
            halCashValidator.setIsoCountryCode(selectedItem.code);
            updateFromInputs();
        });
    }

    private ComboBox<Country> addCountrySelection() {
        HBox hBox = new HBox();
        hBox.setSpacing(10);
        ComboBox<Country> countryComboBox = new JFXComboBox<>();
        hBox.getChildren().addAll(countryComboBox);
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
}
