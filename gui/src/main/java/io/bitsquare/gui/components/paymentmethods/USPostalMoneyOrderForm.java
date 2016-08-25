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
import io.bitsquare.gui.util.validation.USPostalMoneyOrderValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.payment.USPostalMoneyOrderAccount;
import io.bitsquare.payment.USPostalMoneyOrderAccountContractData;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.*;

public class USPostalMoneyOrderForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(USPostalMoneyOrderForm.class);

    private final USPostalMoneyOrderAccount usPostalMoneyOrderAccount;
    private final USPostalMoneyOrderValidator usPostalMoneyOrderValidator;
    private TextArea postalAddressTextArea;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        addLabelTextField(gridPane, ++gridRow, "Account holder name:", ((USPostalMoneyOrderAccountContractData) paymentAccountContractData).getHolderName());
        TextArea textArea = addLabelTextArea(gridPane, ++gridRow, "Postal address:", "").second;
        textArea.setPrefHeight(60);
        textArea.setEditable(false);
        textArea.setId("text-area-disabled");
        textArea.setText(((USPostalMoneyOrderAccountContractData) paymentAccountContractData).getPostalAddress());
        return gridRow;
    }

    public USPostalMoneyOrderForm(PaymentAccount paymentAccount, USPostalMoneyOrderValidator usPostalMoneyOrderValidator, InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.usPostalMoneyOrderAccount = (USPostalMoneyOrderAccount) paymentAccount;
        this.usPostalMoneyOrderValidator = usPostalMoneyOrderValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Account holder name:").second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            usPostalMoneyOrderAccount.setHolderName(newValue);
            updateFromInputs();
        });

        postalAddressTextArea = addLabelTextArea(gridPane, ++gridRow, "Postal address:", "").second;
        postalAddressTextArea.setPrefHeight(60);
        //postalAddressTextArea.setValidator(usPostalMoneyOrderValidator);
        postalAddressTextArea.textProperty().addListener((ov, oldValue, newValue) -> {
            usPostalMoneyOrderAccount.setPostalAddress(newValue);
            updateFromInputs();
        });


        addLabelTextField(gridPane, ++gridRow, "Currency:", usPostalMoneyOrderAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String postalAddress = postalAddressTextArea.getText();
            postalAddress = StringUtils.abbreviate(postalAddress, 9);
            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(postalAddress));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, "Account name:", usPostalMoneyOrderAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(usPostalMoneyOrderAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, "Account holder name:", usPostalMoneyOrderAccount.getHolderName());
        TextArea textArea = addLabelTextArea(gridPane, ++gridRow, "Postal address:", "").second;
        textArea.setText(usPostalMoneyOrderAccount.getPostalAddress());
        textArea.setPrefHeight(60);
        textArea.setEditable(false);
        addLabelTextField(gridPane, ++gridRow, "Currency:", usPostalMoneyOrderAccount.getSingleTradeCurrency().getNameAndCode());
        addAllowedPeriod();
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
