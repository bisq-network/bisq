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
import bisq.desktop.util.validation.PerfectMoneyValidator;

import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.PerfectMoneyAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.PerfectMoneyAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;

import static bisq.desktop.util.FormBuilder.addTopLabelTextFieldWithCopyIcon;

public class PerfectMoneyForm extends PaymentMethodForm {

    private final PerfectMoneyAccount perfectMoneyAccount;
    private final PerfectMoneyValidator perfectMoneyValidator;
    private InputTextField accountNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.account.no"), ((PerfectMoneyAccountPayload) paymentAccountPayload).getAccountNr());
        return gridRow;
    }

    public PerfectMoneyForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, PerfectMoneyValidator perfectMoneyValidator, InputValidator inputValidator, GridPane gridPane, int
            gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.perfectMoneyAccount = (PerfectMoneyAccount) paymentAccount;
        this.perfectMoneyValidator = perfectMoneyValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        accountNrInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.account.no"));
        accountNrInputTextField.setValidator(perfectMoneyValidator);
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            perfectMoneyAccount.setAccountNr(newValue);
            updateFromInputs();
        });

        addTradeCurrencyComboBox();
        currencyComboBox.setItems(FXCollections.observableArrayList(new FiatCurrency("USD"), new FiatCurrency("EUR")));
        currencyComboBox.getSelectionModel().select(0);

        addLimitations();
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(accountNrInputTextField.getText());
    }

    @Override
    public void addFormForDisplayAccount() {
        addFormForAccountNumberDisplayAccount(perfectMoneyAccount.getAccountName(),
                perfectMoneyAccount.getPaymentMethod(), perfectMoneyAccount.getAccountNr(),
                perfectMoneyAccount.getSingleTradeCurrency());
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && perfectMoneyValidator.validate(perfectMoneyAccount.getAccountNr()).isValid
                && perfectMoneyAccount.getTradeCurrencies().size() > 0);
    }
}
