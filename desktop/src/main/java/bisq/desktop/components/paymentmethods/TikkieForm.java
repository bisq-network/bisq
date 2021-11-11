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
import bisq.desktop.util.validation.IBANValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.TikkieAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.TikkieAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.layout.GridPane;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;

public class TikkieForm extends PaymentMethodForm {
    private final TikkieAccount account;
    private InputTextField ibanField;
    private final IBANValidator ibanValidator = new IBANValidator("NL");

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, 0, Res.get("payment.tikkie.iban"),
                ((TikkieAccountPayload) paymentAccountPayload).getIban());
        return gridRow;
    }

    public TikkieForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService,
                        InputValidator inputValidator, GridPane gridPane,
                        int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.account = (TikkieAccount) paymentAccount;
    }

    @Override
    public void addFormForAddAccount() {
        // this payment method is only for Netherlands/EUR
        account.setSingleTradeCurrency(new FiatCurrency("EUR"));
        CountryUtil.findCountryByCode("NL").ifPresent(c -> account.setCountry(c));

        gridRowFrom = gridRow + 1;

        ibanField = FormBuilder.addInputTextField(gridPane, ++gridRow, Res.get("payment.tikkie.iban"));
        ibanField.setValidator(ibanValidator);
        ibanField.textProperty().addListener((ov, oldValue, newValue) -> {
            account.setIban(newValue.trim());
            updateFromInputs();
        });

        addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), account.getSingleTradeCurrency().getNameAndCode());
        addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.country"), account.getCountry().name);
        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    @Override
    protected void autoFillNameTextField() {
        setAccountNameWithString(ibanField.getText());
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                account.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(account.getPaymentMethod().getId()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.tikkie.iban"), account.getIban())
                .second.setMouseTransparent(false);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), account.getSingleTradeCurrency().getNameAndCode());
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.country"), account.getCountry().name);
        addLimitations(true);
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && ibanValidator.validate(account.getIban()).isValid);
    }
}
