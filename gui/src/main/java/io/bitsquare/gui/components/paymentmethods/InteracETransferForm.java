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
import io.bitsquare.locale.Res;
import io.bitsquare.messages.locale.TradeCurrency;
import io.bitsquare.messages.payment.payload.InteracETransferAccountContractData;
import io.bitsquare.messages.payment.payload.PaymentAccountContractData;
import io.bitsquare.payment.InteracETransferAccount;
import io.bitsquare.payment.PaymentAccount;
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

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountContractData paymentAccountContractData) {
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                ((InteracETransferAccountContractData) paymentAccountContractData).getHolderName());
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.emailOrMobile"),
                ((InteracETransferAccountContractData) paymentAccountContractData).getEmail());
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.secret"),
                ((InteracETransferAccountContractData) paymentAccountContractData).getQuestion());
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.answer"),
                ((InteracETransferAccountContractData) paymentAccountContractData).getAnswer());
        return gridRow;
    }

    public InteracETransferForm(PaymentAccount paymentAccount, InteracETransferValidator interacETransferValidator,
                                InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.interacETransferAccount = (InteracETransferAccount) paymentAccount;
        this.interacETransferValidator = interacETransferValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = addLabelInputTextField(gridPane, ++gridRow,
                Res.getWithCol("payment.account.owner")).second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            interacETransferAccount.setHolderName(newValue);
            updateFromInputs();
        });

        mobileNrInputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.emailOrMobile")).second;
        mobileNrInputTextField.setValidator(interacETransferValidator);
        mobileNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            interacETransferAccount.setEmail(newValue);
            updateFromInputs();
        });

        questionInputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.secret")).second;
        questionInputTextField.setValidator(inputValidator);
        questionInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            interacETransferAccount.setQuestion(newValue);
            updateFromInputs();
        });

        answerInputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.answer")).second;
        answerInputTextField.setValidator(inputValidator);
        answerInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            interacETransferAccount.setAnswer(newValue);
            updateFromInputs();
        });
        TradeCurrency singleTradeCurrency = interacETransferAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"),
                nameAndCode);
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
                interacETransferAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(interacETransferAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                interacETransferAccount.getHolderName());
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                interacETransferAccount.getEmail()).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.secret"),
                interacETransferAccount.getQuestion()).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.answer"),
                interacETransferAccount.getAnswer()).second.setMouseTransparent(false);
        TradeCurrency singleTradeCurrency = interacETransferAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"),
                nameAndCode);
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
