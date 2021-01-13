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

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AmazonGiftCardAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.AmazonGiftCardAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;

@Slf4j
public class AmazonGiftCardForm extends PaymentMethodForm {
    private InputTextField accountNrInputTextField;
    private final AmazonGiftCardAccount amazonGiftCardAccount;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        FormBuilder.addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.email.mobile"),
                ((AmazonGiftCardAccountPayload) paymentAccountPayload).getEmailOrMobileNr());
        return gridRow;
    }

    public AmazonGiftCardForm(PaymentAccount paymentAccount,
                              AccountAgeWitnessService accountAgeWitnessService,
                              InputValidator inputValidator,
                              GridPane gridPane,
                              int gridRow,
                              CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);

        this.amazonGiftCardAccount = (AmazonGiftCardAccount) paymentAccount;
    }

    public void addTradeCurrency() {
        addTradeCurrencyComboBox();
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllAmazonGiftCardCurrencies()));
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        accountNrInputTextField = addInputTextField(gridPane, ++gridRow, Res.get("payment.email.mobile"));
        accountNrInputTextField.setValidator(inputValidator);
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            amazonGiftCardAccount.setEmailOrMobileNr(newValue);
            updateFromInputs();
        });

        addTradeCurrency();

        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }


    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(accountNrInputTextField.getText());
    }

    @Override
    public void addFormForDisplayAccount() {
        addFormForAccountNumberDisplayAccount(paymentAccount.getAccountName(), paymentAccount.getPaymentMethod(),
                amazonGiftCardAccount.getEmailOrMobileNr(),
                paymentAccount.getSingleTradeCurrency());
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && inputValidator.validate(amazonGiftCardAccount.getEmailOrMobileNr()).isValid
                && paymentAccount.getTradeCurrencies().size() > 0);
    }

    private void addFormForAccountNumberDisplayAccount(String accountName,
                                                       PaymentMethod paymentMethod,
                                                       String accountNr,
                                                       TradeCurrency singleTradeCurrency) {
        gridRowFrom = gridRow;
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), accountName,
                Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(paymentMethod.getId()));
        TextField field = addCompactTopLabelTextField(gridPane, ++gridRow,
                Res.get("payment.email.mobile"), accountNr).second;
        field.setMouseTransparent(false);

        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);

        addLimitations(true);
    }
}
