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
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.EmailValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.BankUtil;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.payment.MoneyGramAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.MoneyGramAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.util.Tuple2;

import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.*;

@Slf4j
public class MoneyGramForm extends PaymentMethodForm {

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        final MoneyGramAccountPayload payload = (MoneyGramAccountPayload) paymentAccountPayload;
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.account.fullName"),
                payload.getHolderName());
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1, Res.get("payment.email"),
                payload.getEmail());
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                Res.get("payment.bank.country"),
                CountryUtil.getNameAndCode(((MoneyGramAccountPayload) paymentAccountPayload).getCountryCode()));
        if (BankUtil.isStateRequired(payload.getCountryCode()))
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1,
                    Res.get("payment.account.state"),
                    payload.getState());

        return gridRow;
    }

    private final MoneyGramAccountPayload moneyGramAccountPayload;
    private InputTextField holderNameInputTextField;
    private InputTextField stateInputTextField;
    private final EmailValidator emailValidator;

    public MoneyGramForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator,
                         GridPane gridPane, int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.moneyGramAccountPayload = (MoneyGramAccountPayload) paymentAccount.paymentAccountPayload;

        emailValidator = new EmailValidator();
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        final Country country = getMoneyGramPaymentAccount().getCountry();
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), paymentAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(paymentAccount.getPaymentMethod().getId()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.country"), country != null ? country.name : "");
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.fullName"),
                moneyGramAccountPayload.getHolderName());
        if (BankUtil.isStateRequired(moneyGramAccountPayload.getCountryCode()))
            addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.state"),
                    moneyGramAccountPayload.getState()).second.setMouseTransparent(false);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                moneyGramAccountPayload.getEmail());
        addLimitations(true);
        addCurrenciesGrid(false);
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        gridRow = GUIUtil.addRegionCountry(gridPane, gridRow, this::onCountrySelected);

        holderNameInputTextField = addInputTextField(gridPane,
                ++gridRow, Res.get("payment.account.fullName"));
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            moneyGramAccountPayload.setHolderName(newValue);
            updateFromInputs();
        });
        holderNameInputTextField.setValidator(inputValidator);

        stateInputTextField = addInputTextField(gridPane, ++gridRow, Res.get("payment.account.state"));
        stateInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            moneyGramAccountPayload.setState(newValue);
            updateFromInputs();

        });
        applyIsStateRequired();

        InputTextField emailInputTextField = addInputTextField(gridPane, ++gridRow, Res.get("payment.email"));
        emailInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            moneyGramAccountPayload.setEmail(newValue);
            updateFromInputs();
        });
        emailInputTextField.setValidator(emailValidator);

        addCurrenciesGrid(true);
        addLimitations(false);
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
        final Tuple2<Label, FlowPane> labelFlowPaneTuple2 = addTopLabelFlowPane(gridPane, ++gridRow, Res.get("payment.supportedCurrencies"),
                Layout.FLOATING_LABEL_DISTANCE * 3, Layout.FLOATING_LABEL_DISTANCE * 3);

        FlowPane flowPane = labelFlowPaneTuple2.second;

        if (isEditable)
            flowPane.setId("flow-pane-checkboxes-bg");
        else
            flowPane.setId("flow-pane-checkboxes-non-editable-bg");

        CurrencyUtil.getAllMoneyGramCurrencies().forEach(e ->
                fillUpFlowPaneWithCurrencies(isEditable, flowPane, e, paymentAccount));
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
        setAccountNameWithString(holderNameInputTextField.getText());
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
