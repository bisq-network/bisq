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
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.USPostalMoneyOrderAccount;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.USPostalMoneyOrderAccountPayload;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.InputValidator;
import io.bisq.gui.util.validation.USPostalMoneyOrderValidator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bisq.gui.util.FormBuilder.*;

public class USPostalMoneyOrderForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(USPostalMoneyOrderForm.class);

    private final USPostalMoneyOrderAccount usPostalMoneyOrderAccount;
    private final USPostalMoneyOrderValidator usPostalMoneyOrderValidator;
    private TextArea postalAddressTextArea;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                ((USPostalMoneyOrderAccountPayload) paymentAccountPayload).getHolderName());
        TextArea textArea = addLabelTextArea(gridPane, ++gridRow, Res.get("payment.postal.address"), "").second;
        textArea.setPrefHeight(60);
        textArea.setEditable(false);
        textArea.setId("text-area-disabled");
        textArea.setText(((USPostalMoneyOrderAccountPayload) paymentAccountPayload).getPostalAddress());
        return gridRow;
    }

    public USPostalMoneyOrderForm(PaymentAccount paymentAccount,
                                  AccountAgeWitnessService accountAgeWitnessService, USPostalMoneyOrderValidator usPostalMoneyOrderValidator,
                                  InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.usPostalMoneyOrderAccount = (USPostalMoneyOrderAccount) paymentAccount;
        this.usPostalMoneyOrderValidator = usPostalMoneyOrderValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = addLabelInputTextField(gridPane, ++gridRow,
                Res.getWithCol("payment.account.owner")).second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            usPostalMoneyOrderAccount.setHolderName(newValue);
            updateFromInputs();
        });

        postalAddressTextArea = addLabelTextArea(gridPane, ++gridRow,
                Res.get("payment.postal.address"), "").second;
        postalAddressTextArea.setPrefHeight(60);
        //postalAddressTextArea.setValidator(usPostalMoneyOrderValidator);
        postalAddressTextArea.textProperty().addListener((ov, oldValue, newValue) -> {
            usPostalMoneyOrderAccount.setPostalAddress(newValue);
            updateFromInputs();
        });


        TradeCurrency singleTradeCurrency = usPostalMoneyOrderAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"),
                nameAndCode);
        addLimitations();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String postalAddress = postalAddressTextArea.getText();
            postalAddress = StringUtils.abbreviate(postalAddress, 9);
            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(postalAddress));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                usPostalMoneyOrderAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(usPostalMoneyOrderAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                usPostalMoneyOrderAccount.getHolderName());
        TextArea textArea = addLabelTextArea(gridPane, ++gridRow, Res.get("payment.postal.address"), "").second;
        textArea.setText(usPostalMoneyOrderAccount.getPostalAddress());
        textArea.setPrefHeight(60);
        textArea.setEditable(false);
        TradeCurrency singleTradeCurrency = usPostalMoneyOrderAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addLimitations();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && usPostalMoneyOrderValidator.validate(usPostalMoneyOrderAccount.getPostalAddress()).isValid
                && !postalAddressTextArea.getText().isEmpty()
                && inputValidator.validate(usPostalMoneyOrderAccount.getHolderName()).isValid
                && usPostalMoneyOrderAccount.getTradeCurrencies().size() > 0);
    }
}
