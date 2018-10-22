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

import bisq.desktop.components.AutoTooltipCheckBox;
import bisq.desktop.components.InputTextField;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.EmailValidator;

import bisq.core.locale.BankUtil;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.MoneyGramAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.MoneyGramAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;
import javafx.geometry.VPos;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addLabel;
import static bisq.desktop.util.FormBuilder.addLabelTextFieldWithCopyIcon;

@Slf4j
public class MoneyGramForm extends PaymentMethodForm {

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        final MoneyGramAccountPayload payload = (MoneyGramAccountPayload) paymentAccountPayload;
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.account.fullName"),
                payload.getHolderName());
        FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                Res.get("payment.bank.country"),
                CountryUtil.getNameAndCode(((MoneyGramAccountPayload) paymentAccountPayload).getCountryCode()));
        if (BankUtil.isStateRequired(payload.getCountryCode()))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.account.state"),
                    payload.getState());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.email"),
                payload.getEmail());

        return gridRow;
    }

    protected final MoneyGramAccountPayload moneyGramAccountPayload;
    protected InputTextField holderNameInputTextField, emailInputTextField, stateInputTextField;
    private final EmailValidator emailValidator;

    public MoneyGramForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator,
                         GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.moneyGramAccountPayload = (MoneyGramAccountPayload) paymentAccount.paymentAccountPayload;

        emailValidator = new EmailValidator();
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        final Country country = getMoneyGramPaymentAccount().getCountry();
        FormBuilder.addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), paymentAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(paymentAccount.getPaymentMethod().getId()));
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.country"), country != null ? country.name : "");
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.fullName"),
                moneyGramAccountPayload.getHolderName());
        if (BankUtil.isStateRequired(moneyGramAccountPayload.getCountryCode()))
            FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.account.state"),
                    moneyGramAccountPayload.getState()).second.setMouseTransparent(false);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                moneyGramAccountPayload.getEmail());
        addLimitations();
        addCurrenciesGrid(false);
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        gridRow = GUIUtil.addRegionCountry(gridPane, gridRow, this::onCountrySelected);

        holderNameInputTextField = FormBuilder.addInputTextField(gridPane,
                ++gridRow, Res.getWithCol("payment.account.fullName"));
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            moneyGramAccountPayload.setHolderName(newValue);
            updateFromInputs();
        });
        holderNameInputTextField.setValidator(inputValidator);

        stateInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.account.state"));
        stateInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            moneyGramAccountPayload.setState(newValue);
            updateFromInputs();

        });
        applyIsStateRequired();

        emailInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.email"));
        emailInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            moneyGramAccountPayload.setEmail(newValue);
            updateFromInputs();
        });
        emailInputTextField.setValidator(emailValidator);

        addCurrenciesGrid(true);
        addLimitations();
        addAccountNameTextFieldWithAutoFillToggleButton();

        updateFromInputs();
    }

    private void onCountrySelected(Country country) {
        if (country != null) {
            getMoneyGramPaymentAccount().setCountry(country);
            updateFromInputs();
            applyIsStateRequired();
            stateInputTextField.setText("");
        }
    }

    private void addCurrenciesGrid(boolean isEditable) {
        Label label = addLabel(gridPane, ++gridRow, Res.get("payment.supportedCurrencies"), 0);
        GridPane.setValignment(label, VPos.TOP);
        FlowPane flowPane = new FlowPane();
        flowPane.setPadding(new Insets(10, 10, 10, 10));
        flowPane.setVgap(10);
        flowPane.setHgap(10);

        if (isEditable)
            flowPane.setId("flow-pane-checkboxes-bg");
        else
            flowPane.setId("flow-pane-checkboxes-non-editable-bg");

        CurrencyUtil.getAllMoneyGramCurrencies().forEach(e -> {
            CheckBox checkBox = new AutoTooltipCheckBox(e.getCode());
            checkBox.setMouseTransparent(!isEditable);
            checkBox.setSelected(paymentAccount.getTradeCurrencies().contains(e));
            checkBox.setMinWidth(60);
            checkBox.setMaxWidth(checkBox.getMinWidth());
            checkBox.setTooltip(new Tooltip(e.getName()));
            checkBox.setOnAction(event -> {
                if (checkBox.isSelected())
                    paymentAccount.addCurrency(e);
                else
                    paymentAccount.removeCurrency(e);

                updateAllInputsValid();
            });
            flowPane.getChildren().add(checkBox);
        });

        GridPane.setRowIndex(flowPane, gridRow);
        GridPane.setColumnIndex(flowPane, 1);
        gridPane.getChildren().add(flowPane);
    }

    private void applyIsStateRequired() {
        final boolean stateRequired = BankUtil.isStateRequired(moneyGramAccountPayload.getCountryCode());
        stateInputTextField.setManaged(stateRequired);
        stateInputTextField.setVisible(stateRequired);
    }

    private MoneyGramAccount getMoneyGramPaymentAccount() {
        return (MoneyGramAccount) this.paymentAccount;
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameToggleButton != null && !useCustomAccountNameToggleButton.isSelected()) {
            accountNameTextField.setText(Res.get(paymentAccount.getPaymentMethod().getId())
                    .concat(": ")
                    .concat(StringUtils.abbreviate(holderNameInputTextField.getText(), 9)));
        }
    }

    @Override
    public void updateAllInputsValid() {
        boolean result = isAccountNameValid()
                && getMoneyGramPaymentAccount().getCountry() != null
                && inputValidator.validate(moneyGramAccountPayload.getHolderName()).isValid
                && emailValidator.validate(moneyGramAccountPayload.getEmail()).isValid
                && paymentAccount.getTradeCurrencies().size() > 0;
        allInputsValid.set(result);
    }
}
