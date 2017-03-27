/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.components.paymentmethods;

import io.bisq.common.locale.Res;
import io.bisq.core.payment.ClearXchangeAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.FormBuilder;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.ClearXchangeValidator;
import io.bisq.gui.util.validation.InputValidator;
import io.bisq.protobuffer.payload.payment.ClearXchangeAccountPayload;
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClearXchangeForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(ClearXchangeForm.class);

    private final ClearXchangeAccount clearXchangeAccount;
    private final ClearXchangeValidator clearXchangeValidator;
    private InputTextField mobileNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                ((ClearXchangeAccountPayload) paymentAccountPayload).getHolderName());
        FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.email.mobile"),
                ((ClearXchangeAccountPayload) paymentAccountPayload).getEmailOrMobileNr());
        return gridRow;
    }

    public ClearXchangeForm(PaymentAccount paymentAccount, ClearXchangeValidator clearXchangeValidator, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.clearXchangeAccount = (ClearXchangeAccount) paymentAccount;
        this.clearXchangeValidator = clearXchangeValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow,
                Res.getWithCol("payment.account.owner")).second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            clearXchangeAccount.setHolderName(newValue);
            updateFromInputs();
        });

        mobileNrInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow,
                Res.get("payment.email.mobile")).second;
        mobileNrInputTextField.setValidator(clearXchangeValidator);
        mobileNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            clearXchangeAccount.setEmailOrMobileNr(newValue);
            updateFromInputs();
        });

        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"),
                clearXchangeAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String mobileNr = mobileNrInputTextField.getText();
            mobileNr = StringUtils.abbreviate(mobileNr, 9);
            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(mobileNr));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        FormBuilder.addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                clearXchangeAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(clearXchangeAccount.getPaymentMethod().getId()));
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                clearXchangeAccount.getHolderName());
        TextField field = FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.email.mobile"),
                clearXchangeAccount.getEmailOrMobileNr()).second;
        field.setMouseTransparent(false);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"),
                clearXchangeAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && clearXchangeValidator.validate(clearXchangeAccount.getEmailOrMobileNr()).isValid
                && inputValidator.validate(clearXchangeAccount.getHolderName()).isValid
                && clearXchangeAccount.getTradeCurrencies().size() > 0);
    }
}
