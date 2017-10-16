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
import io.bisq.core.payment.InteracETransferAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.payload.InteracETransferAccountPayload;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.FormBuilder;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.InputValidator;
import io.bisq.gui.util.validation.InteracETransferValidator;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InteracETransferForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(InteracETransferForm.class);

    private final InteracETransferAccount interacETransferAccount;
    private final InteracETransferValidator interacETransferValidator;
    private InputTextField mobileNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                ((InteracETransferAccountPayload) paymentAccountPayload).getHolderName());
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.emailOrMobile"),
                ((InteracETransferAccountPayload) paymentAccountPayload).getEmail());
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.secret"),
                ((InteracETransferAccountPayload) paymentAccountPayload).getQuestion());
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.answer"),
                ((InteracETransferAccountPayload) paymentAccountPayload).getAnswer());
        return gridRow;
    }

    public InteracETransferForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InteracETransferValidator interacETransferValidator,
                                InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.interacETransferAccount = (InteracETransferAccount) paymentAccount;
        this.interacETransferValidator = interacETransferValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow,
                Res.getWithCol("payment.account.owner")).second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            interacETransferAccount.setHolderName(newValue);
            updateFromInputs();
        });

        mobileNrInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.emailOrMobile")).second;
        mobileNrInputTextField.setValidator(interacETransferValidator);
        mobileNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            interacETransferAccount.setEmail(newValue);
            updateFromInputs();
        });

        InputTextField questionInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.secret")).second;
        questionInputTextField.setValidator(inputValidator);
        questionInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            interacETransferAccount.setQuestion(newValue);
            updateFromInputs();
        });

        InputTextField answerInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.answer")).second;
        answerInputTextField.setValidator(inputValidator);
        answerInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            interacETransferAccount.setAnswer(newValue);
            updateFromInputs();
        });
        TradeCurrency singleTradeCurrency = interacETransferAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"),
                nameAndCode);
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
                interacETransferAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(interacETransferAccount.getPaymentMethod().getId()));
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                interacETransferAccount.getHolderName());
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                interacETransferAccount.getEmail()).second.setMouseTransparent(false);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.secret"),
                interacETransferAccount.getQuestion()).second.setMouseTransparent(false);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.answer"),
                interacETransferAccount.getAnswer()).second.setMouseTransparent(false);
        TradeCurrency singleTradeCurrency = interacETransferAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"),
                nameAndCode);
        addLimitations();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && interacETransferValidator.validate(interacETransferAccount.getEmail()).isValid
                && inputValidator.validate(interacETransferAccount.getHolderName()).isValid
                && inputValidator.validate(interacETransferAccount.getQuestion()).isValid
                && inputValidator.validate(interacETransferAccount.getAnswer()).isValid
                && interacETransferAccount.getTradeCurrencies().size() > 0);
    }
}
