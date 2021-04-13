/* This file is part of Bisq.
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

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.CashByMailAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.CashByMailAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import com.jfoenix.controls.JFXTextArea;

import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;

import static bisq.desktop.util.FormBuilder.*;

public class CashByMailForm extends PaymentMethodForm {
    private final CashByMailAccount cashByMailAccount;
    private TextArea postalAddressTextArea;
    private InputTextField contactField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        CashByMailAccountPayload cbm = (CashByMailAccountPayload) paymentAccountPayload;
        addTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1,
                Res.get("payment.account.owner"),
                cbm.getHolderName(),
                Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE);

        TextArea textAddress = addCompactTopLabelTextArea(gridPane, ++gridRow, Res.get("payment.postal.address"), "").second;
        textAddress.setMinHeight(70);
        textAddress.setEditable(false);
        textAddress.setText(cbm.getPostalAddress());

        TextArea textExtraInfo = addCompactTopLabelTextArea(gridPane, gridRow, 1, Res.get("payment.shared.extraInfo"), "").second;
        textExtraInfo.setMinHeight(70);
        textExtraInfo.setEditable(false);
        textExtraInfo.setText(cbm.getExtraInfo());
        return gridRow;
    }

    public CashByMailForm(PaymentAccount paymentAccount,
                                  AccountAgeWitnessService accountAgeWitnessService,
                                  InputValidator inputValidator, GridPane gridPane, int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.cashByMailAccount = (CashByMailAccount) paymentAccount;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        addTradeCurrencyComboBox();
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllSortedFiatCurrencies()));

        contactField = addInputTextField(gridPane, ++gridRow,
                Res.get("payment.cashByMail.contact"));
        contactField.setPromptText(Res.get("payment.cashByMail.contact.prompt"));
        contactField.setValidator(inputValidator);
        contactField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashByMailAccount.setContact(newValue);
            updateFromInputs();
        });

        postalAddressTextArea = addTopLabelTextArea(gridPane, ++gridRow,
                Res.get("payment.postal.address"), "").second;
        postalAddressTextArea.setMinHeight(70);
        postalAddressTextArea.textProperty().addListener((ov, oldValue, newValue) -> {
            cashByMailAccount.setPostalAddress(newValue);
            updateFromInputs();
        });

        TextArea extraTextArea = addTopLabelTextArea(gridPane, ++gridRow,
                Res.get("payment.shared.optionalExtra"), Res.get("payment.cashByMail.extraInfo.prompt")).second;
        extraTextArea.setMinHeight(70);
        ((JFXTextArea) extraTextArea).setLabelFloat(false);
        extraTextArea.textProperty().addListener((ov, oldValue, newValue) -> {
            cashByMailAccount.setExtraInfo(newValue);
            updateFromInputs();
        });

        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(contactField.getText());
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                cashByMailAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(cashByMailAccount.getPaymentMethod().getId()));

        TradeCurrency tradeCurrency = paymentAccount.getSingleTradeCurrency();
        String nameAndCode = tradeCurrency != null ? tradeCurrency.getNameAndCode() : "";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);

        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.f2f.contact"),
                cashByMailAccount.getContact());
        TextArea textArea = addCompactTopLabelTextArea(gridPane, ++gridRow, Res.get("payment.postal.address"), "").second;
        textArea.setText(cashByMailAccount.getPostalAddress());
        textArea.setMinHeight(70);
        textArea.setEditable(false);

        TextArea textAreaExtra = addCompactTopLabelTextArea(gridPane, ++gridRow, Res.get("payment.shared.extraInfo"), "").second;
        textAreaExtra.setText(cashByMailAccount.getExtraInfo());
        textAreaExtra.setMinHeight(70);
        textAreaExtra.setEditable(false);

        addLimitations(true);
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && !postalAddressTextArea.getText().isEmpty()
                && inputValidator.validate(cashByMailAccount.getContact()).isValid
                && paymentAccount.getSingleTradeCurrency() != null);
    }
}
