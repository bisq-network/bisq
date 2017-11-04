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

package io.bisq.gui.components.paymentmethods;

import io.bisq.common.locale.*;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Tuple3;
import io.bisq.common.util.Tuple4;
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.CountryBasedPaymentAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.payload.BankAccountPayload;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.AccountNrValidator;
import io.bisq.gui.util.validation.BankIdValidator;
import io.bisq.gui.util.validation.BranchIdValidator;
import io.bisq.gui.util.validation.InputValidator;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bisq.gui.util.FormBuilder.*;

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
            accountNrAccountTypeCombined = BankUtil.isAccountNrRequired(countryCode) &&
                    BankUtil.isAccountTypeRequired(countryCode);
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

        if (!branchIdAccountNrCombined && !accountNrAccountTypeCombined && BankUtil.isAccountNrRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, accountNrLabel, data.getAccountNr());

        if (!accountNrAccountTypeCombined && BankUtil.isAccountTypeRequired(countryCode))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, accountTypeLabel, data.getAccountType());

        return gridRow;
    }

    protected final BankAccountPayload bankAccountPayload;
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
    private final Runnable closeHandler;
    private ComboBox<TradeCurrency> currencyComboBox;

    BankForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator,
             GridPane gridPane, int gridRow, BSFormatter formatter, Runnable closeHandler) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.closeHandler = closeHandler;
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
        gridRowFrom = gridRow + 1;

        Tuple3<Label, ComboBox, ComboBox> tuple3 = addLabelComboBoxComboBox(gridPane, ++gridRow, Res.get("payment.country"));

        //noinspection unchecked,unchecked,unchecked
        ComboBox<Region> regionComboBox = tuple3.second;
        regionComboBox.setPromptText(Res.get("payment.select.region"));
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


        //noinspection unchecked,unchecked,unchecked
        ComboBox<Country> countryComboBox = tuple3.third;
        countryComboBox.setVisibleRowCount(15);
        countryComboBox.setDisable(true);
        countryComboBox.setPromptText(Res.get("payment.select.country"));
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
                    new Popup<>().information(Res.get("payment.us.info"))
                            .onClose(closeHandler::run)
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

        //noinspection unchecked
        currencyComboBox = addLabelComboBox(gridPane, ++gridRow, Res.getWithCol("shared.currency")).second;
        currencyComboBox.setPromptText(Res.get("list.currency.select"));
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllSortedFiatCurrencies()));
        currencyComboBox.setOnAction(e -> {
            TradeCurrency selectedItem = currencyComboBox.getSelectionModel().getSelectedItem();
            FiatCurrency defaultCurrency = CurrencyUtil.getCurrencyByCountryCode(countryComboBox.getSelectionModel().getSelectedItem().code);
            if (!defaultCurrency.equals(selectedItem)) {
                new Popup<>().warning(Res.get("payment.foreign.currency"))
                        .actionButtonText(Res.get("shared.yes"))
                        .onAction(() -> {
                            paymentAccount.setSingleTradeCurrency(selectedItem);
                            autoFillNameTextField();
                        })
                        .closeButtonText(Res.get("payment.restore.default"))
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

        accountTypeTuple = addLabelComboBox(gridPane, ++gridRow, "");
        accountTypeLabel = accountTypeTuple.first;
        //noinspection unchecked
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
