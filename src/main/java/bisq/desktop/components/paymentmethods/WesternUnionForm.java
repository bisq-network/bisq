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
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.EmailValidator;

import bisq.core.locale.BankUtil;
import bisq.core.locale.Country;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.WesternUnionAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.util.Tuple2;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addLabelTextFieldWithCopyIcon;

@Slf4j
public class WesternUnionForm extends PaymentMethodForm {
    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        final WesternUnionAccountPayload payload = (WesternUnionAccountPayload) paymentAccountPayload;
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.account.fullName"),
                payload.getHolderName());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.account.city"),
                payload.getCity());
        if (BankUtil.isStateRequired(payload.getCountryCode()))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.account.state"),
                    payload.getState());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.email"),
                payload.getEmail());

        return gridRow;
    }

    private final WesternUnionAccountPayload westernUnionAccountPayload;
    private InputTextField holderNameInputTextField, emailInputTextField, cityInputTextField, stateInputTextField;
    private Label stateLabel;
    private final EmailValidator emailValidator;
    private Country selectedCountry;

    public WesternUnionForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator,
                            GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.westernUnionAccountPayload = (WesternUnionAccountPayload) paymentAccount.paymentAccountPayload;

        emailValidator = new EmailValidator();
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        String countryCode = westernUnionAccountPayload.getCountryCode();

        FormBuilder.addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), paymentAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(paymentAccount.getPaymentMethod().getId()));
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.country"),
                getCountryBasedPaymentAccount().getCountry() != null ? getCountryBasedPaymentAccount().getCountry().name : "");
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"),
                nameAndCode);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.fullName"),
                westernUnionAccountPayload.getHolderName());
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.account.city"),
                westernUnionAccountPayload.getCity()).second.setMouseTransparent(false);
        if (BankUtil.isStateRequired(westernUnionAccountPayload.getCountryCode()))
            FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.account.state"),
                    westernUnionAccountPayload.getState()).second.setMouseTransparent(false);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                westernUnionAccountPayload.getEmail());
        addLimitations();
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

    private void onCountrySelected(Country country) {
        selectedCountry = country;
        if (country != null) {
            getCountryBasedPaymentAccount().setCountry(country);
            String countryCode = country.code;
            TradeCurrency currency = CurrencyUtil.getCurrencyByCountryCode(countryCode);
            paymentAccount.setSingleTradeCurrency(currency);
            currencyComboBox.setDisable(false);
            currencyComboBox.getSelectionModel().select(currency);
            updateFromInputs();
            applyIsStateRequired();
            cityInputTextField.setText("");
            stateInputTextField.setText("");
        }
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        Tuple2<ComboBox<TradeCurrency>, Integer> tuple = GUIUtil.addRegionCountryTradeCurrencyComboBoxes(gridPane, gridRow, this::onCountrySelected, this::onTradeCurrencySelected);
        currencyComboBox = tuple.first;
        gridRow = tuple.second;

        holderNameInputTextField = FormBuilder.addLabelInputTextField(gridPane,
                ++gridRow, Res.getWithCol("payment.account.fullName")).second;
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            westernUnionAccountPayload.setHolderName(newValue);
            updateFromInputs();
        });
        holderNameInputTextField.setValidator(inputValidator);

        cityInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.account.city")).second;
        cityInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            westernUnionAccountPayload.setCity(newValue);
            updateFromInputs();

        });

        final Tuple2<Label, InputTextField> tuple2 = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.account.state"));
        stateLabel = tuple2.first;
        stateInputTextField = tuple2.second;
        stateInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            westernUnionAccountPayload.setState(newValue);
            updateFromInputs();

        });
        applyIsStateRequired();

        emailInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.email")).second;
        emailInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            westernUnionAccountPayload.setEmail(newValue);
            updateFromInputs();
        });
        emailInputTextField.setValidator(emailValidator);

        addLimitations();
        addAccountNameTextFieldWithAutoFillCheckBox();

        updateFromInputs();
    }

    private void applyIsStateRequired() {
        final boolean stateRequired = BankUtil.isStateRequired(westernUnionAccountPayload.getCountryCode());
        stateLabel.setManaged(stateRequired);
        stateLabel.setVisible(stateRequired);
        stateInputTextField.setManaged(stateRequired);
        stateInputTextField.setVisible(stateRequired);
    }

    private CountryBasedPaymentAccount getCountryBasedPaymentAccount() {
        return (CountryBasedPaymentAccount) this.paymentAccount;
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            accountNameTextField.setText(Res.get(paymentAccount.getPaymentMethod().getId())
                    .concat(": ")
                    .concat(StringUtils.abbreviate(holderNameInputTextField.getText(), 9)));
        }
    }

    @Override
    public void updateAllInputsValid() {
        boolean result = isAccountNameValid()
                && paymentAccount.getSingleTradeCurrency() != null
                && getCountryBasedPaymentAccount().getCountry() != null
                && inputValidator.validate(westernUnionAccountPayload.getHolderName()).isValid
                && inputValidator.validate(westernUnionAccountPayload.getCity()).isValid
                && emailValidator.validate(westernUnionAccountPayload.getEmail()).isValid;
        allInputsValid.set(result);
    }
}
