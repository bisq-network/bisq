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

package io.bisq.gui.components.paymentmethods;

import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.ChaseQuickPayAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.payload.ChaseQuickPayAccountPayload;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.FormBuilder;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.ChaseQuickPayValidator;
import io.bisq.gui.util.validation.InputValidator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChaseQuickPayForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(ChaseQuickPayForm.class);

    private final ChaseQuickPayAccount chaseQuickPayAccount;
    private final ChaseQuickPayValidator chaseQuickPayValidator;
    private InputTextField mobileNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                ((ChaseQuickPayAccountPayload) paymentAccountPayload).getHolderName());
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                ((ChaseQuickPayAccountPayload) paymentAccountPayload).getEmail());
        return gridRow;
    }

    public ChaseQuickPayForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, ChaseQuickPayValidator chaseQuickPayValidator,
                             InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.chaseQuickPayAccount = (ChaseQuickPayAccount) paymentAccount;
        this.chaseQuickPayValidator = chaseQuickPayValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow,
                Res.getWithCol("payment.account.owner")).second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            chaseQuickPayAccount.setHolderName(newValue);
            updateFromInputs();
        });

        mobileNrInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.email")).second;
        mobileNrInputTextField.setValidator(chaseQuickPayValidator);
        mobileNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            chaseQuickPayAccount.setEmail(newValue);
            updateFromInputs();
        });

        TradeCurrency singleTradeCurrency = chaseQuickPayAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
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
                chaseQuickPayAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(chaseQuickPayAccount.getPaymentMethod().getId()));
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                chaseQuickPayAccount.getHolderName());
        TextField field = FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                chaseQuickPayAccount.getEmail()).second;
        field.setMouseTransparent(false);
        TradeCurrency singleTradeCurrency = chaseQuickPayAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && chaseQuickPayValidator.validate(chaseQuickPayAccount.getEmail()).isValid
                && inputValidator.validate(chaseQuickPayAccount.getHolderName()).isValid
                && chaseQuickPayAccount.getTradeCurrencies().size() > 0);
    }
}
