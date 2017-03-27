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

import io.bisq.common.locale.BankUtil;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.core.payment.CountryBasedPaymentAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.validation.InputValidator;
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bisq.gui.util.FormBuilder.addLabelInputTextField;
import static io.bisq.gui.util.FormBuilder.addLabelTextField;

public class SameBankForm extends BankForm {
    private static final Logger log = LoggerFactory.getLogger(SameBankForm.class);

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        return BankForm.addFormForBuyer(gridPane, gridRow, paymentAccountPayload);
    }

    public SameBankForm(PaymentAccount paymentAccount, InputValidator inputValidator,
                        GridPane gridPane, int gridRow, BSFormatter formatter, Runnable closeHandler) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter, closeHandler);
    }

    @Override
    protected void addHolderNameAndId() {
        Tuple2<Label, InputTextField> tuple = addLabelInputTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"));
        holderNameInputTextField = tuple.second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setHolderName(newValue);
            updateFromInputs();
        });
    }

    @Override
    public void updateAllInputsValid() {
        boolean result = isAccountNameValid()
                && inputValidator.validate(bankAccountPayload.getHolderName()).isValid
                && paymentAccount.getSingleTradeCurrency() != null
                && ((CountryBasedPaymentAccount) paymentAccount).getCountry() != null;

        if (BankUtil.isBankNameRequired(bankAccountPayload.getCountryCode()))
            result &= inputValidator.validate(bankAccountPayload.getBankName()).isValid;

        if (BankUtil.isBankIdRequired(bankAccountPayload.getCountryCode()))
            result &= inputValidator.validate(bankAccountPayload.getBankId()).isValid;

        if (BankUtil.isBranchIdRequired(bankAccountPayload.getCountryCode()))
            result &= inputValidator.validate(bankAccountPayload.getBranchId()).isValid;

        if (BankUtil.isAccountNrRequired(bankAccountPayload.getCountryCode()))
            result &= inputValidator.validate(bankAccountPayload.getAccountNr()).isValid;

        allInputsValid.set(result);
    }

    @Override
    protected void addHolderNameAndIdForDisplayAccount() {
        Tuple2<Label, TextField> tuple = addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"));
        TextField holderNameTextField = tuple.second;
        holderNameTextField.setMinWidth(300);
        holderNameTextField.setText(bankAccountPayload.getHolderName());
    }

}
