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
import bisq.desktop.util.validation.TransferwiseValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.TransferwiseAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.TransferwiseAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;

public class TransferwiseForm extends PaymentMethodForm {
    private final TransferwiseAccount account;
    private final TransferwiseValidator validator;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner"),
                ((TransferwiseAccountPayload) paymentAccountPayload).getHolderName());
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.email"),
                ((TransferwiseAccountPayload) paymentAccountPayload).getEmail());
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner"),
                paymentAccountPayload.getHolderName());
        return gridRow;
    }

    public TransferwiseForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService,
                            TransferwiseValidator validator, InputValidator inputValidator, GridPane gridPane,
                            int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.account = (TransferwiseAccount) paymentAccount;
        this.validator = validator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow,
        Res.get("payment.account.owner"));
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setHolderName(newValue.trim());
            updateFromInputs();
        });

        InputTextField emailInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.email"));
        emailInputTextField.setValidator(validator);
        emailInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setEmail(newValue.trim());
            updateFromInputs();
        });

        InputTextField holderNameInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow,
                Res.get("payment.account.owner"));
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setHolderName(newValue);
            updateFromInputs();
        });

        addCurrenciesGrid(true);
        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    private void addCurrenciesGrid(boolean isEditable) {
        FlowPane flowPane = FormBuilder.addTopLabelFlowPane(gridPane, ++gridRow,
                Res.get("payment.supportedCurrenciesForReceiver"), 20, 20).second;

        if (isEditable) {
            flowPane.setId("flow-pane-checkboxes-bg");
        } else {
            flowPane.setId("flow-pane-checkboxes-non-editable-bg");
        }

        account.getSupportedCurrencies().forEach(currency ->
                fillUpFlowPaneWithCurrencies(isEditable, flowPane, currency, account));
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(account.getEmail());
    }

    @Override
    public void addFormForEditAccount() {
        gridRowFrom = gridRow;
        addAccountNameTextFieldWithAutoFillToggleButton();
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(account.getPaymentMethod().getId()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner"),
                account.getHolderName());
        TextField field = addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                account.getEmail()).second;
        field.setMouseTransparent(false);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner"),
                account.getHolderName());
        addLimitations(true);
        addCurrenciesGrid(false);
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && inputValidator.validate(account.getHolderName()).isValid
                && validator.validate(account.getEmail()).isValid
                && inputValidator.validate(account.getHolderName()).isValid
                && account.getTradeCurrencies().size() > 0);
    }
}
