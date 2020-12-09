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
import bisq.desktop.util.validation.USPostalMoneyOrderValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.USPostalMoneyOrderAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.USPostalMoneyOrderAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;

import static bisq.desktop.util.FormBuilder.*;

public class USPostalMoneyOrderForm extends PaymentMethodForm {
    private final USPostalMoneyOrderAccount usPostalMoneyOrderAccount;
    private final USPostalMoneyOrderValidator usPostalMoneyOrderValidator;
    private TextArea postalAddressTextArea;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.account.owner"),
                ((USPostalMoneyOrderAccountPayload) paymentAccountPayload).getHolderName());
        TextArea textArea = addCompactTopLabelTextArea(gridPane, ++gridRow, Res.get("payment.postal.address"), "").second;
        textArea.setMinHeight(70);
        textArea.setEditable(false);
        textArea.setId("text-area-disabled");
        textArea.setText(((USPostalMoneyOrderAccountPayload) paymentAccountPayload).getPostalAddress());
        return gridRow;
    }

    public USPostalMoneyOrderForm(PaymentAccount paymentAccount,
                                  AccountAgeWitnessService accountAgeWitnessService, USPostalMoneyOrderValidator usPostalMoneyOrderValidator,
                                  InputValidator inputValidator, GridPane gridPane, int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.usPostalMoneyOrderAccount = (USPostalMoneyOrderAccount) paymentAccount;
        this.usPostalMoneyOrderValidator = usPostalMoneyOrderValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow,
                Res.get("payment.account.owner"));
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            usPostalMoneyOrderAccount.setHolderName(newValue);
            updateFromInputs();
        });

        postalAddressTextArea = addTopLabelTextArea(gridPane, ++gridRow,
                Res.get("payment.postal.address"), "").second;
        postalAddressTextArea.setMinHeight(70);
        //postalAddressTextArea.setValidator(usPostalMoneyOrderValidator);
        postalAddressTextArea.textProperty().addListener((ov, oldValue, newValue) -> {
            usPostalMoneyOrderAccount.setPostalAddress(newValue);
            updateFromInputs();
        });


        TradeCurrency singleTradeCurrency = usPostalMoneyOrderAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"),
                nameAndCode);
        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(postalAddressTextArea.getText());
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                usPostalMoneyOrderAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(usPostalMoneyOrderAccount.getPaymentMethod().getId()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner"),
                usPostalMoneyOrderAccount.getHolderName());
        TextArea textArea = addCompactTopLabelTextArea(gridPane, ++gridRow, Res.get("payment.postal.address"), "").second;
        textArea.setText(usPostalMoneyOrderAccount.getPostalAddress());
        textArea.setMinHeight(70);
        textArea.setEditable(false);
        TradeCurrency singleTradeCurrency = usPostalMoneyOrderAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);
        addLimitations(true);
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
