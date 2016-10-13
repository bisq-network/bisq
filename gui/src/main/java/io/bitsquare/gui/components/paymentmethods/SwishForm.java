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
import io.bitsquare.gui.util.validation.SwishValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.payment.SwishAccount;
import io.bitsquare.payment.SwishAccountContractData;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.addLabelInputTextField;
import static io.bitsquare.gui.util.FormBuilder.addLabelTextField;

public class SwishForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(SwishForm.class);

    private final SwishAccount swishAccount;
    private final SwishValidator swishValidator;
    private InputTextField mobileNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        addLabelTextField(gridPane, ++gridRow, "Account holder name:", ((SwishAccountContractData) paymentAccountContractData).getHolderName());
        addLabelTextField(gridPane, ++gridRow, "Mobile no.:", ((SwishAccountContractData) paymentAccountContractData).getMobileNr());
        return gridRow;
    }

    public SwishForm(PaymentAccount paymentAccount, SwishValidator swishValidator, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.swishAccount = (SwishAccount) paymentAccount;
        this.swishValidator = swishValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Account holder name:").second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            swishAccount.setHolderName(newValue);
            updateFromInputs();
        });

        mobileNrInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Mobile no.:").second;
        mobileNrInputTextField.setValidator(swishValidator);
        mobileNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            swishAccount.setMobileNr(newValue);
            updateFromInputs();
        });

        addLabelTextField(gridPane, ++gridRow, "Currency:", swishAccount.getSingleTradeCurrency().getNameAndCode());
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
        addLabelTextField(gridPane, gridRow, "Account name:", swishAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(swishAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, "Account holder name:", swishAccount.getHolderName());
        TextField field = addLabelTextField(gridPane, ++gridRow, "Mobile no.:", swishAccount.getMobileNr()).second;
        field.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Currency:", swishAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && swishValidator.validate(swishAccount.getMobileNr()).isValid
                && inputValidator.validate(swishAccount.getHolderName()).isValid
                && swishAccount.getTradeCurrencies().size() > 0);
    }
}
