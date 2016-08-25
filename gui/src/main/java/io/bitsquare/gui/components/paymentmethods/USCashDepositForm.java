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
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.gui.util.validation.USCashDepositValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.payment.USCashDepositAccount;
import io.bitsquare.payment.USCashDepositAccountContractData;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.addLabelInputTextField;
import static io.bitsquare.gui.util.FormBuilder.addLabelTextField;

public class USCashDepositForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(USCashDepositForm.class);

    private final USCashDepositAccount usCashDepositAccount;
    private final USCashDepositValidator usCashDepositValidator;
    private InputTextField mobileNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        addLabelTextField(gridPane, ++gridRow, "Account holder name:", ((USCashDepositAccountContractData) paymentAccountContractData).getEmailOrMobileNr());
        addLabelTextField(gridPane, ++gridRow, "Mobile nr.:", ((USCashDepositAccountContractData) paymentAccountContractData).getEmailOrMobileNr());
        return gridRow;
    }

    public USCashDepositForm(PaymentAccount paymentAccount, USCashDepositValidator usCashDepositValidator, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.usCashDepositAccount = (USCashDepositAccount) paymentAccount;
        this.usCashDepositValidator = usCashDepositValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Account holder name:").second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            usCashDepositAccount.setEmailOrMobileNr(newValue);
            updateFromInputs();
        });

        mobileNrInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Mobile nr.:").second;
        mobileNrInputTextField.setValidator(usCashDepositValidator);
        mobileNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            usCashDepositAccount.setEmailOrMobileNr(newValue);
            updateFromInputs();
        });

        addLabelTextField(gridPane, ++gridRow, "Currency:", usCashDepositAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String mobileNr = mobileNrInputTextField.getText();
            mobileNr = StringUtils.abbreviate(mobileNr, 9);
            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(mobileNr));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, "Account name:", usCashDepositAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(usCashDepositAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, "Account holder name:", usCashDepositAccount.getEmailOrMobileNr());
        TextField field = addLabelTextField(gridPane, ++gridRow, "Mobile nr.:", usCashDepositAccount.getEmailOrMobileNr()).second;
        field.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Currency:", usCashDepositAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && usCashDepositValidator.validate(usCashDepositAccount.getEmailOrMobileNr()).isValid
                && inputValidator.validate(usCashDepositAccount.getEmailOrMobileNr()).isValid
                && usCashDepositAccount.getTradeCurrencies().size() > 0);
    }
}
