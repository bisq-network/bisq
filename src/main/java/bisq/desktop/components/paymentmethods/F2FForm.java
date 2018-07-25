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
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.F2FValidator;

import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.F2FAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.F2FAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.util.Tuple2;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;

import static bisq.desktop.util.FormBuilder.addLabelInputTextField;
import static bisq.desktop.util.FormBuilder.addLabelTextArea;
import static bisq.desktop.util.FormBuilder.addLabelTextField;
import static bisq.desktop.util.FormBuilder.addLabelTextFieldWithCopyIcon;

public class F2FForm extends PaymentMethodForm {
    private final F2FAccount f2fAccount;
    private final F2FValidator f2fValidator;
    private InputTextField cityInputTextField;
    private Country selectedCountry;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload, Offer offer) {
        F2FAccountPayload f2fAccountPayload = (F2FAccountPayload) paymentAccountPayload;
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("shared.country"),
                CountryUtil.getNameAndCode(f2fAccountPayload.getCountryCode()));
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.f2f.contact"),
                f2fAccountPayload.getContact());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.f2f.city"),
                offer.getF2FCity());
        TextArea textArea = addLabelTextArea(gridPane, ++gridRow, Res.getWithCol("payment.f2f.extra"), "").second;
        textArea.setPrefHeight(60);
        textArea.setEditable(false);
        textArea.setId("text-area-disabled");
        textArea.setText(offer.getF2FExtraInfo());
        return gridRow;
    }

    public F2FForm(PaymentAccount paymentAccount,
                   AccountAgeWitnessService accountAgeWitnessService, F2FValidator f2fValidator,
                   InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);

        this.f2fAccount = (F2FAccount) paymentAccount;
        this.f2fValidator = f2fValidator;
    }


    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        Tuple2<ComboBox<TradeCurrency>, Integer> tuple = GUIUtil.addRegionCountryTradeCurrencyComboBoxes(gridPane, gridRow, this::onCountrySelected, this::onTradeCurrencySelected);
        currencyComboBox = tuple.first;
        gridRow = tuple.second;

        InputTextField contactInputTextField = addLabelInputTextField(gridPane, ++gridRow,
                Res.getWithCol("payment.f2f.contact")).second;
        contactInputTextField.setPromptText(Res.get("payment.f2f.contact.prompt"));
        contactInputTextField.setValidator(f2fValidator);
        contactInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            f2fAccount.setContact(newValue);
            updateFromInputs();
        });

        cityInputTextField = addLabelInputTextField(gridPane, ++gridRow,
                Res.getWithCol("payment.f2f.city")).second;
        cityInputTextField.setPromptText(Res.get("payment.f2f.city.prompt"));
        cityInputTextField.setValidator(f2fValidator);
        cityInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            f2fAccount.setCity(newValue);
            updateFromInputs();
        });

        TextArea extraTextArea = addLabelTextArea(gridPane, ++gridRow,
                Res.getWithCol("payment.f2f.optionalExtra"), "").second;
        extraTextArea.setPromptText(Res.get("payment.f2f.extra.prompt"));
        extraTextArea.setPrefHeight(60);
        //extraTextArea.setValidator(f2fValidator);
        extraTextArea.textProperty().addListener((ov, oldValue, newValue) -> {
            f2fAccount.setExtraInfo(newValue);
            updateFromInputs();
        });

        addLimitations();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    private void onCountrySelected(Country country) {
        selectedCountry = country;
        if (selectedCountry != null) {
            getCountryBasedPaymentAccount().setCountry(selectedCountry);
            String countryCode = selectedCountry.code;
            TradeCurrency currency = CurrencyUtil.getCurrencyByCountryCode(countryCode);
            paymentAccount.setSingleTradeCurrency(currency);
            currencyComboBox.setDisable(false);
            currencyComboBox.getSelectionModel().select(currency);

            updateFromInputs();
        }
    }

    private void onTradeCurrencySelected(TradeCurrency tradeCurrency) {
        FiatCurrency defaultCurrency = CurrencyUtil.getCurrencyByCountryCode(selectedCountry.code);
        if (!defaultCurrency.equals(tradeCurrency)) {
            new Popup<>().warning(Res.get("payment.foreign.currency"))
                    .actionButtonText(Res.get("shared.yes"))
                    .onAction(() -> {
                        paymentAccount.setSingleTradeCurrency(tradeCurrency);
                        autoFillNameTextField();
                    })
                    .closeButtonText(Res.get("payment.restore.default"))
                    .onClose(() -> currencyComboBox.getSelectionModel().select(defaultCurrency))
                    .show();
        } else {
            paymentAccount.setSingleTradeCurrency(tradeCurrency);
            autoFillNameTextField();
        }
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String city = cityInputTextField.getText();
            city = StringUtils.abbreviate(city, 9);
            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(city));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;

        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                paymentAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(paymentAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.country"),
                getCountryBasedPaymentAccount().getCountry() != null ? getCountryBasedPaymentAccount().getCountry().name : "");
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.f2f.contact", f2fAccount.getContact()),
                f2fAccount.getContact());
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.f2f.city", f2fAccount.getCity()),
                f2fAccount.getCity());
        TextArea textArea = addLabelTextArea(gridPane, ++gridRow, Res.get("payment.f2f.extra"), "").second;
        textArea.setText(f2fAccount.getExtraInfo());
        textArea.setPrefHeight(60);
        textArea.setEditable(false);

        addLimitations();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && f2fValidator.validate(f2fAccount.getContact()).isValid
                && f2fValidator.validate(f2fAccount.getCity()).isValid
                && f2fAccount.getTradeCurrencies().size() > 0);
    }

    private CountryBasedPaymentAccount getCountryBasedPaymentAccount() {
        return (CountryBasedPaymentAccount) this.paymentAccount;
    }
}
