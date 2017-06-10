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
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.AccountNrValidator;
import io.bitsquare.gui.util.validation.BranchIdValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.payment.FasterPaymentsAccount;
import io.bitsquare.payment.FasterPaymentsAccountContractData;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.addLabelInputTextField;
import static io.bitsquare.gui.util.FormBuilder.addLabelTextField;

public class FasterPaymentsForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(FasterPaymentsForm.class);

    private final FasterPaymentsAccount fasterPaymentsAccount;
    private InputTextField accountNrInputTextField;
    private InputTextField sortCodeInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        addLabelTextField(gridPane, ++gridRow, "UK sort code:", ((FasterPaymentsAccountContractData) paymentAccountContractData).getSortCode());
        addLabelTextField(gridPane, ++gridRow, "Account number:", ((FasterPaymentsAccountContractData) paymentAccountContractData).getAccountNr());
        return gridRow;
    }

    public FasterPaymentsForm(PaymentAccount paymentAccount, InputValidator inputValidator, GridPane gridPane,
                              int gridRow, BSFormatter formatter) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.fasterPaymentsAccount = (FasterPaymentsAccount) paymentAccount;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        sortCodeInputTextField = addLabelInputTextField(gridPane, ++gridRow, "UK sort code:").second;
        sortCodeInputTextField.setValidator(inputValidator);
        sortCodeInputTextField.setValidator(new BranchIdValidator("GB"));
        sortCodeInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            fasterPaymentsAccount.setSortCode(newValue);
            updateFromInputs();
        });

        accountNrInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Account number:").second;
        accountNrInputTextField.setValidator(new AccountNrValidator("GB"));
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            fasterPaymentsAccount.setAccountNr(newValue);
            updateFromInputs();
        });

        addLabelTextField(gridPane, ++gridRow, "Currency:", fasterPaymentsAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String accountNr = accountNrInputTextField.getText();
            accountNr = StringUtils.abbreviate(accountNr, 9);
            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(accountNr));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, "Account name:", fasterPaymentsAccount.getAccountName(), MainView.scale(Layout.FIRST_ROW_AND_GROUP_DISTANCE));
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(fasterPaymentsAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, "UK Sort code:", fasterPaymentsAccount.getSortCode());
        TextField field = addLabelTextField(gridPane, ++gridRow, "Account number:", fasterPaymentsAccount.getAccountNr()).second;
        field.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Currency:", fasterPaymentsAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && sortCodeInputTextField.getValidator().validate(fasterPaymentsAccount.getSortCode()).isValid
                && accountNrInputTextField.getValidator().validate(fasterPaymentsAccount.getAccountNr()).isValid
                && fasterPaymentsAccount.getTradeCurrencies().size() > 0);
    }
}
