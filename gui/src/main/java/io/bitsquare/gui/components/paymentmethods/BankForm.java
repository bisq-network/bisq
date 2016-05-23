/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.components.paymentmethods;

import io.bitsquare.common.util.Tuple2;
import io.bitsquare.common.util.Tuple3;
import io.bitsquare.common.util.Tuple4;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.AccountNrValidator;
import io.bitsquare.gui.util.validation.BankValidator;
import io.bitsquare.gui.util.validation.BranchIdValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.*;
import io.bitsquare.payment.BankAccountContractData;
import io.bitsquare.payment.CountryBasedPaymentAccount;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
import javafx.beans.value.WeakChangeListener;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.gui.util.FormBuilder.*;

abstract class BankForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(BankForm.class);

    protected final BankAccountContractData bankAccountContractData;
    private InputTextField bankNameInputTextField, bankIdInputTextField, branchIdInputTextField, accountNrInputTextField, holderIdInputTextField;
    private TextField currencyTextField;
    private Label holderIdLabel;
    private InputTextField holderNameInputTextField;
    private Label bankIdLabel;
    private Label branchIdLabel;
    private Label accountNrLabel;
    private Tuple2<Label, InputTextField> bankIdTuple;
    private Tuple2<Label, InputTextField> accountNrTuple;
    private Tuple2<Label, InputTextField> branchIdTuple;
    private Tuple2<Label, InputTextField> bankNameTuple;
    private Tuple2<Label, ComboBox> accountTypeTuple;
    private Label accountTypeLabel;
    private ComboBox<String> accountTypeComboBox;

    static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        BankAccountContractData bankAccountContractData = (BankAccountContractData) paymentAccountContractData;
        String countryCode = ((BankAccountContractData) paymentAccountContractData).getCountryCode();

        if (bankAccountContractData.getHolderTaxId() != null)
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Account holder name / " + BankUtil.getHolderIdLabel(countryCode),
                    bankAccountContractData.getHolderName() + " / " + bankAccountContractData.getHolderTaxId());
        else
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Account holder name:", bankAccountContractData.getHolderName());

        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Country of bank:", CountryUtil.getNameAndCode(countryCode));

        String bankCodeLabel = BankUtil.getBankIdLabel(countryCode);
        String branchCodeLabel = BankUtil.getBranchIdLabel(countryCode);
        boolean branchCodeDisplayed = false;
        if (BankUtil.isBankNameRequired(countryCode) && BankUtil.isBankIdRequired(countryCode)) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Bank name / " + bankCodeLabel,
                    bankAccountContractData.getBankName() + " / " + bankAccountContractData.getBankId());
        } else if (BankUtil.isBankNameRequired(countryCode) && !BankUtil.isBankIdRequired(countryCode) && BankUtil.isBranchIdRequired(countryCode)) {
            branchCodeDisplayed = true;
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Bank name / " + branchCodeLabel,
                    bankAccountContractData.getBankName() + " / " + bankAccountContractData.getBranchId());
        } else if (BankUtil.isBankNameRequired(countryCode)) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Bank name:", bankAccountContractData.getBankName());
        } else if (BankUtil.isBankIdRequired(countryCode)) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, bankCodeLabel, bankAccountContractData.getBankId());
        }

        String accountNrLabel = BankUtil.getAccountNrLabel(countryCode);
        String accountTypeLabel = BankUtil.getAccountTypeLabel(countryCode);

        String accountTypeString = "";
        String accountTypeLabelString = "";

        if (BankUtil.isAccountTypeRequired(countryCode)) {
            accountTypeString = " (" + bankAccountContractData.getAccountType() + ")";
            accountTypeLabelString = " (" + accountTypeLabel.substring(0, accountTypeLabel.length() - 1) + "):";
        }

        if (!branchCodeDisplayed && BankUtil.isBranchIdRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, branchCodeLabel, bankAccountContractData.getBranchId());
        if (BankUtil.isAccountNrRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, accountNrLabel.substring(0, accountNrLabel.length() - 1) +
                    accountTypeLabelString, bankAccountContractData.getAccountNr() + accountTypeString);

        return gridRow;
    }

    BankForm(PaymentAccount paymentAccount, InputValidator inputValidator,
             GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.bankAccountContractData = (BankAccountContractData) paymentAccount.contractData;
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        String countryCode = bankAccountContractData.getCountryCode();

        addLabelTextField(gridPane, gridRow, "Account name:", paymentAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(paymentAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, "Country:", getCountryBasedPaymentAccount().getCountry() != null ? getCountryBasedPaymentAccount().getCountry().name : "");
        addLabelTextField(gridPane, ++gridRow, "Currency:", paymentAccount.getSingleTradeCurrency().getNameAndCode());
        addAcceptedBanksForDisplayAccount();
        addHolderNameAndIdForDisplayAccount();

        if (BankUtil.isBankNameRequired(countryCode))
            addLabelTextField(gridPane, ++gridRow, "Bank name:", bankAccountContractData.getBankName()).second.setMouseTransparent(false);

        if (BankUtil.isBankIdRequired(countryCode))
            addLabelTextField(gridPane, ++gridRow, BankUtil.getBankIdLabel(countryCode), bankAccountContractData.getBankId()).second.setMouseTransparent(false);

        if (BankUtil.isBranchIdRequired(countryCode))
            addLabelTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(countryCode), bankAccountContractData.getBranchId()).second.setMouseTransparent(false);

        if (BankUtil.isAccountNrRequired(countryCode))
            addLabelTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(countryCode), bankAccountContractData.getAccountNr()).second.setMouseTransparent(false);

        if (BankUtil.isAccountTypeRequired(countryCode))
            addLabelTextField(gridPane, ++gridRow, BankUtil.getAccountTypeLabel(countryCode), bankAccountContractData.getAccountType()).second.setMouseTransparent(false);

        addAllowedPeriod();
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        Tuple3<Label, ComboBox, ComboBox> tuple3 = addLabelComboBoxComboBox(gridPane, ++gridRow, "Country:");
        currencyTextField = addLabelTextField(gridPane, ++gridRow, "Currency:").second;
        currencyTextField.setMouseTransparent(true);

        ComboBox<Region> regionComboBox = tuple3.second;
        regionComboBox.setPromptText("Select region");
        regionComboBox.setConverter(new StringConverter<Region>() {
            @Override
            public String toString(Region region) {
                return region.name;
            }

            @Override
            public Region fromString(String s) {
                return null;
            }
        });
        regionComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllRegions()));


        ComboBox<Country> countryComboBox = tuple3.third;
        countryComboBox.setDisable(true);
        countryComboBox.setPromptText("Select country");
        countryComboBox.setConverter(new StringConverter<Country>() {
            @Override
            public String toString(Country country) {
                return country.name + " (" + country.code + ")";
            }

            @Override
            public Country fromString(String s) {
                return null;
            }
        });
        countryComboBox.setOnAction(e -> {
            Country selectedItem = countryComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                getCountryBasedPaymentAccount().setCountry(selectedItem);
                String countryCode = selectedItem.code;
                TradeCurrency currency = CurrencyUtil.getCurrencyByCountryCode(countryCode);
                paymentAccount.setSingleTradeCurrency(currency);
                currencyTextField.setText(currency.getNameAndCode());

                bankIdLabel.setText(BankUtil.getBankIdLabel(countryCode));
                branchIdLabel.setText(BankUtil.getBranchIdLabel(countryCode));
                accountNrLabel.setText(BankUtil.getAccountNrLabel(countryCode));
                accountTypeLabel.setText(BankUtil.getAccountTypeLabel(countryCode));

                bankNameInputTextField.setText("");
                bankIdInputTextField.setText("");
                branchIdInputTextField.setText("");
                accountNrInputTextField.setText("");
                accountTypeComboBox.getSelectionModel().clearSelection();
                accountTypeComboBox.setItems(FXCollections.observableArrayList(BankUtil.getAccountTypeValues(countryCode)));

                bankNameInputTextField.resetValidation();
                bankIdInputTextField.resetValidation();
                branchIdInputTextField.resetValidation();
                accountNrInputTextField.resetValidation();

                if (holderIdInputTextField != null) {
                    holderIdInputTextField.resetValidation();
                    holderIdLabel.setText(BankUtil.getHolderIdLabel(countryCode));
                    boolean requiresHolderId = BankUtil.isHolderIdRequired(countryCode);
                    if (requiresHolderId) {
                        holderNameInputTextField.minWidthProperty().unbind();
                        holderNameInputTextField.setMinWidth(300);
                    } else {
                        holderNameInputTextField.minWidthProperty().bind(currencyTextField.widthProperty());
                        holderIdInputTextField.setText("");
                    }
                    holderIdLabel.setVisible(requiresHolderId);
                    holderIdLabel.setManaged(requiresHolderId);
                    holderIdInputTextField.setVisible(requiresHolderId);
                    holderIdInputTextField.setManaged(requiresHolderId);
                }

                bankNameTuple.second.resetValidation();
                bankIdTuple.second.resetValidation();
                branchIdTuple.second.resetValidation();
                accountNrTuple.second.resetValidation();

                boolean bankNameRequired = BankUtil.isBankNameRequired(countryCode);
                bankNameTuple.first.setVisible(bankNameRequired);
                bankNameTuple.first.setManaged(bankNameRequired);
                bankNameTuple.second.setVisible(bankNameRequired);
                bankNameTuple.second.setManaged(bankNameRequired);

                boolean bankIdRequired = BankUtil.isBankIdRequired(countryCode);
                bankIdTuple.first.setVisible(bankIdRequired);
                bankIdTuple.first.setManaged(bankIdRequired);
                bankIdTuple.second.setVisible(bankIdRequired);
                bankIdTuple.second.setManaged(bankIdRequired);

                boolean branchIdRequired = BankUtil.isBranchIdRequired(countryCode);
                branchIdTuple.first.setVisible(branchIdRequired);
                branchIdTuple.first.setManaged(branchIdRequired);
                branchIdTuple.second.setVisible(branchIdRequired);
                branchIdTuple.second.setManaged(branchIdRequired);
                ((BankValidator) branchIdTuple.second.getValidator()).setCountryCode(bankAccountContractData.getCountryCode());

                boolean accountNrRequired = BankUtil.isAccountNrRequired(countryCode);
                accountNrTuple.first.setVisible(accountNrRequired);
                accountNrTuple.first.setManaged(accountNrRequired);
                accountNrTuple.second.setVisible(accountNrRequired);
                accountNrTuple.second.setManaged(accountNrRequired);
                ((BankValidator) accountNrTuple.second.getValidator()).setCountryCode(bankAccountContractData.getCountryCode());

                boolean accountTypeRequired = BankUtil.isAccountTypeRequired(countryCode);
                accountTypeTuple.first.setVisible(accountTypeRequired);
                accountTypeTuple.first.setManaged(accountTypeRequired);
                accountTypeTuple.second.setVisible(accountTypeRequired);
                accountTypeTuple.second.setManaged(accountTypeRequired);

                updateFromInputs();

                onCountryChanged();
            }
        });

        regionComboBox.setOnAction(e -> {
            Region selectedItem = regionComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                countryComboBox.setDisable(false);
                countryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllCountriesForRegion(selectedItem)));
            }
        });

        addAcceptedBanksForAddAccount();

        addHolderNameAndId();

        bankNameTuple = addLabelInputTextField(gridPane, ++gridRow, "Bank name:");
        bankNameInputTextField = bankNameTuple.second;
        bankNameInputTextField.setValidator(inputValidator);
        bankNameInputTextField.textProperty().addListener(new WeakChangeListener<>((ov, oldValue, newValue) -> {
            bankAccountContractData.setBankName(newValue);
            updateFromInputs();

        }));

        bankIdTuple = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getBankIdLabel(""));
        bankIdLabel = bankIdTuple.first;
        bankIdInputTextField = bankIdTuple.second;
        bankIdInputTextField.setValidator(inputValidator);
        bankIdInputTextField.textProperty().addListener(new WeakChangeListener<>((ov, oldValue, newValue) -> {
            bankAccountContractData.setBankId(newValue);
            updateFromInputs();

        }));

        branchIdTuple = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(""));
        branchIdLabel = branchIdTuple.first;
        branchIdInputTextField = branchIdTuple.second;
        branchIdInputTextField.setValidator(new BranchIdValidator());
        branchIdInputTextField.textProperty().addListener(new WeakChangeListener<>((ov, oldValue, newValue) -> {
            bankAccountContractData.setBranchId(newValue);
            updateFromInputs();

        }));

        accountNrTuple = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(""));
        accountNrLabel = accountNrTuple.first;
        accountNrInputTextField = accountNrTuple.second;
        accountNrInputTextField.setValidator(new AccountNrValidator());
        accountNrInputTextField.textProperty().addListener(new WeakChangeListener<>((ov, oldValue, newValue) -> {
            bankAccountContractData.setAccountNr(newValue);
            updateFromInputs();

        }));


        accountTypeTuple = addLabelComboBox(gridPane, ++gridRow, "");
        accountTypeLabel = accountTypeTuple.first;
        accountTypeComboBox = accountTypeTuple.second;
        accountTypeComboBox.setPromptText("Select account type");
        accountTypeComboBox.setOnAction(e -> {
            if (BankUtil.isAccountTypeRequired(bankAccountContractData.getCountryCode())) {
                bankAccountContractData.setAccountType(accountTypeComboBox.getSelectionModel().getSelectedItem());
                updateFromInputs();
            }
        });

        addAllowedPeriod();
        addAccountNameTextFieldWithAutoFillCheckBox();

        updateFromInputs();
    }

    private CountryBasedPaymentAccount getCountryBasedPaymentAccount() {
        return (CountryBasedPaymentAccount) this.paymentAccount;
    }

    protected void onCountryChanged() {
    }

    protected void addHolderNameAndId() {
        Tuple4<Label, InputTextField, Label, InputTextField> tuple = addLabelInputTextFieldLabelInputTextField(gridPane, ++gridRow, "Account holder name:", BankUtil.getHolderIdLabel(""));
        holderNameInputTextField = tuple.second;
        holderNameInputTextField.setMinWidth(300);
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener(new WeakChangeListener<>((ov, oldValue, newValue) -> {
            bankAccountContractData.setHolderName(newValue);
            updateFromInputs();
        }));
        holderNameInputTextField.minWidthProperty().bind(currencyTextField.widthProperty());

        holderIdLabel = tuple.third;
        holderIdLabel.setVisible(false);
        holderIdLabel.setManaged(false);

        holderIdInputTextField = tuple.forth;
        holderIdInputTextField.setVisible(false);
        holderIdInputTextField.setManaged(false);
        holderIdInputTextField.setValidator(inputValidator);
        holderIdInputTextField.textProperty().addListener(new WeakChangeListener<>((ov, oldValue, newValue) -> {
            bankAccountContractData.setHolderTaxId(newValue);
            updateFromInputs();
        }));
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String bankId = null;
            String countryCode = bankAccountContractData.getCountryCode();
            if (countryCode == null)
                countryCode = "";
            if (BankUtil.isBankIdRequired(countryCode)) {
                bankId = bankIdInputTextField.getText();
                if (bankId.length() > 6)
                    bankId = StringUtils.abbreviate(bankId, 9);
            } else if (BankUtil.isBranchIdRequired(countryCode)) {
                bankId = branchIdInputTextField.getText();
                if (bankId.length() > 6)
                    bankId = StringUtils.abbreviate(bankId, 9);
            } else if (BankUtil.isBankNameRequired(countryCode)) {
                bankId = bankNameInputTextField.getText();
                if (bankId.length() > 6)
                    bankId = StringUtils.abbreviate(bankId, 9);
            }

            String accountNr = accountNrInputTextField.getText();
            if (accountNr.length() > 6)
                accountNr = StringUtils.abbreviate(accountNr, 9);

            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            if (bankId != null)
                accountNameTextField.setText(method.concat(", ").concat(bankId).concat(", ").concat(accountNr));
            else
                accountNameTextField.setText(method.concat(", ").concat(accountNr));
        }
    }

    @Override
    public void updateAllInputsValid() {
        boolean result = isAccountNameValid()
                && holderNameInputTextField.getValidator().validate(bankAccountContractData.getHolderName()).isValid
                && paymentAccount.getSingleTradeCurrency() != null
                && getCountryBasedPaymentAccount().getCountry() != null;

        String countryCode = bankAccountContractData.getCountryCode();
        if (BankUtil.isBankNameRequired(countryCode))
            result &= bankNameInputTextField.getValidator().validate(bankAccountContractData.getBankName()).isValid;

        if (BankUtil.isBankIdRequired(countryCode))
            result &= bankIdInputTextField.getValidator().validate(bankAccountContractData.getBankId()).isValid;

        if (BankUtil.isBranchIdRequired(countryCode))
            result &= branchIdInputTextField.getValidator().validate(bankAccountContractData.getBranchId()).isValid;

        if (BankUtil.isAccountNrRequired(countryCode))
            result &= accountNrInputTextField.getValidator().validate(bankAccountContractData.getAccountNr()).isValid;

        if (BankUtil.isAccountTypeRequired(countryCode))
            result &= bankAccountContractData.getAccountType() != null;

        if (getCountryBasedPaymentAccount().getCountry() != null &&
                BankUtil.isHolderIdRequired(getCountryBasedPaymentAccount().getCountry().code))
            result &= holderIdInputTextField.getValidator().validate(bankAccountContractData.getHolderTaxId()).isValid;

        allInputsValid.set(result);
    }

    protected void addHolderNameAndIdForDisplayAccount() {
        if (BankUtil.isHolderIdRequired(bankAccountContractData.getCountryCode())) {
            Tuple4<Label, TextField, Label, TextField> tuple = addLabelTextFieldLabelTextField(gridPane, ++gridRow,
                    "Account holder name:", BankUtil.getHolderIdLabel(bankAccountContractData.getCountryCode()));
            TextField holderNameTextField = tuple.second;
            holderNameTextField.setText(bankAccountContractData.getHolderName());
            holderNameTextField.setMinWidth(300);
            tuple.forth.setText(bankAccountContractData.getHolderTaxId());
        } else {
            addLabelTextField(gridPane, ++gridRow, "Account holder name:", bankAccountContractData.getHolderName());
        }
    }

    protected void addAcceptedBanksForAddAccount() {
    }

    public void addAcceptedBanksForDisplayAccount() {
    }
}
