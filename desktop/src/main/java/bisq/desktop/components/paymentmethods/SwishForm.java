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
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.SwishValidator;

import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.SwishAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.SwishAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.addLabelInputTextField;
import static bisq.desktop.util.FormBuilder.addLabelTextField;

public class SwishForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(SwishForm.class);

    private final SwishAccount swishAccount;
    private final SwishValidator swishValidator;
    private InputTextField mobileNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                ((SwishAccountPayload) paymentAccountPayload).getHolderName());
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.mobile"),
                ((SwishAccountPayload) paymentAccountPayload).getMobileNr());
        return gridRow;
    }

    public SwishForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, SwishValidator swishValidator,
                     InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.swishAccount = (SwishAccount) paymentAccount;
        this.swishValidator = swishValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = addLabelInputTextField(gridPane, ++gridRow,
                Res.getWithCol("payment.account.owner")).second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            swishAccount.setHolderName(newValue);
            updateFromInputs();
        });

        mobileNrInputTextField = addLabelInputTextField(gridPane, ++gridRow,
                Res.get("payment.mobile")).second;
        mobileNrInputTextField.setValidator(swishValidator);
        mobileNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            swishAccount.setMobileNr(newValue);
            updateFromInputs();
        });

        TradeCurrency singleTradeCurrency = swishAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
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
        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                swishAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(swishAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                swishAccount.getHolderName());
        TextField field = addLabelTextField(gridPane, ++gridRow, Res.get("payment.mobile"),
                swishAccount.getMobileNr()).second;
        field.setMouseTransparent(false);
        TradeCurrency singleTradeCurrency = swishAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && swishValidator.validate(swishAccount.getMobileNr()).isValid
                && inputValidator.validate(swishAccount.getHolderName()).isValid
                && swishAccount.getTradeCurrencies().size() > 0);
    }
}
