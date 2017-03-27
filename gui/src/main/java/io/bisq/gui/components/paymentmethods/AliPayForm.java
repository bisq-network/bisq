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
import io.bisq.core.payment.AliPayAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.AliPayValidator;
import io.bisq.gui.util.validation.InputValidator;
import io.bisq.protobuffer.payload.payment.AliPayAccountPayload;
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bisq.gui.util.FormBuilder.*;

public class AliPayForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(AliPayForm.class);

    private final AliPayAccount aliPayAccount;
    private final AliPayValidator aliPayValidator;
    private InputTextField accountNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.account.no"), ((AliPayAccountPayload) paymentAccountPayload).getAccountNr());
        return gridRow;
    }

    public AliPayForm(PaymentAccount paymentAccount, AliPayValidator aliPayValidator, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.aliPayAccount = (AliPayAccount) paymentAccount;
        this.aliPayValidator = aliPayValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        accountNrInputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.account.no")).second;
        accountNrInputTextField.setValidator(aliPayValidator);
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            aliPayAccount.setAccountNr(newValue);
            updateFromInputs();
        });

        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), aliPayAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String accountNr = accountNrInputTextField.getText();
            accountNr = StringUtils.abbreviate(accountNr, 9);
            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(accountNr));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), aliPayAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"), Res.get(aliPayAccount.getPaymentMethod().getId()));
        TextField field = addLabelTextField(gridPane, ++gridRow, Res.get("payment.account.no"), aliPayAccount.getAccountNr()).second;
        field.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), aliPayAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && aliPayValidator.validate(aliPayAccount.getAccountNr()).isValid
                && aliPayAccount.getTradeCurrencies().size() > 0);
    }

}
