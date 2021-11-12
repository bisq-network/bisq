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

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.BankUtil;
import bisq.core.locale.Country;
import bisq.core.locale.Res;
import bisq.core.payment.AchTransferAccount;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.AchTransferAccountPayload;
import bisq.core.payment.payload.BankAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.control.ComboBox;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;

import static bisq.desktop.util.FormBuilder.*;

public class AchTransferForm extends GeneralUsBankForm {

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        AchTransferAccountPayload achTransferAccountPayload = (AchTransferAccountPayload) paymentAccountPayload;
        return addFormForBuyer(gridPane, gridRow, paymentAccountPayload, achTransferAccountPayload.getAccountType(), achTransferAccountPayload.getHolderAddress());
    }

    private final AchTransferAccount achTransferAccount;

    public AchTransferForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator,
                           GridPane gridPane, int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.achTransferAccount = (AchTransferAccount) paymentAccount;
    }

    @Override
    public void addFormForDisplayAccount() {
        addFormForDisplayAccount(achTransferAccount.getPayload(), achTransferAccount.getPayload().getHolderAddress());
    }

    @Override
    public void addFormForAddAccount() {
        addFormForAddAccountInternal(achTransferAccount.getPayload(), achTransferAccount.getPayload().getHolderAddress());
    }

    @Override
    protected void setHolderAddress(String holderAddress) {
        achTransferAccount.getPayload().setHolderAddress(holderAddress);
    }

    @Override
    protected void maybeAddAccountTypeCombo(BankAccountPayload bankAccountPayload, Country country) {
        ComboBox<String> accountTypeComboBox = addComboBox(gridPane, ++gridRow, Res.get("payment.select.account"));
        accountTypeComboBox.setItems(FXCollections.observableArrayList(BankUtil.getAccountTypeValues(country.code)));
        accountTypeComboBox.setOnAction(e -> {
            if (BankUtil.isAccountTypeRequired(country.code)) {
                bankAccountPayload.setAccountType(accountTypeComboBox.getSelectionModel().getSelectedItem());
                updateFromInputs();
            }
        });
    }

    @Override
    public void updateAllInputsValid() {
        AchTransferAccountPayload achTransferAccountPayload = achTransferAccount.getPayload();
        boolean result = isAccountNameValid()
                && paymentAccount.getSingleTradeCurrency() != null
                && ((CountryBasedPaymentAccount) this.paymentAccount).getCountry() != null
                && inputValidator.validate(achTransferAccountPayload.getHolderName()).isValid
                && inputValidator.validate(achTransferAccountPayload.getHolderAddress()).isValid;

        result = getValidationResult(result,
                achTransferAccountPayload.getCountryCode(),
                achTransferAccountPayload.getBankName(),
                achTransferAccountPayload.getBankId(),
                achTransferAccountPayload.getBranchId(),
                achTransferAccountPayload.getAccountNr(),
                achTransferAccountPayload.getAccountType(),
                achTransferAccountPayload.getHolderTaxId(),
                achTransferAccountPayload.getNationalAccountId());
        allInputsValid.set(result);
    }
}
