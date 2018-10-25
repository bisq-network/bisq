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
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;

import bisq.core.locale.BankUtil;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.BankAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple4;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.addInputTextField;
import static bisq.desktop.util.FormBuilder.addInputTextFieldInputTextField;
import static bisq.desktop.util.FormBuilder.addLabelTextFieldLabelTextField;
import static bisq.desktop.util.FormBuilder.addTopLabelTextFieldWithCopyIcon;

abstract class BankForm extends GeneralBankForm {
    private static final Logger log = LoggerFactory.getLogger(BankForm.class);

    static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        BankAccountPayload data = (BankAccountPayload) paymentAccountPayload;
        String countryCode = ((BankAccountPayload) paymentAccountPayload).getCountryCode();

        if (data.getHolderTaxId() != null) {
            final String title = Res.get("payment.account.owner") + " / " + BankUtil.getHolderIdLabelShort(countryCode);
            final String value = data.getHolderName() + " / " + data.getHolderTaxId();
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, title, value);
        } else {
            final String title = Res.get("payment.account.owner");
            final String value = data.getHolderName();
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, title, value);
        }

        addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.bank.country"),
                CountryUtil.getNameAndCode(countryCode));

        // We don't want to display more than 6 rows to avoid scrolling, so if we get too many fields we combine them horizontally
        int nrRows = 0;
        if (BankUtil.isBankNameRequired(countryCode))
            nrRows++;
        if (BankUtil.isBankIdRequired(countryCode))
            nrRows++;
        if (BankUtil.isBranchIdRequired(countryCode))
            nrRows++;
        if (BankUtil.isAccountNrRequired(countryCode))
            nrRows++;
        if (BankUtil.isAccountTypeRequired(countryCode))
            nrRows++;
        if (BankUtil.isNationalAccountIdRequired(countryCode))
            nrRows++;

        String bankNameLabel = BankUtil.getBankNameLabel(countryCode);
        String bankIdLabel = BankUtil.getBankIdLabel(countryCode);
        String branchIdLabel = BankUtil.getBranchIdLabel(countryCode);
        String nationalAccountIdLabel = BankUtil.getNationalAccountIdLabel(countryCode);
        String accountNrLabel = BankUtil.getAccountNrLabel(countryCode);
        String accountTypeLabel = BankUtil.getAccountTypeLabel(countryCode);


        accountNrAccountTypeCombined = false;
        nationalAccountIdAccountNrCombined = false;
        bankNameBankIdCombined = false;
        bankIdBranchIdCombined = false;
        bankNameBranchIdCombined = false;
        branchIdAccountNrCombined = false;

        prepareFormLayoutFlags(countryCode, nrRows);

        if (bankNameBankIdCombined) {
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    bankNameLabel.substring(0, bankNameLabel.length() - 1) + " / " +
                            bankIdLabel.substring(0, bankIdLabel.length() - 1) + ":",
                    data.getBankName() + " / " + data.getBankId());
        }
        if (bankNameBranchIdCombined) {
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    bankNameLabel.substring(0, bankNameLabel.length() - 1) + " / " +
                            branchIdLabel.substring(0, branchIdLabel.length() - 1) + ":",
                    data.getBankName() + " / " + data.getBranchId());
        }

        if (!bankNameBankIdCombined && !bankNameBranchIdCombined && BankUtil.isBankNameRequired(countryCode))
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, bankNameLabel, data.getBankName());

        if (!bankNameBankIdCombined && !bankNameBranchIdCombined &&
                !branchIdAccountNrCombined && bankIdBranchIdCombined) {
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    bankIdLabel.substring(0, bankIdLabel.length() - 1) + " / " +
                            branchIdLabel.substring(0, branchIdLabel.length() - 1) + ":",
                    data.getBankId() + " / " + data.getBranchId());
        }

        if (!bankNameBankIdCombined && !bankIdBranchIdCombined && BankUtil.isBankIdRequired(countryCode))
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, bankIdLabel, data.getBankId());

        if (!bankNameBranchIdCombined && !bankIdBranchIdCombined && branchIdAccountNrCombined) {
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    branchIdLabel.substring(0, branchIdLabel.length() - 1) + " / " +
                            accountNrLabel.substring(0, accountNrLabel.length() - 1) + ":",
                    data.getBranchId() + " / " + data.getAccountNr());
        }

        if (!bankNameBranchIdCombined && !bankIdBranchIdCombined && !branchIdAccountNrCombined &&
                BankUtil.isBranchIdRequired(countryCode))
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, branchIdLabel, data.getBranchId());

        if (!branchIdAccountNrCombined && accountNrAccountTypeCombined) {
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    accountNrLabel.substring(0, accountNrLabel.length() - 1) + " / " + accountTypeLabel,
                    data.getAccountNr() + " / " + data.getAccountType());
        }

        if (!branchIdAccountNrCombined && !accountNrAccountTypeCombined && !nationalAccountIdAccountNrCombined &&
                BankUtil.isAccountNrRequired(countryCode))
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, accountNrLabel, data.getAccountNr());

        if (!accountNrAccountTypeCombined && BankUtil.isAccountTypeRequired(countryCode))
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, accountTypeLabel, data.getAccountType());

        if (!branchIdAccountNrCombined && !accountNrAccountTypeCombined && nationalAccountIdAccountNrCombined)
            addTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    nationalAccountIdLabel.substring(0, nationalAccountIdLabel.length() - 1) + " / " +
                            accountNrLabel.substring(0, accountNrLabel.length() - 1), data.getNationalAccountId() +
                            " / " + data.getAccountNr());

        return gridRow;
    }

    protected final BankAccountPayload bankAccountPayload;
    protected InputTextField holderNameInputTextField;
    private ComboBox<String> accountTypeComboBox;
    private Country selectedCountry;

    BankForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator,
             GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.bankAccountPayload = (BankAccountPayload) paymentAccount.paymentAccountPayload;
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        String countryCode = bankAccountPayload.getCountryCode();

        FormBuilder.addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                paymentAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        FormBuilder.addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(paymentAccount.getPaymentMethod().getId()));
        FormBuilder.addTopLabelTextField(gridPane, ++gridRow, Res.get("payment.country"),
                getCountryBasedPaymentAccount().getCountry() != null ? getCountryBasedPaymentAccount().getCountry().name : "");
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        FormBuilder.addTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);
        addAcceptedBanksForDisplayAccount();
        addHolderNameAndIdForDisplayAccount();

        if (BankUtil.isBankNameRequired(countryCode))
            FormBuilder.addTopLabelTextField(gridPane, ++gridRow, Res.get("payment.bank.name"),
                    bankAccountPayload.getBankName()).second.setMouseTransparent(false);

        if (BankUtil.isBankIdRequired(countryCode))
            FormBuilder.addTopLabelTextField(gridPane, ++gridRow, BankUtil.getBankIdLabel(countryCode),
                    bankAccountPayload.getBankId()).second.setMouseTransparent(false);

        if (BankUtil.isBranchIdRequired(countryCode))
            FormBuilder.addTopLabelTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(countryCode),
                    bankAccountPayload.getBranchId()).second.setMouseTransparent(false);

        if (BankUtil.isNationalAccountIdRequired(countryCode))
            FormBuilder.addTopLabelTextField(gridPane, ++gridRow, BankUtil.getNationalAccountIdLabel(countryCode),
                    bankAccountPayload.getNationalAccountId()).second.setMouseTransparent(false);

        if (BankUtil.isAccountNrRequired(countryCode))
            FormBuilder.addTopLabelTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(countryCode),
                    bankAccountPayload.getAccountNr()).second.setMouseTransparent(false);

        if (BankUtil.isAccountTypeRequired(countryCode))
            FormBuilder.addTopLabelTextField(gridPane, ++gridRow, BankUtil.getAccountTypeLabel(countryCode),
                    bankAccountPayload.getAccountType()).second.setMouseTransparent(false);

        addLimitations();
    }

    @Override
    public void addFormForAddAccount() {
        accountNrInputTextFieldEdited = false;
        gridRowFrom = gridRow + 1;

        Tuple2<ComboBox<TradeCurrency>, Integer> tuple = GUIUtil.addRegionCountryTradeCurrencyComboBoxes(gridPane, gridRow, this::onCountrySelected, this::onTradeCurrencySelected);
        currencyComboBox = tuple.first;
        gridRow = tuple.second;

        addAcceptedBanksForAddAccount();

        addHolderNameAndId();

        nationalAccountIdInputTextField = addInputTextField(gridPane, ++gridRow, BankUtil.getNationalAccountIdLabel(""));

        nationalAccountIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setNationalAccountId(newValue);
            updateFromInputs();

        });

        bankNameInputTextField = addInputTextField(gridPane, ++gridRow, Res.get("payment.bank.name"));

        bankNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setBankName(newValue);
            updateFromInputs();

        });

        bankIdInputTextField = addInputTextField(gridPane, ++gridRow, BankUtil.getBankIdLabel(""));
        bankIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setBankId(newValue);
            updateFromInputs();

        });

        branchIdInputTextField = addInputTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(""));
        branchIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setBranchId(newValue);
            updateFromInputs();

        });

        accountNrInputTextField = addInputTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(""));
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setAccountNr(newValue);
            updateFromInputs();

        });

        accountTypeComboBox = FormBuilder.addComboBox(gridPane, ++gridRow, "");
        accountTypeComboBox.setPromptText(Res.get("payment.select.account"));
        accountTypeComboBox.setOnAction(e -> {
            if (BankUtil.isAccountTypeRequired(bankAccountPayload.getCountryCode())) {
                bankAccountPayload.setAccountType(accountTypeComboBox.getSelectionModel().getSelectedItem());
                updateFromInputs();
            }
        });

        addLimitations();
        addAccountNameTextFieldWithAutoFillToggleButton();

        updateFromInputs();
    }

    private void onCountrySelected(Country country) {
        selectedCountry = country;
        if (country != null) {
            getCountryBasedPaymentAccount().setCountry(country);
            String countryCode = country.code;
            TradeCurrency currency = CurrencyUtil.getCurrencyByCountryCode(countryCode);
            paymentAccount.setSingleTradeCurrency(currency);
            currencyComboBox.setDisable(false);
            currencyComboBox.getSelectionModel().select(currency);

            bankIdInputTextField.setPromptText(BankUtil.getBankIdLabel(countryCode));
            branchIdInputTextField.setPromptText(BankUtil.getBranchIdLabel(countryCode));
            nationalAccountIdInputTextField.setPromptText(BankUtil.getNationalAccountIdLabel(countryCode));
            accountNrInputTextField.setPromptText(BankUtil.getAccountNrLabel(countryCode));
            accountTypeComboBox.setPromptText(BankUtil.getAccountTypeLabel(countryCode));

            bankNameInputTextField.setText("");
            bankIdInputTextField.setText("");
            branchIdInputTextField.setText("");
            nationalAccountIdInputTextField.setText("");
            accountNrInputTextField.setText("");
            accountNrInputTextField.focusedProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue) accountNrInputTextFieldEdited = true;
            });
            accountTypeComboBox.getSelectionModel().clearSelection();
            accountTypeComboBox.setItems(FXCollections.observableArrayList(BankUtil.getAccountTypeValues(countryCode)));

            validateInput(countryCode);

            holderNameInputTextField.resetValidation();
            bankNameInputTextField.resetValidation();
            bankIdInputTextField.resetValidation();
            branchIdInputTextField.resetValidation();
            accountNrInputTextField.resetValidation();
            nationalAccountIdInputTextField.resetValidation();

            boolean requiresHolderId = BankUtil.isHolderIdRequired(countryCode);
            if (requiresHolderId) {
                holderNameInputTextField.minWidthProperty().unbind();
                holderNameInputTextField.setMinWidth(250);
            } else {
                holderNameInputTextField.minWidthProperty().bind(currencyComboBox.widthProperty());
            }

            updateHolderIDInput(countryCode, requiresHolderId);

            boolean nationalAccountIdRequired = BankUtil.isNationalAccountIdRequired(countryCode);
            nationalAccountIdInputTextField.setVisible(nationalAccountIdRequired);
            nationalAccountIdInputTextField.setManaged(nationalAccountIdRequired);

            boolean bankNameRequired = BankUtil.isBankNameRequired(countryCode);
            bankNameInputTextField.setVisible(bankNameRequired);
            bankNameInputTextField.setManaged(bankNameRequired);

            boolean bankIdRequired = BankUtil.isBankIdRequired(countryCode);
            bankIdInputTextField.setVisible(bankIdRequired);
            bankIdInputTextField.setManaged(bankIdRequired);

            boolean branchIdRequired = BankUtil.isBranchIdRequired(countryCode);
            branchIdInputTextField.setVisible(branchIdRequired);
            branchIdInputTextField.setManaged(branchIdRequired);

            boolean accountNrRequired = BankUtil.isAccountNrRequired(countryCode);
            accountNrInputTextField.setVisible(accountNrRequired);
            accountNrInputTextField.setManaged(accountNrRequired);

            boolean accountTypeRequired = BankUtil.isAccountTypeRequired(countryCode);

            updateFromInputs();

            onCountryChanged();
        }
    }

    private void onTradeCurrencySelected(TradeCurrency tradeCurrency) {
        FiatCurrency defaultCurrency = CurrencyUtil.getCurrencyByCountryCode(selectedCountry.code);
        applyTradeCurrency(tradeCurrency, defaultCurrency);
    }

    private CountryBasedPaymentAccount getCountryBasedPaymentAccount() {
        return (CountryBasedPaymentAccount) this.paymentAccount;
    }

    protected void onCountryChanged() {
    }

    protected void addHolderNameAndId() {
        Tuple2<InputTextField, InputTextField> tuple = addInputTextFieldInputTextField(gridPane,
                ++gridRow, Res.get("payment.account.owner"), BankUtil.getHolderIdLabel(""));
        holderNameInputTextField = tuple.second;
        holderNameInputTextField.setMinWidth(250);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setHolderName(newValue);
            updateFromInputs();
        });
        holderNameInputTextField.minWidthProperty().bind(currencyComboBox.widthProperty());
        holderNameInputTextField.setValidator(inputValidator);

        useHolderID = true;

        holderIdInputTextField = tuple.second;
        holderIdInputTextField.setVisible(false);
        holderIdInputTextField.setManaged(false);
        holderIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setHolderTaxId(newValue);
            updateFromInputs();
        });
    }

    @Override
    protected void autoFillNameTextField() {
        autoFillAccountTextFields(bankAccountPayload);
    }

    @Override
    public void updateAllInputsValid() {
        boolean result = isAccountNameValid()
                && paymentAccount.getSingleTradeCurrency() != null
                && getCountryBasedPaymentAccount().getCountry() != null
                && holderNameInputTextField.getValidator().validate(bankAccountPayload.getHolderName()).isValid;

        String countryCode = bankAccountPayload.getCountryCode();
        result = getValidationResult(result, countryCode,
                bankAccountPayload.getBankName(),
                bankAccountPayload.getBankId(),
                bankAccountPayload.getBranchId(),
                bankAccountPayload.getAccountNr(),
                bankAccountPayload.getAccountType(),
                bankAccountPayload.getHolderTaxId(),
                bankAccountPayload.getNationalAccountId());
        allInputsValid.set(result);
    }

    protected void addHolderNameAndIdForDisplayAccount() {
        String countryCode = bankAccountPayload.getCountryCode();
        if (BankUtil.isHolderIdRequired(countryCode)) {
            Tuple4<Label, TextField, Label, TextField> tuple = addLabelTextFieldLabelTextField(gridPane, ++gridRow,
                    Res.get("payment.account.owner"), BankUtil.getHolderIdLabel(countryCode));
            TextField holderNameTextField = tuple.second;
            holderNameTextField.setText(bankAccountPayload.getHolderName());
            holderNameTextField.setMinWidth(250);
            tuple.forth.setText(bankAccountPayload.getHolderTaxId());
        } else {
            FormBuilder.addTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner"), bankAccountPayload.getHolderName());
        }
    }

    protected void addAcceptedBanksForAddAccount() {
    }

    public void addAcceptedBanksForDisplayAccount() {
    }
}
