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
import io.bitsquare.gui.util.validation.InteracETransferValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.payment.InteracETransferAccount;
import io.bitsquare.payment.InteracETransferAccountContractData;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.addLabelInputTextField;
import static io.bitsquare.gui.util.FormBuilder.addLabelTextField;

public class InteracETransferForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(InteracETransferForm.class);

    private final InteracETransferAccount interacETransferAccount;
    private final InteracETransferValidator interacETransferValidator;
    private InputTextField mobileNrInputTextField;
    private InputTextField questionInputTextField;
    private InputTextField answerInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        addLabelTextField(gridPane, ++gridRow, "Account holder name:", ((InteracETransferAccountContractData) paymentAccountContractData).getHolderName());
        addLabelTextField(gridPane, ++gridRow, "Email or mobile nr:", ((InteracETransferAccountContractData) paymentAccountContractData).getEmail());
        addLabelTextField(gridPane, ++gridRow, "Secret question:", ((InteracETransferAccountContractData) paymentAccountContractData).getQuestion());
        addLabelTextField(gridPane, ++gridRow, "Answer:", ((InteracETransferAccountContractData) paymentAccountContractData).getAnswer());
        return gridRow;
    }

    public InteracETransferForm(PaymentAccount paymentAccount, InteracETransferValidator interacETransferValidator, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.interacETransferAccount = (InteracETransferAccount) paymentAccount;
        this.interacETransferValidator = interacETransferValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Account holder name:").second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            interacETransferAccount.setHolderName(newValue);
            updateFromInputs();
        });

        mobileNrInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Email or mobile nr:").second;
        mobileNrInputTextField.setValidator(interacETransferValidator);
        mobileNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            interacETransferAccount.setEmail(newValue);
            updateFromInputs();
        });

        questionInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Secret question:").second;
        questionInputTextField.setValidator(inputValidator);
        questionInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            interacETransferAccount.setQuestion(newValue);
            updateFromInputs();
        });

        answerInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Answer:").second;
        answerInputTextField.setValidator(inputValidator);
        answerInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            interacETransferAccount.setAnswer(newValue);
            updateFromInputs();
        });

        addLabelTextField(gridPane, ++gridRow, "Currency:", interacETransferAccount.getSingleTradeCurrency().getNameAndCode());
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
        addLabelTextField(gridPane, gridRow, "Account name:", interacETransferAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(interacETransferAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, "Account holder name:", interacETransferAccount.getHolderName());
        addLabelTextField(gridPane, ++gridRow, "Email:", interacETransferAccount.getEmail()).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Secret question:", interacETransferAccount.getQuestion()).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Answer:", interacETransferAccount.getAnswer()).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Currency:", interacETransferAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
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
