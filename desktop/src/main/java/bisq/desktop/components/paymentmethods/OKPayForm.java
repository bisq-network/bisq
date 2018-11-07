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
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.OKPayValidator;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.OKPayAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.OKPayAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;

import javafx.geometry.Insets;
import javafx.geometry.VPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addLabel;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;

@Deprecated
public class OKPayForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(OKPayForm.class);

    private final OKPayAccount okPayAccount;
    private final OKPayValidator okPayValidator;
    private InputTextField accountNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.wallet"),
                ((OKPayAccountPayload) paymentAccountPayload).getAccountNr());
        return gridRow;
    }

    public OKPayForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, OKPayValidator okPayValidator,
                     InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.okPayAccount = (OKPayAccount) paymentAccount;
        this.okPayValidator = okPayValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        accountNrInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.wallet"));
        accountNrInputTextField.setValidator(okPayValidator);
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            okPayAccount.setAccountNr(newValue);
            updateFromInputs();
        });

        addCurrenciesGrid(true);
        addLimitations();
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    private void addCurrenciesGrid(boolean isEditable) {
        Label label = addLabel(gridPane, ++gridRow, Res.get("payment.supportedCurrencies"), 0);
        GridPane.setValignment(label, VPos.TOP);
        FlowPane flowPane = new FlowPane();
        flowPane.setPadding(new Insets(10, 10, 10, 10));
        flowPane.setVgap(10);
        flowPane.setHgap(10);

        if (isEditable)
            flowPane.setId("flow-pane-checkboxes-bg");
        else
            flowPane.setId("flow-pane-checkboxes-non-editable-bg");

        CurrencyUtil.getAllOKPayCurrencies().stream().forEach(e ->
                fillUpFlowPaneWithCurrencies(isEditable, flowPane, e, okPayAccount));

        GridPane.setRowIndex(flowPane, gridRow);
        GridPane.setColumnIndex(flowPane, 1);
        gridPane.getChildren().add(flowPane);
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameToggleButton != null && !useCustomAccountNameToggleButton.isSelected()) {
            String accountNr = accountNrInputTextField.getText();
            accountNr = StringUtils.abbreviate(accountNr, 9);
            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(accountNr));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                okPayAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        FormBuilder.addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(okPayAccount.getPaymentMethod().getId()));
        TextField field = FormBuilder.addTopLabelTextField(gridPane, ++gridRow, Res.get("payment.wallet"),
                okPayAccount.getAccountNr()).second;
        field.setMouseTransparent(false);
        addLimitations();
        addCurrenciesGrid(false);
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && okPayValidator.validate(okPayAccount.getAccountNr()).isValid
                && okPayAccount.getTradeCurrencies().size() > 0);
    }

}
