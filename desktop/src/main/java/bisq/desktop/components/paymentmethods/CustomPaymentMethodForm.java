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
import bisq.desktop.util.validation.CustomPaymentMethodNameValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.payment.CustomPaymentMethodAccount;
import bisq.core.payment.CustomPaymentMethodUtil;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.CustomPaymentMethodAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.desktop.components.controls.BisqJfxTextArea;

import javafx.scene.control.TextArea;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;

import static bisq.desktop.util.FormBuilder.*;

public class CustomPaymentMethodForm extends PaymentMethodForm {
    private final CustomPaymentMethodAccount customPaymentMethodAccount;
    private final CustomPaymentMethodNameValidator nameValidator = new CustomPaymentMethodNameValidator();

    // The name is taken from the offer, which both traders have agreed to. The account data is a text of the seller.
    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload, Offer offer) {
        addTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1,
                Res.get("payment.custom.name"),
                offer.getCustomPaymentMethodName(),
                Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE);

        TextArea accountDataTextArea = addCompactTopLabelTextArea(gridPane, ++gridRow,
                Res.get("payment.custom.accountData"), "").second;
        accountDataTextArea.setMinHeight(100);
        accountDataTextArea.setMaxHeight(100);
        accountDataTextArea.setEditable(false);
        accountDataTextArea.setText(((CustomPaymentMethodAccountPayload) paymentAccountPayload).getAccountData());
        GridPane.setColumnSpan(accountDataTextArea.getParent(), 2);
        return gridRow;
    }

    public CustomPaymentMethodForm(PaymentAccount paymentAccount,
                                   AccountAgeWitnessService accountAgeWitnessService,
                                   InputValidator inputValidator, GridPane gridPane, int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.customPaymentMethodAccount = (CustomPaymentMethodAccount) paymentAccount;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        addTradeCurrencyComboBox();
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllSortedFiatCurrencies()));

        InputTextField nameField = addInputTextField(gridPane, ++gridRow, Res.get("payment.custom.name"));
        nameField.setPromptText(Res.get("payment.custom.name.prompt", CustomPaymentMethodUtil.MAX_NAME_LENGTH));
        nameField.setValidator(nameValidator);
        nameField.textProperty().addListener((ov, oldValue, newValue) -> {
            customPaymentMethodAccount.setPaymentMethodName(CustomPaymentMethodUtil.cleanName(newValue));
            updateFromInputs();
        });

        TextArea accountDataTextArea = addTopLabelTextArea(gridPane, ++gridRow,
                Res.get("payment.custom.accountData"),
                Res.get("payment.custom.accountData.prompt", CustomPaymentMethodUtil.MAX_ACCOUNT_DATA_LENGTH)).second;
        accountDataTextArea.setMinHeight(100);
        ((BisqJfxTextArea) accountDataTextArea).setLabelFloat(false);
        accountDataTextArea.setTextFormatter(new TextFormatter<String>(CustomPaymentMethodForm::cutToMaxLength));
        accountDataTextArea.textProperty().addListener((ov, oldValue, newValue) -> {
            customPaymentMethodAccount.setAccountData(CustomPaymentMethodUtil.cleanAccountData(newValue));
            updateFromInputs();
        });

        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    // Text which is typed or pasted is cut, so that the account data stays within its max length
    private static TextFormatter.Change cutToMaxLength(TextFormatter.Change change) {
        int excess = change.getControlNewText().length() - CustomPaymentMethodUtil.MAX_ACCOUNT_DATA_LENGTH;
        if (change.isContentChange() && excess > 0) {
            String text = change.getText();
            change.setText(text.substring(0, Math.max(0, text.length() - excess)));
        }
        return change;
    }

    // The default account name of other payment methods shortens the given text to 9 characters. Here the full
    // name and the currency are used, as they are what tells the accounts of the custom payment method apart.
    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameToggleButton != null && !useCustomAccountNameToggleButton.isSelected()) {
            TradeCurrency tradeCurrency = paymentAccount.getSingleTradeCurrency();
            String currencyCode = tradeCurrency != null ? " (" + tradeCurrency.getCode() + ")" : "";
            accountNameTextField.setText(paymentAccount.getPaymentMethod().getShortName() + ": " +
                    customPaymentMethodAccount.getPaymentMethodName() + currencyCode);
        }
    }

    @Override
    public void addFormForEditAccount() {
        gridRowFrom = gridRow;
        addAccountNameTextFieldWithAutoFillToggleButton();
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(customPaymentMethodAccount.getPaymentMethod().getId()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.custom.name"),
                customPaymentMethodAccount.getPaymentMethodName());

        TradeCurrency tradeCurrency = paymentAccount.getSingleTradeCurrency();
        String nameAndCode = tradeCurrency != null ? tradeCurrency.getNameAndCode() : "";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);

        TextArea accountDataTextArea = addCompactTopLabelTextArea(gridPane, ++gridRow,
                Res.get("payment.custom.accountData"), "").second;
        accountDataTextArea.setText(customPaymentMethodAccount.getAccountData());
        accountDataTextArea.setMinHeight(100);
        accountDataTextArea.setMaxHeight(100);
        accountDataTextArea.setEditable(false);

        addLimitations(true);
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && nameValidator.validate(customPaymentMethodAccount.getPaymentMethodName()).isValid
                && CustomPaymentMethodUtil.isValidAccountData(customPaymentMethodAccount.getAccountData())
                && paymentAccount.getSingleTradeCurrency() != null);
    }
}
