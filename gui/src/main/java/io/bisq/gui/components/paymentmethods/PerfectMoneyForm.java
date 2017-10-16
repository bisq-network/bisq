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

import io.bisq.common.locale.FiatCurrency;
import io.bisq.common.locale.Res;
import io.bisq.common.locale.TradeCurrency;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.PerfectMoneyAccount;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.PerfectMoneyAccountPayload;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.InputValidator;
import io.bisq.gui.util.validation.PerfectMoneyValidator;
import javafx.collections.FXCollections;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bisq.gui.util.FormBuilder.*;

public class PerfectMoneyForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(PerfectMoneyForm.class);

    private final PerfectMoneyAccount perfectMoneyAccount;
    private final PerfectMoneyValidator perfectMoneyValidator;
    private InputTextField accountNrInputTextField;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.account.no"), ((PerfectMoneyAccountPayload) paymentAccountPayload).getAccountNr());
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

        accountNrInputTextField = addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.account.no")).second;
        accountNrInputTextField.setValidator(perfectMoneyValidator);
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            perfectMoneyAccount.setAccountNr(newValue);
            updateFromInputs();
        });

        addTradeCurrencyComboBox();
        currencyComboBox.setItems(FXCollections.observableArrayList(new FiatCurrency("USD"), new FiatCurrency("EUR")));
        currencyComboBox.getSelectionModel().select(0);

        addLimitations();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String accountNr = accountNrInputTextField.getText();
            accountNr = StringUtils.abbreviate(accountNr, 9);
            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(accountNr));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), perfectMoneyAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"), Res.get(perfectMoneyAccount.getPaymentMethod().getId()));
        TextField field = addLabelTextField(gridPane, ++gridRow, Res.get("payment.account.no"), perfectMoneyAccount.getAccountNr()).second;
        field.setMouseTransparent(false);

        final TradeCurrency singleTradeCurrency = perfectMoneyAccount.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);

        addLimitations();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && perfectMoneyValidator.validate(perfectMoneyAccount.getAccountNr()).isValid
                && perfectMoneyAccount.getTradeCurrencies().size() > 0);
    }
}
