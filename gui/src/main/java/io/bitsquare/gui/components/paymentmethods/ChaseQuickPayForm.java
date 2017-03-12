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

package io.bitsquare.gui.components.paymentmethods;

import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.ChaseQuickPayValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.Res;
import io.bitsquare.messages.locale.TradeCurrency;
import io.bitsquare.messages.payment.payload.ChaseQuickPayAccountContractData;
import io.bitsquare.messages.payment.payload.PaymentAccountContractData;
import io.bitsquare.payment.ChaseQuickPayAccount;
import io.bitsquare.payment.PaymentAccount;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.addLabelInputTextField;
import static io.bitsquare.gui.util.FormBuilder.addLabelTextField;

public class ChaseQuickPayForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(ChaseQuickPayForm.class);

    private final ChaseQuickPayAccount chaseQuickPayAccount;
    private final ChaseQuickPayValidator chaseQuickPayValidator;
    private InputTextField mobileNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                ((ChaseQuickPayAccountContractData) paymentAccountContractData).getHolderName());
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                ((ChaseQuickPayAccountContractData) paymentAccountContractData).getEmail());
        return gridRow;
    }

    public ChaseQuickPayForm(PaymentAccount paymentAccount, ChaseQuickPayValidator chaseQuickPayValidator,
                             InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.chaseQuickPayAccount = (ChaseQuickPayAccount) paymentAccount;
        this.chaseQuickPayValidator = chaseQuickPayValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = addLabelInputTextField(gridPane, ++gridRow,
                Res.getWithCol("payment.account.owner")).second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            chaseQuickPayAccount.setHolderName(newValue);
            updateFromInputs();
        });

        mobileNrInputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.email")).second;
        mobileNrInputTextField.setValidator(chaseQuickPayValidator);
        mobileNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            chaseQuickPayAccount.setEmail(newValue);
            updateFromInputs();
        });

        TradeCurrency singleTradeCurrency = chaseQuickPayAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
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
        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                chaseQuickPayAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(chaseQuickPayAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                chaseQuickPayAccount.getHolderName());
        TextField field = addLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                chaseQuickPayAccount.getEmail()).second;
        field.setMouseTransparent(false);
        TradeCurrency singleTradeCurrency = chaseQuickPayAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addAllowedPeriod();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && chaseQuickPayValidator.validate(chaseQuickPayAccount.getEmail()).isValid
                && inputValidator.validate(chaseQuickPayAccount.getHolderName()).isValid
                && chaseQuickPayAccount.getTradeCurrencies().size() > 0);
    }
}
