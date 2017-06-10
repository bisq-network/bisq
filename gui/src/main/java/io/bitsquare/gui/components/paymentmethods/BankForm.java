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
import io.bitsquare.gui.main.MainView;
import io.bitsquare.gui.main.overlays.popups.Popup;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.AccountNrValidator;
import io.bitsquare.gui.util.validation.BankIdValidator;
import io.bitsquare.gui.util.validation.BranchIdValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.*;
import io.bitsquare.payment.BankAccountContractData;
import io.bitsquare.payment.CountryBasedPaymentAccount;
import io.bitsquare.payment.PaymentAccount;
import io.bitsquare.payment.PaymentAccountContractData;
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
    private Label holderIdLabel;
    protected InputTextField holderNameInputTextField;
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
    private boolean validatorsApplied;
    private boolean useHolderID;
    private Runnable closeHandler;
    private ComboBox<TradeCurrency> currencyComboBox;

    static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        BankAccountContractData data = (BankAccountContractData) paymentAccountContractData;
        String countryCode = ((BankAccountContractData) paymentAccountContractData).getCountryCode();

        if (data.getHolderTaxId() != null)
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Account holder name / " + BankUtil.getHolderIdLabel(countryCode),
                    data.getHolderName() + " / " + data.getHolderTaxId());
        else
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Account holder name:", data.getHolderName());

        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Country of bank:", CountryUtil.getNameAndCode(countryCode));

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

        String bankNameLabel = BankUtil.getBankNameLabel(countryCode);
        String bankIdLabel = BankUtil.getBankIdLabel(countryCode);
        String branchIdLabel = BankUtil.getBranchIdLabel(countryCode);
        String accountNrLabel = BankUtil.getAccountNrLabel(countryCode);
        String accountTypeLabel = BankUtil.getAccountTypeLabel(countryCode);


        boolean accountNrAccountTypeCombined = false;
        boolean bankNameBankIdCombined = false;
        boolean bankIdBranchIdCombined = false;
        boolean bankNameBranchIdCombined = false;
        boolean branchIdAccountNrCombined = false;
        if (nrRows > 2) {
            // Try combine AccountNr + AccountType
            accountNrAccountTypeCombined = BankUtil.isAccountNrRequired(countryCode) && BankUtil.isAccountTypeRequired(countryCode);
            if (accountNrAccountTypeCombined)
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

        if (bankNameBankIdCombined) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    bankNameLabel.substring(0, bankNameLabel.length() - 1) + " / " + bankIdLabel.substring(0, bankIdLabel.length() - 1) + ":",
                    data.getBankName() + " / " + data.getBankId());
        }
        if (bankNameBranchIdCombined) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    bankNameLabel.substring(0, bankNameLabel.length() - 1) + " / " + branchIdLabel.substring(0, branchIdLabel.length() - 1) + ":",
                    data.getBankName() + " / " + data.getBranchId());
        }

        if (!bankNameBankIdCombined && !bankNameBranchIdCombined && BankUtil.isBankNameRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, bankNameLabel, data.getBankName());

        if (!bankNameBankIdCombined && !bankNameBranchIdCombined && !branchIdAccountNrCombined && bankIdBranchIdCombined) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    bankIdLabel.substring(0, bankIdLabel.length() - 1) + " / " + branchIdLabel.substring(0, branchIdLabel.length() - 1) + ":",
                    data.getBankId() + " / " + data.getBranchId());
        }

        if (!bankNameBankIdCombined && !bankIdBranchIdCombined && BankUtil.isBankIdRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, bankIdLabel, data.getBankId());

        if (!bankNameBranchIdCombined && !bankIdBranchIdCombined && branchIdAccountNrCombined) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    branchIdLabel.substring(0, branchIdLabel.length() - 1) + " / " + accountNrLabel.substring(0, accountNrLabel.length() - 1) + ":",
                    data.getBranchId() + " / " + data.getAccountNr());
        }

        if (!bankNameBranchIdCombined && !bankIdBranchIdCombined && !branchIdAccountNrCombined && BankUtil.isBranchIdRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, branchIdLabel, data.getBranchId());

        if (!branchIdAccountNrCombined && accountNrAccountTypeCombined) {
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    accountNrLabel.substring(0, accountNrLabel.length() - 1) + " / " + accountTypeLabel,
                    data.getAccountNr() + " / " + data.getAccountType());
        }

        if (!branchIdAccountNrCombined && !accountNrAccountTypeCombined && BankUtil.isAccountNrRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, accountNrLabel, data.getAccountNr());

        if (!accountNrAccountTypeCombined && BankUtil.isAccountTypeRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, accountTypeLabel, data.getAccountType());

        return gridRow;
    }

    BankForm(PaymentAccount paymentAccount, InputValidator inputValidator,
             GridPane gridPane, int gridRow, BSFormatter formatter, Runnable closeHandler) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.closeHandler = closeHandler;
        this.bankAccountContractData = (BankAccountContractData) paymentAccount.contractData;
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        String countryCode = bankAccountContractData.getCountryCode();

        addLabelTextField(gridPane, gridRow, "Account name:", paymentAccount.getAccountName(), MainView.scale(Layout.FIRST_ROW_AND_GROUP_DISTANCE));
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
        countryComboBox.setVisibleRowCount(15);
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
                if (selectedItem.code.equals("US")) {
                    new Popup<>().information("Bank transfer with WIRE or ACH is not supported for the US because WIRE is too expensive and ACH has a high chargeback risk.\n\n" +
                            "Please use payment methods \"ClearXchange\", \"US Postal Money Order\" or \"Cash/ATM Deposit\" instead.")
                            .onClose(() -> closeHandler.run())
                            .show();
                } else {
                    getCountryBasedPaymentAccount().setCountry(selectedItem);
                    String countryCode = selectedItem.code;
                    TradeCurrency currency = CurrencyUtil.getCurrencyByCountryCode(countryCode);
                    paymentAccount.setSingleTradeCurrency(currency);
                    currencyComboBox.setDisable(false);
                    currencyComboBox.getSelectionModel().select(currency);

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

                    if (BankUtil.useValidation(countryCode) && !validatorsApplied) {
                        validatorsApplied = true;
                        if (useHolderID)
                            holderIdInputTextField.setValidator(inputValidator);
                        bankNameInputTextField.setValidator(inputValidator);
                        bankIdInputTextField.setValidator(new BankIdValidator(countryCode));
                        branchIdInputTextField.setValidator(new BranchIdValidator(countryCode));
                        accountNrInputTextField.setValidator(new AccountNrValidator(countryCode));
                    } else {
                        validatorsApplied = false;
                        if (useHolderID)
                            holderIdInputTextField.setValidator(null);
                        bankNameInputTextField.setValidator(null);
                        bankIdInputTextField.setValidator(null);
                        branchIdInputTextField.setValidator(null);
                        accountNrInputTextField.setValidator(null);
                    }
                    holderNameInputTextField.resetValidation();
                    bankNameInputTextField.resetValidation();
                    bankIdInputTextField.resetValidation();
                    branchIdInputTextField.resetValidation();
                    accountNrInputTextField.resetValidation();

                    boolean requiresHolderId = BankUtil.isHolderIdRequired(countryCode);
                    if (requiresHolderId) {
                        holderNameInputTextField.minWidthProperty().unbind();
                        holderNameInputTextField.setMinWidth(MainView.scale(300));
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
        });

        regionComboBox.setOnAction(e -> {
            Region selectedItem = regionComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                countryComboBox.setDisable(false);
                countryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllCountriesForRegion(selectedItem)));
            }
        });

        currencyComboBox = addLabelComboBox(gridPane, ++gridRow, "Currency:").second;
        currencyComboBox.setPromptText("Select currency");
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllSortedFiatCurrencies()));
        currencyComboBox.setOnAction(e -> {
            TradeCurrency selectedItem = currencyComboBox.getSelectionModel().getSelectedItem();
            FiatCurrency defaultCurrency = CurrencyUtil.getCurrencyByCountryCode(countryComboBox.getSelectionModel().getSelectedItem().code);
            if (!defaultCurrency.equals(selectedItem)) {
                new Popup<>().warning("Are you sure you want to choose a currency other than the country's default currency?")
                        .actionButtonText("Yes")
                        .onAction(() -> {
                            paymentAccount.setSingleTradeCurrency(selectedItem);
                            autoFillNameTextField();
                        })
                        .closeButtonText("No, restore default currency")
                        .onClose(() -> currencyComboBox.getSelectionModel().select(defaultCurrency))
                        .show();
            } else {
                paymentAccount.setSingleTradeCurrency(selectedItem);
                autoFillNameTextField();
            }
        });
        currencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency currency) {
                return currency.getNameAndCode();
            }

            @Override
            public TradeCurrency fromString(String string) {
                return null;
            }
        });
        currencyComboBox.setDisable(true);

        addAcceptedBanksForAddAccount();

        addHolderNameAndId();

        bankNameTuple = addLabelInputTextField(gridPane, ++gridRow, "Bank name:");
        bankNameInputTextField = bankNameTuple.second;

        bankNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountContractData.setBankName(newValue);
            updateFromInputs();

        });

        bankIdTuple = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getBankIdLabel(""));
        bankIdLabel = bankIdTuple.first;
        bankIdInputTextField = bankIdTuple.second;
        bankIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountContractData.setBankId(newValue);
            updateFromInputs();

        });

        branchIdTuple = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(""));
        branchIdLabel = branchIdTuple.first;
        branchIdInputTextField = branchIdTuple.second;
        branchIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountContractData.setBranchId(newValue);
            updateFromInputs();

        });

        accountNrTuple = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(""));
        accountNrLabel = accountNrTuple.first;
        accountNrInputTextField = accountNrTuple.second;
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountContractData.setAccountNr(newValue);
            updateFromInputs();

        });

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
        holderNameInputTextField.setMinWidth(MainView.scale(300));
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountContractData.setHolderName(newValue);
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
            bankAccountContractData.setHolderTaxId(newValue);
            updateFromInputs();
        });
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

            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            if (bankId != null && !bankId.isEmpty())
                accountNameTextField.setText(method.concat(": ").concat(bankId).concat(", ").concat(accountNr));
            else
                accountNameTextField.setText(method.concat(": ").concat(accountNr));
        }
    }

    @Override
    public void updateAllInputsValid() {
        boolean result = isAccountNameValid()
                && paymentAccount.getSingleTradeCurrency() != null
                && getCountryBasedPaymentAccount().getCountry() != null
                && holderNameInputTextField.getValidator().validate(bankAccountContractData.getHolderName()).isValid;

        String countryCode = bankAccountContractData.getCountryCode();
        if (validatorsApplied && BankUtil.useValidation(countryCode)) {
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

            if (useHolderID && BankUtil.isHolderIdRequired(countryCode))
                result &= holderIdInputTextField.getValidator().validate(bankAccountContractData.getHolderTaxId()).isValid;
        }
        allInputsValid.set(result);
    }

    protected void addHolderNameAndIdForDisplayAccount() {
        String countryCode = bankAccountContractData.getCountryCode();
        if (BankUtil.isHolderIdRequired(countryCode)) {
            Tuple4<Label, TextField, Label, TextField> tuple = addLabelTextFieldLabelTextField(gridPane, ++gridRow,
                    "Account holder name:", BankUtil.getHolderIdLabel(countryCode));
            TextField holderNameTextField = tuple.second;
            holderNameTextField.setText(bankAccountContractData.getHolderName());
            holderNameTextField.setMinWidth(MainView.scale(300));
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
