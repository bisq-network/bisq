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
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.GUIUtil;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.AccountNrValidator;
import bisq.desktop.util.validation.BankIdValidator;
import bisq.desktop.util.validation.BranchIdValidator;
import bisq.desktop.util.validation.NationalAccountIdValidator;

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

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.*;

abstract class BankForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(BankForm.class);

    static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        BankAccountPayload data = (BankAccountPayload) paymentAccountPayload;
        String countryCode = ((BankAccountPayload) paymentAccountPayload).getCountryCode();

        if (data.getHolderTaxId() != null) {
            final String title = Res.get("payment.account.owner") + " / " + BankUtil.getHolderIdLabelShort(countryCode);
            final String value = data.getHolderName() + " / " + data.getHolderTaxId();
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, title, value);
        } else {
            final String title = Res.get("payment.account.owner");
            final String value = data.getHolderName();
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, title, value);
        }

        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.bank.country"),
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


        boolean accountNrAccountTypeCombined = false;
        boolean nationalAccountIdAccountNrCombined = false;
        boolean bankNameBankIdCombined = false;
        boolean bankIdBranchIdCombined = false;
        boolean bankNameBranchIdCombined = false;
        boolean branchIdAccountNrCombined = false;
        if (nrRows > 2) {
            // Try combine AccountNr + AccountType
            accountNrAccountTypeCombined = BankUtil.isAccountNrRequired(countryCode) &&
                    BankUtil.isAccountTypeRequired(countryCode);
            if (accountNrAccountTypeCombined)
                nrRows--;

            if (nrRows > 2) {

                nationalAccountIdAccountNrCombined = BankUtil.isAccountNrRequired(countryCode) &&
                        BankUtil.isNationalAccountIdRequired(countryCode);

                if (nationalAccountIdAccountNrCombined)
                    nrRows--;

                if (nrRows > 2) {
                    // Next we try BankName + BankId
                    bankNameBankIdCombined = BankUtil.isBankNameRequired(countryCode) && BankUtil.isBankIdRequired(countryCode);
                    if (bankNameBankIdCombined)
                        nrRows--;

                    if (nrRows > 2) {
                        // Next we try BankId + BranchId
                        bankIdBranchIdCombined = !bankNameBankIdCombined && BankUtil.isBankIdRequired(countryCode) && BankUtil.isBranchIdRequired(countryCode);
                        if (bankIdBranchIdCombined)
                            nrRows--;

                        if (nrRows > 2) {
                            // Next we try BankId + BranchId
                            bankNameBranchIdCombined = !bankNameBankIdCombined && !bankIdBranchIdCombined &&
                                    BankUtil.isBankNameRequired(countryCode) && BankUtil.isBranchIdRequired(countryCode);
                            if (bankNameBranchIdCombined)
                                nrRows--;

                            if (nrRows > 2) {
                                branchIdAccountNrCombined = !bankNameBranchIdCombined && !bankIdBranchIdCombined && !accountNrAccountTypeCombined &&
                                        BankUtil.isBranchIdRequired(countryCode) && BankUtil.isAccountNrRequired(countryCode);
                                if (branchIdAccountNrCombined)
                                    nrRows--;

                                if (nrRows > 2)
                                    log.warn("We still have too many rows....");
                            }
                        }
                    }
                }
            }
        }

        if (bankNameBankIdCombined) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    bankNameLabel.substring(0, bankNameLabel.length() - 1) + " / " +
                            bankIdLabel.substring(0, bankIdLabel.length() - 1) + ":",
                    data.getBankName() + " / " + data.getBankId());
        }
        if (bankNameBranchIdCombined) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    bankNameLabel.substring(0, bankNameLabel.length() - 1) + " / " +
                            branchIdLabel.substring(0, branchIdLabel.length() - 1) + ":",
                    data.getBankName() + " / " + data.getBranchId());
        }

        if (!bankNameBankIdCombined && !bankNameBranchIdCombined && BankUtil.isBankNameRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, bankNameLabel, data.getBankName());

        if (!bankNameBankIdCombined && !bankNameBranchIdCombined &&
                !branchIdAccountNrCombined && bankIdBranchIdCombined) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    bankIdLabel.substring(0, bankIdLabel.length() - 1) + " / " +
                            branchIdLabel.substring(0, branchIdLabel.length() - 1) + ":",
                    data.getBankId() + " / " + data.getBranchId());
        }

        if (!bankNameBankIdCombined && !bankIdBranchIdCombined && BankUtil.isBankIdRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, bankIdLabel, data.getBankId());

        if (!bankNameBranchIdCombined && !bankIdBranchIdCombined && branchIdAccountNrCombined) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    branchIdLabel.substring(0, branchIdLabel.length() - 1) + " / " +
                            accountNrLabel.substring(0, accountNrLabel.length() - 1) + ":",
                    data.getBranchId() + " / " + data.getAccountNr());
        }

        if (!bankNameBranchIdCombined && !bankIdBranchIdCombined && !branchIdAccountNrCombined &&
                BankUtil.isBranchIdRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, branchIdLabel, data.getBranchId());

        if (!branchIdAccountNrCombined && accountNrAccountTypeCombined) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    accountNrLabel.substring(0, accountNrLabel.length() - 1) + " / " + accountTypeLabel,
                    data.getAccountNr() + " / " + data.getAccountType());
        }

        if (!branchIdAccountNrCombined && !accountNrAccountTypeCombined && !nationalAccountIdAccountNrCombined &&
                BankUtil.isAccountNrRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, accountNrLabel, data.getAccountNr());

        if (!accountNrAccountTypeCombined && BankUtil.isAccountTypeRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, accountTypeLabel, data.getAccountType());

        if (!branchIdAccountNrCombined && !accountNrAccountTypeCombined && nationalAccountIdAccountNrCombined)
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    nationalAccountIdLabel.substring(0, nationalAccountIdLabel.length() - 1) + " / " +
                            accountNrLabel.substring(0, accountNrLabel.length() - 1), data.getNationalAccountId() +
                            " / " + data.getAccountNr());

        return gridRow;
    }

    protected final BankAccountPayload bankAccountPayload;
    private InputTextField bankNameInputTextField, bankIdInputTextField, branchIdInputTextField, accountNrInputTextField,
            holderIdInputTextField, nationalAccountIdInputTextField;
    private Label holderIdLabel;
    protected InputTextField holderNameInputTextField;
    private Label bankIdLabel, branchIdLabel, accountNrLabel, nationalAccountIdLabel;
    private Tuple2<Label, InputTextField> bankIdTuple, accountNrTuple, branchIdTuple,
            bankNameTuple, nationalAccountIdTuple;
    private Tuple2<Label, ComboBox<String>> accountTypeTuple;
    private Label accountTypeLabel;
    private ComboBox<String> accountTypeComboBox;
    private boolean validatorsApplied;
    private boolean useHolderID;
    private boolean accountNrInputTextFieldEdited;
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

        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                paymentAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(paymentAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.country"),
                getCountryBasedPaymentAccount().getCountry() != null ? getCountryBasedPaymentAccount().getCountry().name : "");
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        addAcceptedBanksForDisplayAccount();
        addHolderNameAndIdForDisplayAccount();

        if (BankUtil.isBankNameRequired(countryCode))
            addLabelTextField(gridPane, ++gridRow, Res.get("payment.bank.name"),
                    bankAccountPayload.getBankName()).second.setMouseTransparent(false);

        if (BankUtil.isBankIdRequired(countryCode))
            addLabelTextField(gridPane, ++gridRow, BankUtil.getBankIdLabel(countryCode),
                    bankAccountPayload.getBankId()).second.setMouseTransparent(false);

        if (BankUtil.isBranchIdRequired(countryCode))
            addLabelTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(countryCode),
                    bankAccountPayload.getBranchId()).second.setMouseTransparent(false);

        if (BankUtil.isNationalAccountIdRequired(countryCode))
            addLabelTextField(gridPane, ++gridRow, BankUtil.getNationalAccountIdLabel(countryCode),
                    bankAccountPayload.getNationalAccountId()).second.setMouseTransparent(false);

        if (BankUtil.isAccountNrRequired(countryCode))
            addLabelTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(countryCode),
                    bankAccountPayload.getAccountNr()).second.setMouseTransparent(false);

        if (BankUtil.isAccountTypeRequired(countryCode))
            addLabelTextField(gridPane, ++gridRow, BankUtil.getAccountTypeLabel(countryCode),
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

        nationalAccountIdTuple = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getNationalAccountIdLabel(""));
        nationalAccountIdLabel = nationalAccountIdTuple.first;
        nationalAccountIdInputTextField = nationalAccountIdTuple.second;

        nationalAccountIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setNationalAccountId(newValue);
            updateFromInputs();

        });

        bankNameTuple = addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.bank.name"));
        bankNameInputTextField = bankNameTuple.second;

        bankNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setBankName(newValue);
            updateFromInputs();

        });

        bankIdTuple = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getBankIdLabel(""));
        bankIdLabel = bankIdTuple.first;
        bankIdInputTextField = bankIdTuple.second;
        bankIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setBankId(newValue);
            updateFromInputs();

        });

        branchIdTuple = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(""));
        branchIdLabel = branchIdTuple.first;
        branchIdInputTextField = branchIdTuple.second;
        branchIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setBranchId(newValue);
            updateFromInputs();

        });

        accountNrTuple = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(""));
        accountNrLabel = accountNrTuple.first;
        accountNrInputTextField = accountNrTuple.second;
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setAccountNr(newValue);
            updateFromInputs();

        });

        accountTypeTuple = FormBuilder.addLabelComboBox(gridPane, ++gridRow, "");
        accountTypeLabel = accountTypeTuple.first;

        accountTypeComboBox = accountTypeTuple.second;
        accountTypeComboBox.setPromptText(Res.get("payment.select.account"));
        accountTypeComboBox.setOnAction(e -> {
            if (BankUtil.isAccountTypeRequired(bankAccountPayload.getCountryCode())) {
                bankAccountPayload.setAccountType(accountTypeComboBox.getSelectionModel().getSelectedItem());
                updateFromInputs();
            }
        });

        addLimitations();
        addAccountNameTextFieldWithAutoFillCheckBox();

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

            bankIdLabel.setText(BankUtil.getBankIdLabel(countryCode));
            branchIdLabel.setText(BankUtil.getBranchIdLabel(countryCode));
            nationalAccountIdLabel.setText(BankUtil.getNationalAccountIdLabel(countryCode));
            accountNrLabel.setText(BankUtil.getAccountNrLabel(countryCode));
            accountTypeLabel.setText(BankUtil.getAccountTypeLabel(countryCode));

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

            if (BankUtil.useValidation(countryCode) && !validatorsApplied) {
                validatorsApplied = true;
                if (useHolderID)
                    holderIdInputTextField.setValidator(inputValidator);
                bankNameInputTextField.setValidator(inputValidator);
                bankIdInputTextField.setValidator(new BankIdValidator(countryCode));
                branchIdInputTextField.setValidator(new BranchIdValidator(countryCode));
                accountNrInputTextField.setValidator(new AccountNrValidator(countryCode));
                nationalAccountIdInputTextField.setValidator(new NationalAccountIdValidator(countryCode));
            } else {
                validatorsApplied = false;
                if (useHolderID)
                    holderIdInputTextField.setValidator(null);
                bankNameInputTextField.setValidator(null);
                bankIdInputTextField.setValidator(null);
                branchIdInputTextField.setValidator(null);
                accountNrInputTextField.setValidator(null);
                nationalAccountIdInputTextField.setValidator(null);
            }
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

            if (useHolderID) {
                if (!requiresHolderId)
                    holderIdInputTextField.setText("");

                holderIdInputTextField.resetValidation();
                holderIdInputTextField.setVisible(requiresHolderId);
                holderIdInputTextField.setManaged(requiresHolderId);

                holderIdLabel.setText(BankUtil.getHolderIdLabel(countryCode));
                holderIdLabel.setVisible(requiresHolderId);
                holderIdLabel.setManaged(requiresHolderId);
            }

            boolean nationalAccountIdRequired = BankUtil.isNationalAccountIdRequired(countryCode);
            nationalAccountIdTuple.first.setVisible(nationalAccountIdRequired);
            nationalAccountIdTuple.first.setManaged(nationalAccountIdRequired);
            nationalAccountIdInputTextField.setVisible(nationalAccountIdRequired);
            nationalAccountIdInputTextField.setManaged(nationalAccountIdRequired);

            boolean bankNameRequired = BankUtil.isBankNameRequired(countryCode);
            bankNameTuple.first.setVisible(bankNameRequired);
            bankNameTuple.first.setManaged(bankNameRequired);
            bankNameInputTextField.setVisible(bankNameRequired);
            bankNameInputTextField.setManaged(bankNameRequired);

            boolean bankIdRequired = BankUtil.isBankIdRequired(countryCode);
            bankIdTuple.first.setVisible(bankIdRequired);
            bankIdTuple.first.setManaged(bankIdRequired);
            bankIdInputTextField.setVisible(bankIdRequired);
            bankIdInputTextField.setManaged(bankIdRequired);

            boolean branchIdRequired = BankUtil.isBranchIdRequired(countryCode);
            branchIdTuple.first.setVisible(branchIdRequired);
            branchIdTuple.first.setManaged(branchIdRequired);
            branchIdInputTextField.setVisible(branchIdRequired);
            branchIdInputTextField.setManaged(branchIdRequired);

            boolean accountNrRequired = BankUtil.isAccountNrRequired(countryCode);
            accountNrTuple.first.setVisible(accountNrRequired);
            accountNrTuple.first.setManaged(accountNrRequired);
            accountNrInputTextField.setVisible(accountNrRequired);
            accountNrInputTextField.setManaged(accountNrRequired);

            boolean accountTypeRequired = BankUtil.isAccountTypeRequired(countryCode);
            accountTypeTuple.first.setVisible(accountTypeRequired);
            accountTypeTuple.first.setManaged(accountTypeRequired);
            accountTypeTuple.second.setVisible(accountTypeRequired);
            accountTypeTuple.second.setManaged(accountTypeRequired);

            updateFromInputs();

            onCountryChanged();
        }
    }

    private void onTradeCurrencySelected(TradeCurrency tradeCurrency) {
        FiatCurrency defaultCurrency = CurrencyUtil.getCurrencyByCountryCode(selectedCountry.code);
        if (!defaultCurrency.equals(tradeCurrency)) {
            new Popup<>().warning(Res.get("payment.foreign.currency"))
                    .actionButtonText(Res.get("shared.yes"))
                    .onAction(() -> {
                        paymentAccount.setSingleTradeCurrency(tradeCurrency);
                        autoFillNameTextField();
                    })
                    .closeButtonText(Res.get("payment.restore.default"))
                    .onClose(() -> currencyComboBox.getSelectionModel().select(defaultCurrency))
                    .show();
        } else {
            paymentAccount.setSingleTradeCurrency(tradeCurrency);
            autoFillNameTextField();
        }
    }

    private CountryBasedPaymentAccount getCountryBasedPaymentAccount() {
        return (CountryBasedPaymentAccount) this.paymentAccount;
    }

    protected void onCountryChanged() {
    }

    protected void addHolderNameAndId() {
        Tuple4<Label, InputTextField, Label, InputTextField> tuple = addLabelInputTextFieldLabelInputTextField(gridPane,
                ++gridRow, Res.getWithCol("payment.account.owner"), BankUtil.getHolderIdLabel(""));
        holderNameInputTextField = tuple.second;
        holderNameInputTextField.setMinWidth(250);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setHolderName(newValue);
            updateFromInputs();
        });
        holderNameInputTextField.minWidthProperty().bind(currencyComboBox.widthProperty());
        holderNameInputTextField.setValidator(inputValidator);

        useHolderID = true;
        holderIdLabel = tuple.third;
        holderIdLabel.setVisible(false);
        holderIdLabel.setManaged(false);

        holderIdInputTextField = tuple.forth;
        holderIdInputTextField.setVisible(false);
        holderIdInputTextField.setManaged(false);
        holderIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountPayload.setHolderTaxId(newValue);
            updateFromInputs();
        });
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String bankId = null;
            String countryCode = bankAccountPayload.getCountryCode();
            if (countryCode == null)
                countryCode = "";
            if (BankUtil.isBankIdRequired(countryCode)) {
                bankId = bankIdInputTextField.getText();
                if (bankId.length() > 9)
                    bankId = StringUtils.abbreviate(bankId, 9);
            } else if (BankUtil.isBranchIdRequired(countryCode)) {
                bankId = branchIdInputTextField.getText();
                if (bankId.length() > 9)
                    bankId = StringUtils.abbreviate(bankId, 9);
            } else if (BankUtil.isBankNameRequired(countryCode)) {
                bankId = bankNameInputTextField.getText();
                if (bankId.length() > 9)
                    bankId = StringUtils.abbreviate(bankId, 9);
            }

            String accountNr = accountNrInputTextField.getText();
            if (accountNr.length() > 9)
                accountNr = StringUtils.abbreviate(accountNr, 9);

            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            if (bankId != null && !bankId.isEmpty())
                accountNameTextField.setText(method.concat(": ").concat(bankId).concat(", ").concat(accountNr));
            else
                accountNameTextField.setText(method.concat(": ").concat(accountNr));

            if (BankUtil.isNationalAccountIdRequired(countryCode)) {
                String nationalAccountId = nationalAccountIdInputTextField.getText();

                if (countryCode.equals("AR") && nationalAccountId.length() == 22 && !accountNrInputTextFieldEdited) {
                    branchIdInputTextField.setText(nationalAccountId.substring(3, 7));
                    accountNrInputTextField.setText(nationalAccountId.substring(8, 21));
                }
            }

        }
    }

    @Override
    public void updateAllInputsValid() {
        boolean result = isAccountNameValid()
                && paymentAccount.getSingleTradeCurrency() != null
                && getCountryBasedPaymentAccount().getCountry() != null
                && holderNameInputTextField.getValidator().validate(bankAccountPayload.getHolderName()).isValid;

        String countryCode = bankAccountPayload.getCountryCode();
        if (validatorsApplied && BankUtil.useValidation(countryCode)) {
            if (BankUtil.isBankNameRequired(countryCode))
                result = result && bankNameInputTextField.getValidator().validate(bankAccountPayload.getBankName()).isValid;

            if (BankUtil.isBankIdRequired(countryCode))
                result = result && bankIdInputTextField.getValidator().validate(bankAccountPayload.getBankId()).isValid;

            if (BankUtil.isBranchIdRequired(countryCode))
                result = result && branchIdInputTextField.getValidator().validate(bankAccountPayload.getBranchId()).isValid;

            if (BankUtil.isAccountNrRequired(countryCode))
                result = result && accountNrInputTextField.getValidator().validate(bankAccountPayload.getAccountNr()).isValid;

            if (BankUtil.isAccountTypeRequired(countryCode))
                result = result && bankAccountPayload.getAccountType() != null;

            if (useHolderID && BankUtil.isHolderIdRequired(countryCode))
                result = result && holderIdInputTextField.getValidator().validate(bankAccountPayload.getHolderTaxId()).isValid;

            if (BankUtil.isNationalAccountIdRequired(countryCode))
                result = result && nationalAccountIdInputTextField.getValidator().validate(bankAccountPayload.getNationalAccountId()).isValid;
        }
        allInputsValid.set(result);
    }

    protected void addHolderNameAndIdForDisplayAccount() {
        String countryCode = bankAccountPayload.getCountryCode();
        if (BankUtil.isHolderIdRequired(countryCode)) {
            Tuple4<Label, TextField, Label, TextField> tuple = addLabelTextFieldLabelTextField(gridPane, ++gridRow,
                    Res.getWithCol("payment.account.owner"), BankUtil.getHolderIdLabel(countryCode));
            TextField holderNameTextField = tuple.second;
            holderNameTextField.setText(bankAccountPayload.getHolderName());
            holderNameTextField.setMinWidth(250);
            tuple.forth.setText(bankAccountPayload.getHolderTaxId());
        } else {
            addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"), bankAccountPayload.getHolderName());
        }
    }

    protected void addAcceptedBanksForAddAccount() {
    }

    public void addAcceptedBanksForDisplayAccount() {
    }
}
