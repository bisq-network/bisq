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

import bisq.desktop.util.validation.AdvancedCashValidator;

import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.AdvancedCashAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.AdvancedCashAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;


import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;

public class AdvancedCashForm extends GeneralAccountNumberForm {

    private final AdvancedCashAccount advancedCashAccount;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.account.no"), ((AdvancedCashAccountPayload) paymentAccountPayload).getAccountNr());
        return gridRow;
    }

    public AdvancedCashForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, AdvancedCashValidator advancedCashValidator, InputValidator inputValidator, GridPane gridPane, int
            gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, advancedCashValidator, gridPane, gridRow, formatter);
        this.advancedCashAccount = (AdvancedCashAccount) paymentAccount;
    }

    @Override
    public void addTradeCurrency() {
        addTradeCurrencyComboBox();
        currencyComboBox.setItems(FXCollections.observableArrayList(
                new FiatCurrency("USD"),
                new FiatCurrency("EUR"),
                new FiatCurrency("GBP"),
                new FiatCurrency("RUB"),
                new FiatCurrency("UAH"),
                new FiatCurrency("KZT"),
                new FiatCurrency("BRL")));
        currencyComboBox.getSelectionModel().select(0);
    }

    @Override
    void setAccountNumber(String newValue) {
        advancedCashAccount.setAccountNr(newValue);
    }

    @Override
    String getAccountNr() {
        return advancedCashAccount.getAccountNr();
    }
}
