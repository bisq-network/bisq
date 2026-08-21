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

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.MercadoPagoAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.MercadoPagoAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.UserThread;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import static bisq.desktop.util.FormBuilder.*;

public class MercadoPagoForm extends PaymentMethodForm {
    private final MercadoPagoAccount mercadoPagoAccount;
    ComboBox<Country> countryCombo;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        MercadoPagoAccountPayload mercadoPagoAccountPayload = (MercadoPagoAccountPayload) paymentAccountPayload;

        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner.fullname"),
                mercadoPagoAccountPayload.getAccountHolderName());
        addCompactTopLabelTextField(gridPane, gridRow, 1, Res.get("shared.country"),
                CountryUtil.getNameAndCode(mercadoPagoAccountPayload.getCountryCode()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.mercadoPago.holderId"),
                mercadoPagoAccountPayload.getAccountHolderId());
        addCompactTopLabelTextField(gridPane, gridRow, 1, Res.get("payment.mercadoPago.site"),
                MercadoPagoAccount.countryToMercadoPagoSite(mercadoPagoAccountPayload.getCountryCode()));
        return gridRow;
    }

    public MercadoPagoForm(PaymentAccount paymentAccount,
                              AccountAgeWitnessService accountAgeWitnessService,
                              InputValidator inputValidator,
                              GridPane gridPane,
                              int gridRow,
                              CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.mercadoPagoAccount = (MercadoPagoAccount) paymentAccount;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow,
                Res.get("payment.account.owner.fullname"));
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            mercadoPagoAccount.setAccountHolderName(newValue);
            updateFromInputs();
        });

        InputTextField mobileNrInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.mercadoPago.holderId"));
        mobileNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            mercadoPagoAccount.setAccountHolderId(newValue);
            updateFromInputs();
        });

        countryCombo = addComboBox(gridPane, ++gridRow, Res.get("shared.country"));
        countryCombo.setPromptText(Res.get("payment.select.country"));
        countryCombo.setItems(FXCollections.observableArrayList(MercadoPagoAccount.getAllMercadoPagoCountries()));
        TextField ccyField = addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), "").second;
        countryCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Country country) {
                return country.name + " (" + country.code + ")";
            }
            @Override
            public Country fromString(String s) {
                return null;
            }
        });
        countryCombo.setOnAction(e -> {
            Country countryCode = countryCombo.getValue();
            mercadoPagoAccount.setCountry(countryCode);
            TradeCurrency currency = CurrencyUtil.getCurrencyByCountryCode(countryCode.code);
            paymentAccount.setSingleTradeCurrency(currency);
            ccyField.setText(currency.getNameAndCode());
            updateFromInputs();
        });
        if (countryCombo.getItems().size() == 1) {  // auto select when only one choice
            UserThread.runAfter(() -> countryCombo.setValue(countryCombo.getItems().get(0)), 1);
        }

        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(mercadoPagoAccount.getAccountHolderId());
    }

    @Override
    public void addFormForEditAccount() {
        gridRowFrom = gridRow;
        addAccountNameTextFieldWithAutoFillToggleButton();
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(mercadoPagoAccount.getPaymentMethod().getId()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner.fullname"),
                mercadoPagoAccount.getAccountHolderName());
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.mercadoPago.holderId"),
                mercadoPagoAccount.getAccountHolderId());
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.country"),
                mercadoPagoAccount.getCountry() != null ? mercadoPagoAccount.getCountry().name : "");
        TradeCurrency singleTradeCurrency = mercadoPagoAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);
        addLimitations(true);
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && inputValidator.validate(mercadoPagoAccount.getAccountHolderId()).isValid
                && inputValidator.validate(mercadoPagoAccount.getAccountHolderName()).isValid
                && mercadoPagoAccount.getTradeCurrencies().size() > 0);
    }
}
