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
import io.bitsquare.gui.util.Layout;
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
    private TextField currencyTextField;
    private Label holderIdLabel;
    private InputTextField holderNameInputTextField;
    private Label bankIdLabel;
    private Label branchIdLabel;
    private Label accountNrLabel;

    static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        BankAccountContractData bankAccountContractData = (BankAccountContractData) paymentAccountContractData;
        if (bankAccountContractData.getHolderTaxId() != null)
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Account holder name / " + bankAccountContractData.getHolderIdLabel(),
                    bankAccountContractData.getHolderName() + " / " + bankAccountContractData.getHolderTaxId());
        else
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Account holder name:", bankAccountContractData.getHolderName());

        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Country of bank:", CountryUtil.getNameAndCode(bankAccountContractData.getCountryCode()));
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Bank name / number:", bankAccountContractData.getBankName() + " / " + bankAccountContractData.getBankId());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Branch number / Account number:", bankAccountContractData.getBranchId() + " / " + bankAccountContractData.getAccountNr());
        return gridRow;
    }

    BankForm(PaymentAccount paymentAccount, InputValidator inputValidator,
             GridPane gridPane, int gridRow) {
        super(paymentAccount, inputValidator, gridPane, gridRow);
        this.bankAccountContractData = (BankAccountContractData) paymentAccount.contractData;
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
                this.paymentAccount.setSingleTradeCurrency(currency);
                currencyTextField.setText(currency.getNameAndCode());

                bankIdLabel.setText(BankUtil.getBankCodeLabel(bankAccountContractData.getCountryCode()));
                branchIdLabel.setText(BankUtil.getBranchCodeLabel(bankAccountContractData.getCountryCode()));
                accountNrLabel.setText(BankUtil.getAccountNrLabel(bankAccountContractData.getCountryCode()));

                if (holderIdInputTextField != null) {
                    boolean requiresHolderId = BankUtil.requiresHolderId(countryCode);
                    if (requiresHolderId) {
                        holderNameInputTextField.minWidthProperty().unbind();
                        holderNameInputTextField.setMinWidth(300);
                    } else {
                        holderNameInputTextField.minWidthProperty().bind(currencyTextField.widthProperty());
                    }
                    holderIdLabel.setText(BankUtil.getHolderIdLabel(countryCode));
                    holderIdLabel.setVisible(requiresHolderId);
                    holderIdLabel.setManaged(requiresHolderId);
                    holderIdInputTextField.resetValidation();
                    holderIdInputTextField.setVisible(requiresHolderId);
                    holderIdInputTextField.setManaged(requiresHolderId);
                    if (!requiresHolderId)
                        holderIdInputTextField.setText("");
                }

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

        bankNameInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Bank name:").second;
        bankNameInputTextField.setValidator(inputValidator);
        bankNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountContractData.setBankName(newValue);
            updateFromInputs();

        });

        Tuple2<Label, InputTextField> tuple2 = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getBankCodeLabel(""));
        bankIdLabel = tuple2.first;
        bankIdInputTextField = tuple2.second;
        bankIdInputTextField.setValidator(inputValidator);
        bankIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountContractData.setBankId(newValue);
            updateFromInputs();

        });

        tuple2 = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getBranchCodeLabel(""));
        branchIdLabel = tuple2.first;

        branchIdInputTextField = tuple2.second;
        branchIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountContractData.setBranchId(newValue);
            updateFromInputs();

        });

        tuple2 = addLabelInputTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(""));
        accountNrLabel = tuple2.first;

        accountNrInputTextField = tuple2.second;
        accountNrInputTextField.setValidator(inputValidator);
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountContractData.setAccountNr(newValue);
            updateFromInputs();

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
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountContractData.setHolderName(newValue);
            updateFromInputs();
        });
        holderNameInputTextField.minWidthProperty().bind(currencyTextField.widthProperty());

        holderIdLabel = tuple.third;
        holderIdLabel.setVisible(false);
        holderIdLabel.setManaged(false);

        holderIdInputTextField = tuple.forth;
        holderIdInputTextField.setVisible(false);
        holderIdInputTextField.setManaged(false);
        holderIdInputTextField.setValidator(inputValidator);
        holderIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            bankAccountContractData.setHolderTaxId(newValue);
            updateFromInputs();
        });
    }

    protected void addAcceptedBanksForAddAccount() {
    }


    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String bankName = bankNameInputTextField.getText();
            if (bankName.length() > 6)
                bankName = StringUtils.abbreviate(bankName, 9);
            String accountNr = accountNrInputTextField.getText();
            if (accountNr.length() > 6)
                accountNr = StringUtils.abbreviate(accountNr, 9);

            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(", ").concat(bankName).concat(", ").concat(accountNr));
        }
    }

    @Override
    public void updateAllInputsValid() {
        boolean holderIdValid = true;
        if (getCountryBasedPaymentAccount().getCountry() != null) {
            if (BankUtil.requiresHolderId(getCountryBasedPaymentAccount().getCountry().code))
                holderIdValid = inputValidator.validate(bankAccountContractData.getHolderTaxId()).isValid;
        }

        allInputsValid.set(isAccountNameValid()
                && inputValidator.validate(bankAccountContractData.getHolderName()).isValid
                && inputValidator.validate(bankAccountContractData.getBankName()).isValid
                && inputValidator.validate(bankAccountContractData.getBankId()).isValid
                && inputValidator.validate(bankAccountContractData.getAccountNr()).isValid
                && holderIdValid
                && paymentAccount.getSingleTradeCurrency() != null
                && getCountryBasedPaymentAccount().getCountry() != null);
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;

        addLabelTextField(gridPane, gridRow, "Account name:", paymentAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(paymentAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, "Country:", getCountryBasedPaymentAccount().getCountry() != null ? getCountryBasedPaymentAccount().getCountry().name : "");
        addLabelTextField(gridPane, ++gridRow, "Currency:", paymentAccount.getSingleTradeCurrency().getNameAndCode());
        addAcceptedBanksForDisplayAccount();
        addHolderNameAndIdForDisplayAccount();
        addLabelTextField(gridPane, ++gridRow, "Bank name:", bankAccountContractData.getBankName()).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Bank number:", bankAccountContractData.getBankId()).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Branch number:", bankAccountContractData.getBranchId()).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Account number:", bankAccountContractData.getAccountNr()).second.setMouseTransparent(false);

        addAllowedPeriod();
    }

    protected void addHolderNameAndIdForDisplayAccount() {
        if (BankUtil.requiresHolderId(bankAccountContractData.getCountryCode())) {
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

    public void addAcceptedBanksForDisplayAccount() {
    }
}
