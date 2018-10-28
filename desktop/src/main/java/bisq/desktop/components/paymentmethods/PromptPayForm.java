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
import bisq.core.payment.PromptPayAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.PromptPayAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import static bisq.desktop.util.FormBuilder.addLabelInputTextField;
import static bisq.desktop.util.FormBuilder.addLabelTextField;

public class PromptPayForm extends PaymentMethodForm {
    private final PromptPayAccount promptPayAccount;
    private final PromptPayValidator promptPayValidator;
    private InputTextField promptPayIdInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.promptPay.promptPayId"),
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

        promptPayIdInputTextField = addLabelInputTextField(gridPane, ++gridRow,
                Res.get("payment.promptPay.promptPayId")).second;
        promptPayIdInputTextField.setValidator(promptPayValidator);
        promptPayIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            promptPayAccount.setPromptPayId(newValue);
            updateFromInputs();
        });

        TradeCurrency singleTradeCurrency = promptPayAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String promptPayId = promptPayIdInputTextField.getText();
            promptPayId = StringUtils.abbreviate(promptPayId, 9);
            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(promptPayId));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                promptPayAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(promptPayAccount.getPaymentMethod().getId()));
        TextField field = addLabelTextField(gridPane, ++gridRow, Res.get("payment.promptPay.promptPayId"),
                promptPayAccount.getPromptPayId()).second;
        field.setMouseTransparent(false);
        TradeCurrency singleTradeCurrency = promptPayAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && promptPayValidator.validate(promptPayAccount.getPromptPayId()).isValid
                && promptPayAccount.getTradeCurrencies().size() > 0);
    }
}
