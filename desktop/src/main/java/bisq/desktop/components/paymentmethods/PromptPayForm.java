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
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.PromptPayValidator;

import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PromptPayAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PromptPayAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTextField;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;

public class PromptPayForm extends PaymentMethodForm {
    private final PromptPayAccount promptPayAccount;
    private final PromptPayValidator promptPayValidator;
    private InputTextField promptPayIdInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        addTopLabelTextField(gridPane, ++gridRow, Res.get("payment.promptPay.promptPayId"),
                ((PromptPayAccountPayload) paymentAccountPayload).getPromptPayId());
        return gridRow;
    }

    public PromptPayForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, PromptPayValidator promptPayValidator,
                       InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.promptPayAccount = (PromptPayAccount) paymentAccount;
        this.promptPayValidator = promptPayValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        promptPayIdInputTextField = addInputTextField(gridPane, ++gridRow,
                Res.get("payment.promptPay.promptPayId"));
        promptPayIdInputTextField.setValidator(promptPayValidator);
        promptPayIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            promptPayAccount.setPromptPayId(newValue);
            updateFromInputs();
        });

        TradeCurrency singleTradeCurrency = promptPayAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(promptPayIdInputTextField.getText());
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addTextField(gridPane, gridRow, Res.get("payment.account.name"),
                promptPayAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(promptPayAccount.getPaymentMethod().getId()));
        TextField field = addTextField(gridPane, ++gridRow, Res.get("payment.promptPay.promptPayId"),
                promptPayAccount.getPromptPayId());
        field.setMouseTransparent(false);
        TradeCurrency singleTradeCurrency = promptPayAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && promptPayValidator.validate(promptPayAccount.getPromptPayId()).isValid
                && promptPayAccount.getTradeCurrencies().size() > 0);
    }
}
