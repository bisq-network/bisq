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
import bisq.desktop.util.validation.CashAppValidator;

import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.CashAppAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.CashAppAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;

// Removed due too high chargeback risk
@Deprecated
public class CashAppForm extends PaymentMethodForm {
    private final CashAppAccount account;
    private final CashAppValidator validator;
    private InputTextField accountIdInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.cashApp.cashTag"), ((CashAppAccountPayload) paymentAccountPayload).getCashTag());
        return gridRow;
    }

    public CashAppForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, CashAppValidator cashAppValidator, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.account = (CashAppAccount) paymentAccount;
        this.validator = cashAppValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        accountIdInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.cashApp.cashTag"));
        accountIdInputTextField.setValidator(validator);
        accountIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setCashTag(newValue);
            updateFromInputs();
        });

        final TradeCurrency singleTradeCurrency = account.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        FormBuilder.addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);
        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(accountIdInputTextField.getText());
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), account.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        FormBuilder.addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"), Res.get(account.getPaymentMethod().getId()));
        TextField field = FormBuilder.addTopLabelTextField(gridPane, ++gridRow, Res.get("payment.cashApp.cashTag"), account.getCashTag()).second;
        field.setMouseTransparent(false);
        final TradeCurrency singleTradeCurrency = account.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        FormBuilder.addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);
        addLimitations(true);
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && validator.validate(account.getCashTag()).isValid
                && account.getTradeCurrencies().size() > 0);
    }
}
