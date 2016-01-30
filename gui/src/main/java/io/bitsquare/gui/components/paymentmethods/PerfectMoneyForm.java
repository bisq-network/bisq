/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.components.paymentmethods;

import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.gui.util.validation.PerfectMoneyValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.payment.PerfectMoneyAccount;
import io.bitsquare.payment.PerfectMoneyAccountContractData;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.*;

public class PerfectMoneyForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(PerfectMoneyForm.class);

    private final PerfectMoneyAccount perfectMoneyAccount;
    private final PerfectMoneyValidator perfectMoneyValidator;
    private InputTextField accountNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(paymentAccountContractData.getPaymentMethodName()));
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Account nr.:", ((PerfectMoneyAccountContractData) paymentAccountContractData).getAccountNr());
        addAllowedPeriod(gridPane, ++gridRow, paymentAccountContractData);
        return gridRow;
    }

    public PerfectMoneyForm(PaymentAccount paymentAccount, PerfectMoneyValidator perfectMoneyValidator, InputValidator inputValidator, GridPane gridPane, int
            gridRow) {
        super(paymentAccount, inputValidator, gridPane, gridRow);
        this.perfectMoneyAccount = (PerfectMoneyAccount) paymentAccount;
        this.perfectMoneyValidator = perfectMoneyValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        accountNrInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Account nr.:").second;
        accountNrInputTextField.setValidator(perfectMoneyValidator);
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            perfectMoneyAccount.setAccountNr(newValue);
            updateFromInputs();
        });

        addLabelTextField(gridPane, ++gridRow, "Currency:", perfectMoneyAccount.getSingleTradeCurrency().getCodeAndName());
        addAllowedPeriod();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }


    @Override
    protected void autoFillNameTextField() {
        if (autoFillCheckBox != null && autoFillCheckBox.isSelected()) {
            String accountNr = accountNrInputTextField.getText();
            accountNr = accountNr.substring(0, Math.min(5, accountNr.length())) + "...";
            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(", ").concat(accountNr));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, "Account name:", perfectMoneyAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(perfectMoneyAccount.getPaymentMethod().getId()));
        TextField field = addLabelTextField(gridPane, ++gridRow, "Account nr.:", perfectMoneyAccount.getAccountNr()).second;
        field.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Currency:", perfectMoneyAccount.getSingleTradeCurrency().getCodeAndName());
        addAllowedPeriod();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && perfectMoneyValidator.validate(perfectMoneyAccount.getAccountNr()).isValid
                && perfectMoneyAccount.getTradeCurrencies().size() > 0);
    }
}
