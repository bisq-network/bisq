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
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.EmailValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.BankUtil;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.CountryBasedPaymentAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.CashDepositAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple4;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;

import static bisq.desktop.util.FormBuilder.*;

public class CashDepositForm extends GeneralBankForm {

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        CashDepositAccountPayload data = (CashDepositAccountPayload) paymentAccountPayload;
        String countryCode = data.getCountryCode();
        String requirements = data.getRequirements();
        boolean showRequirements = requirements != null && !requirements.isEmpty();

        int colIndex = 0;

        if (data.getHolderTaxId() != null)
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                    Res.get("payment.account.name.emailAndHolderId", BankUtil.getHolderIdLabel(countryCode)),
                    data.getHolderName() + " / " + data.getHolderEmail() + " / " + data.getHolderTaxId());
        else
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                    Res.get("payment.account.name.email"),
                    data.getHolderName() + " / " + data.getHolderEmail());

        if (!showRequirements)
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++), Res.getWithCol("payment.bank.country"),
                    CountryUtil.getNameAndCode(countryCode));
        else
            requirements += "\n" + Res.get("payment.bank.country") + " " + CountryUtil.getNameAndCode(countryCode);

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
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                    bankNameLabel + " / " +
                            bankIdLabel,
                    data.getBankName() + " / " + data.getBankId());
        }
        if (bankNameBranchIdCombined) {
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                    bankNameLabel + " / " +
                            branchIdLabel,
                    data.getBankName() + " / " + data.getBranchId());
        }

        if (!bankNameBankIdCombined && !bankNameBranchIdCombined && BankUtil.isBankNameRequired(countryCode))
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++), bankNameLabel, data.getBankName());

        if (!bankNameBankIdCombined && !bankNameBranchIdCombined && !branchIdAccountNrCombined && bankIdBranchIdCombined) {
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                    bankIdLabel + " / " +
                            branchIdLabel,
                    data.getBankId() + " / " + data.getBranchId());
        }

        if (!bankNameBankIdCombined && !bankIdBranchIdCombined && BankUtil.isBankIdRequired(countryCode))
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++), bankIdLabel, data.getBankId());

        if (!bankNameBranchIdCombined && !bankIdBranchIdCombined && branchIdAccountNrCombined) {
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                    branchIdLabel + " / " +
                            accountNrLabel,
                    data.getBranchId() + " / " + data.getAccountNr());
        }

        if (!bankNameBranchIdCombined && !bankIdBranchIdCombined && !branchIdAccountNrCombined &&
                BankUtil.isBranchIdRequired(countryCode))
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++), branchIdLabel, data.getBranchId());

        if (!branchIdAccountNrCombined && accountNrAccountTypeCombined) {
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                    accountNrLabel + " / " + accountTypeLabel,
                    data.getAccountNr() + " / " + data.getAccountType());
        }

        if (!branchIdAccountNrCombined && !accountNrAccountTypeCombined && !nationalAccountIdAccountNrCombined && BankUtil.isAccountNrRequired(countryCode))
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++), accountNrLabel, data.getAccountNr());

        if (!accountNrAccountTypeCombined && BankUtil.isAccountTypeRequired(countryCode))
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++), accountTypeLabel, data.getAccountType());

        if (!branchIdAccountNrCombined && !accountNrAccountTypeCombined && nationalAccountIdAccountNrCombined)
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                    nationalAccountIdLabel + " / " +
                            accountNrLabel, data.getNationalAccountId() +
                            " / " + data.getAccountNr());

        if (showRequirements) {
            TextArea textArea = addCompactTopLabelTextArea(gridPane, getIndexOfColumn(colIndex) == 0 ? ++gridRow : gridRow, getIndexOfColumn(colIndex++),
                    Res.get("payment.extras"), "").second;
            textArea.setMinHeight(45);
            textArea.setMaxHeight(45);
            textArea.setEditable(false);
            textArea.setId("text-area-disabled");
            textArea.setText(requirements);
        }

        return gridRow;
    }

    private final CashDepositAccountPayload cashDepositAccountPayload;
    private InputTextField holderNameInputTextField, emailInputTextField;
    private ComboBox<String> accountTypeComboBox;

    private final EmailValidator emailValidator;
    private Country selectedCountry;

    public CashDepositForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator,
                           GridPane gridPane, int gridRow, CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.cashDepositAccountPayload = (CashDepositAccountPayload) paymentAccount.paymentAccountPayload;

        emailValidator = new EmailValidator();
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        String countryCode = cashDepositAccountPayload.getCountryCode();

        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), paymentAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(paymentAccount.getPaymentMethod().getId()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.country"),
                getCountryBasedPaymentAccount().getCountry() != null ? getCountryBasedPaymentAccount().getCountry().name : "");
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"),
                nameAndCode);
        addHolderNameAndIdForDisplayAccount();
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                cashDepositAccountPayload.getHolderEmail());

        if (BankUtil.isBankNameRequired(countryCode))
            addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.bank.name"),
                    cashDepositAccountPayload.getBankName()).second.setMouseTransparent(false);

        if (BankUtil.isBankIdRequired(countryCode))
            addCompactTopLabelTextField(gridPane, ++gridRow, BankUtil.getBankIdLabel(countryCode),
                    cashDepositAccountPayload.getBankId()).second.setMouseTransparent(false);

        if (BankUtil.isBranchIdRequired(countryCode))
            addCompactTopLabelTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(countryCode),
                    cashDepositAccountPayload.getBranchId()).second.setMouseTransparent(false);

        if (BankUtil.isNationalAccountIdRequired(countryCode))
            addCompactTopLabelTextField(gridPane, ++gridRow, BankUtil.getNationalAccountIdLabel(countryCode),
                    cashDepositAccountPayload.getNationalAccountId()).second.setMouseTransparent(false);

        if (BankUtil.isAccountNrRequired(countryCode))
            addCompactTopLabelTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(countryCode),
                    cashDepositAccountPayload.getAccountNr()).second.setMouseTransparent(false);

        if (BankUtil.isAccountTypeRequired(countryCode))
            addCompactTopLabelTextField(gridPane, ++gridRow, BankUtil.getAccountTypeLabel(countryCode),
                    cashDepositAccountPayload.getAccountType()).second.setMouseTransparent(false);

        String requirements = cashDepositAccountPayload.getRequirements();
        boolean showRequirements = requirements != null && !requirements.isEmpty();
        if (showRequirements) {
            TextArea textArea = addCompactTopLabelTextArea(gridPane, ++gridRow, Res.get("payment.extras"), "").second;
            textArea.setMinHeight(30);
            textArea.setMaxHeight(30);
            textArea.setEditable(false);
            textArea.setId("text-area-disabled");
            textArea.setText(requirements);
        }

        addLimitations(true);
    }

    @Override
    public void addFormForAddAccount() {
        accountNrInputTextFieldEdited = false;
        gridRowFrom = gridRow + 1;

        Tuple2<ComboBox<TradeCurrency>, Integer> tuple = GUIUtil.addRegionCountryTradeCurrencyComboBoxes(gridPane, gridRow, this::onCountrySelected, this::onTradeCurrencySelected);
        currencyComboBox = tuple.first;
        gridRow = tuple.second;

        addHolderNameAndId();

        nationalAccountIdInputTextField = addInputTextField(gridPane, ++gridRow, BankUtil.getNationalAccountIdLabel(""));

        nationalAccountIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setNationalAccountId(newValue);
            updateFromInputs();

        });

        bankNameInputTextField = addInputTextField(gridPane, ++gridRow, Res.get("payment.bank.name"));

        bankNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setBankName(newValue);
            updateFromInputs();

        });

        bankIdInputTextField = addInputTextField(gridPane, ++gridRow, BankUtil.getBankIdLabel(""));
        bankIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setBankId(newValue);
            updateFromInputs();

        });

        branchIdInputTextField = addInputTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(""));
        branchIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setBranchId(newValue);
            updateFromInputs();

        });

        accountNrInputTextField = addInputTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(""));
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setAccountNr(newValue);
            updateFromInputs();

        });

        accountTypeComboBox = addComboBox(gridPane, ++gridRow, Res.get("payment.select.account"));
        accountTypeComboBox.setOnAction(e -> {
            if (BankUtil.isAccountTypeRequired(cashDepositAccountPayload.getCountryCode())) {
                cashDepositAccountPayload.setAccountType(accountTypeComboBox.getSelectionModel().getSelectedItem());
                updateFromInputs();
            }
        });

        TextArea requirementsTextArea = addTextArea(gridPane, ++gridRow, Res.get("payment.extras"));
        requirementsTextArea.setMinHeight(30);
        requirementsTextArea.setMaxHeight(90);
        requirementsTextArea.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setRequirements(newValue);
            updateFromInputs();
        });

        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();

        updateFromInputs();
    }

    private void onTradeCurrencySelected(TradeCurrency tradeCurrency) {
        FiatCurrency defaultCurrency = CurrencyUtil.getCurrencyByCountryCode(selectedCountry.code);
        applyTradeCurrency(tradeCurrency, defaultCurrency);
    }


    private void onCountrySelected(Country country) {
        selectedCountry = country;
        if (selectedCountry != null) {
            getCountryBasedPaymentAccount().setCountry(selectedCountry);
            String countryCode = selectedCountry.code;
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
            emailInputTextField.resetValidation();
            bankNameInputTextField.resetValidation();
            bankIdInputTextField.resetValidation();
            branchIdInputTextField.resetValidation();
            accountNrInputTextField.resetValidation();
            nationalAccountIdInputTextField.resetValidation();

            holderNameInputTextField.validate();
            emailInputTextField.validate();
            bankNameInputTextField.validate();
            bankIdInputTextField.validate();
            branchIdInputTextField.validate();
            accountNrInputTextField.validate();
            nationalAccountIdInputTextField.validate();

            boolean requiresHolderId = BankUtil.isHolderIdRequired(countryCode);
            if (requiresHolderId) {
                holderNameInputTextField.minWidthProperty().unbind();
                holderNameInputTextField.setMinWidth(300);
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
            accountTypeComboBox.setVisible(accountTypeRequired);
            accountTypeComboBox.setManaged(accountTypeRequired);

            updateFromInputs();

            onCountryChanged();
        }
    }

    private CountryBasedPaymentAccount getCountryBasedPaymentAccount() {
        return (CountryBasedPaymentAccount) this.paymentAccount;
    }

    private void onCountryChanged() {
    }

    private void addHolderNameAndId() {
        Tuple2<InputTextField, InputTextField> tuple = addInputTextFieldInputTextField(gridPane,
                ++gridRow, Res.get("payment.account.owner"), BankUtil.getHolderIdLabel(""));
        holderNameInputTextField = tuple.first;
        holderNameInputTextField.setMinWidth(300);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setHolderName(newValue);
            updateFromInputs();
        });
        holderNameInputTextField.minWidthProperty().bind(currencyComboBox.widthProperty());
        holderNameInputTextField.setValidator(inputValidator);

        emailInputTextField = addInputTextField(gridPane, ++gridRow, Res.get("payment.email"));
        emailInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setHolderEmail(newValue);
            updateFromInputs();
        });
        emailInputTextField.minWidthProperty().bind(currencyComboBox.widthProperty());
        emailInputTextField.setValidator(emailValidator);

        useHolderID = true;

        holderIdInputTextField = tuple.second;
        holderIdInputTextField.setMinWidth(250);
        holderIdInputTextField.setVisible(false);
        holderIdInputTextField.setManaged(false);
        holderIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setHolderTaxId(newValue);
            updateFromInputs();
        });
    }

    @Override
    protected void autoFillNameTextField() {
        autoFillAccountTextFields(cashDepositAccountPayload);
    }

    @Override
    public void updateAllInputsValid() {
        boolean result = isAccountNameValid()
                && paymentAccount.getSingleTradeCurrency() != null
                && getCountryBasedPaymentAccount().getCountry() != null
                && holderNameInputTextField.getValidator().validate(cashDepositAccountPayload.getHolderName()).isValid
                && emailInputTextField.getValidator().validate(cashDepositAccountPayload.getHolderEmail()).isValid;

        String countryCode = cashDepositAccountPayload.getCountryCode();
        result = getValidationResult(result, countryCode,
                cashDepositAccountPayload.getBankName(),
                cashDepositAccountPayload.getBankId(),
                cashDepositAccountPayload.getBranchId(),
                cashDepositAccountPayload.getAccountNr(),
                cashDepositAccountPayload.getAccountType(),
                cashDepositAccountPayload.getHolderTaxId(),
                cashDepositAccountPayload.getNationalAccountId());
        allInputsValid.set(result);
    }

    private void addHolderNameAndIdForDisplayAccount() {
        String countryCode = cashDepositAccountPayload.getCountryCode();
        if (BankUtil.isHolderIdRequired(countryCode)) {
            Tuple4<Label, TextField, Label, TextField> tuple = addCompactTopLabelTextFieldTopLabelTextField(gridPane, ++gridRow,
                    Res.get("payment.account.owner"), BankUtil.getHolderIdLabel(countryCode));
            TextField holderNameTextField = tuple.second;
            holderNameTextField.setText(cashDepositAccountPayload.getHolderName());
            holderNameTextField.setMinWidth(300);
            tuple.fourth.setText(cashDepositAccountPayload.getHolderTaxId());
        } else {
            addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner"),
                    cashDepositAccountPayload.getHolderName());
        }
    }
}
