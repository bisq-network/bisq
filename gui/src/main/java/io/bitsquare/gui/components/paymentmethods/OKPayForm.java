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
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.gui.util.validation.OKPayValidator;
import io.bitsquare.locale.BSResources;
import io.bitsquare.locale.CurrencyUtil;
import io.bitsquare.payment.OKPayAccount;
import io.bitsquare.payment.OKPayAccountContractData;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.*;

public class OKPayForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(OKPayForm.class);

    private final OKPayAccount okPayAccount;
    private final OKPayValidator okPayValidator;
    private InputTextField accountNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(paymentAccountContractData.getPaymentMethodName()));
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Account nr.:", ((OKPayAccountContractData) paymentAccountContractData).getAccountNr());
        addAllowedPeriod(gridPane, ++gridRow, paymentAccountContractData);
        return gridRow;
    }

    public OKPayForm(PaymentAccount paymentAccount, OKPayValidator okPayValidator, InputValidator inputValidator, GridPane gridPane, int gridRow) {
        super(paymentAccount, inputValidator, gridPane, gridRow);
        this.okPayAccount = (OKPayAccount) paymentAccount;
        this.okPayValidator = okPayValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        accountNrInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Account nr.:").second;
        accountNrInputTextField.setValidator(okPayValidator);
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            okPayAccount.setAccountNr(newValue);
            updateFromInputs();
        });

        addCurrenciesGrid(true);
        addAllowedPeriod();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    private void addCurrenciesGrid(boolean isEditable) {
        Label label = addLabel(gridPane, ++gridRow, "Supported OKPay currencies:", 0);
        GridPane.setValignment(label, VPos.TOP);
        FlowPane flowPane = new FlowPane();
        flowPane.setPadding(new Insets(10, 10, 10, 10));
        flowPane.setVgap(10);
        flowPane.setHgap(10);

        if (isEditable)
            flowPane.setId("flowpane-checkboxes-bg");
        else
            flowPane.setId("flowpane-checkboxes-non-editable-bg");

        CurrencyUtil.getAllOKPayCurrencies().stream().forEach(e ->
        {
            CheckBox checkBox = new CheckBox(e.getCode());
            checkBox.setMouseTransparent(!isEditable);
            checkBox.setSelected(okPayAccount.getTradeCurrencies().contains(e));
            checkBox.setMinWidth(60);
            checkBox.setMaxWidth(checkBox.getMinWidth());
            checkBox.setTooltip(new Tooltip(e.getName()));
            checkBox.setOnAction(event -> {
                if (checkBox.isSelected())
                    okPayAccount.addCurrency(e);
                else
                    okPayAccount.removeCurrency(e);

                updateAllInputsValid();
            });
            flowPane.getChildren().add(checkBox);
        });

        GridPane.setRowIndex(flowPane, gridRow);
        GridPane.setColumnIndex(flowPane, 1);
        gridPane.getChildren().add(flowPane);
    }

    @Override
    protected void autoFillNameTextField() {
        if (autoFillCheckBox != null && autoFillCheckBox.isSelected()) {
            String accountNr = accountNrInputTextField.getText();
            accountNr = accountNr.substring(0, Math.min(5, accountNr.length())) + "...";
            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(", ").concat(accountNr));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, "Account name:", okPayAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(okPayAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, "Account nr.:", okPayAccount.getAccountNr());
        addAllowedPeriod();
        addCurrenciesGrid(false);
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && okPayValidator.validate(okPayAccount.getAccountNr()).isValid
                && okPayAccount.getTradeCurrencies().size() > 0);
    }

}
