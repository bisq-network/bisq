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

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.BankUtil;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.BankAccountPayload;
import bisq.core.payment.payload.CountryBasedPaymentAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;

import javax.annotation.Nullable;

import static bisq.common.util.Utilities.cleanString;
import static bisq.desktop.util.FormBuilder.*;

public abstract class GeneralUsBankForm extends GeneralBankForm {

    protected static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload,
                                         @Nullable String accountType,
                                         String holderAddress) {
        BankAccountPayload bankAccountPayload = (BankAccountPayload) paymentAccountPayload;
        String countryCode = bankAccountPayload.getCountryCode();
        int colIndex = 1;

        addTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                Res.get("payment.account.owner"), bankAccountPayload.getHolderName(), Layout.COMPACT_FIRST_ROW_AND_GROUP_DISTANCE);

        String branchIdLabel = BankUtil.getBranchIdLabel(countryCode);
        String accountNrLabel = BankUtil.getAccountNrLabel(countryCode);
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                branchIdLabel + " / " + accountNrLabel,
                bankAccountPayload.getBranchId() + " / " + bankAccountPayload.getAccountNr());

        String bankNameLabel = BankUtil.getBankNameLabel(countryCode);
        String accountTypeLabel = accountType == null ? "" : " / " + BankUtil.getAccountTypeLabel(countryCode);
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                bankNameLabel + accountTypeLabel,
                accountType == null ? bankAccountPayload.getBankName() : bankAccountPayload.getBankName() + " / " + accountType);

        if (holderAddress.length() > 0) {
            TextArea textAddress = addCompactTopLabelTextArea(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                    Res.get("payment.account.address"), "").second;
            textAddress.setMinHeight(70);
            textAddress.setEditable(false);
            textAddress.setText(holderAddress);
        }
        return gridRow;
    }

    public GeneralUsBankForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator,
                             GridPane gridPane, int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
    }

    protected void addFormForDisplayAccount(BankAccountPayload bankAccountPayload, String holderAddress) {
        Country country = ((CountryBasedPaymentAccount) paymentAccount).getCountry();
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                paymentAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(paymentAccount.getPaymentMethod().getId()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner"), bankAccountPayload.getHolderName());
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner.address"), cleanString(holderAddress));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.bank.name"), bankAccountPayload.getBankName());
        addCompactTopLabelTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(country.code), bankAccountPayload.getBranchId());
        addCompactTopLabelTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(country.code), bankAccountPayload.getAccountNr());
        if (bankAccountPayload.getAccountType() != null) {
            addCompactTopLabelTextField(gridPane, ++gridRow, BankUtil.getAccountTypeLabel(country.code), bankAccountPayload.getAccountType());
        }
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), paymentAccount.getSingleTradeCurrency().getNameAndCode());
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.country"), country.name);
        addLimitations(true);
    }

    protected void addFormForAddAccountInternal(BankAccountPayload bankAccountPayload, String holderAddress) {
        // this payment method is only for United States/USD
        CountryUtil.findCountryByCode("US").ifPresent(c -> onCountrySelected(c));
        Country country = ((CountryBasedPaymentAccount) paymentAccount).getCountry();

        InputTextField holderNameInputTextField = addInputTextField(gridPane, ++gridRow, Res.get("payment.account.owner"));
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setHolderName(newValue);
            updateFromInputs();
        });
        holderNameInputTextField.setValidator(inputValidator);

        TextArea addressTextArea = addTopLabelTextArea(gridPane, ++gridRow,
                Res.get("payment.account.owner.address"), Res.get("payment.account.owner.address")).second;
        addressTextArea.setMinHeight(70);
        addressTextArea.textProperty().addListener((ov, oldValue, newValue) -> {
            setHolderAddress(newValue.trim());
            updateFromInputs();
        });

        bankNameInputTextField = addInputTextField(gridPane, ++gridRow, Res.get("payment.bank.name"));
        bankNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setBankName(newValue);
            updateFromInputs();
        });
        bankNameInputTextField.setValidator(inputValidator);

        branchIdInputTextField = addInputTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(country.code));
        branchIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setBranchId(newValue);
            updateFromInputs();
        });
        branchIdInputTextField.setValidator(inputValidator);

        accountNrInputTextField = addInputTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(country.code));
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setAccountNr(newValue);
            updateFromInputs();
        });
        accountNrInputTextField.setValidator(inputValidator);

        maybeAddAccountTypeCombo(bankAccountPayload, country);

        addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), paymentAccount.getSingleTradeCurrency().getNameAndCode());
        addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.country"), country.name);
        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
        this.validatorsApplied = true;
    }

    abstract protected void setHolderAddress(String holderAddress);

    abstract protected void maybeAddAccountTypeCombo(BankAccountPayload bankAccountPayload, Country country);

    protected void onCountrySelected(Country country) {
        if (country != null) {
            ((CountryBasedPaymentAccount) this.paymentAccount).setCountry(country);
            String countryCode = country.code;
            TradeCurrency currency = CurrencyUtil.getCurrencyByCountryCode(countryCode);
            paymentAccount.setSingleTradeCurrency(currency);
        }
    }

    @Override
    protected void autoFillNameTextField() {
        autoFillAccountTextFields((CountryBasedPaymentAccountPayload) paymentAccount.getPaymentAccountPayload());
    }
}
