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
import io.bisq.core.payment.payload.CashDepositAccountPayload;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.FormBuilder;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.*;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CashDepositForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(CashDepositForm.class);

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        CashDepositAccountPayload data = (CashDepositAccountPayload) paymentAccountPayload;
        String countryCode = data.getCountryCode();
        String requirements = data.getRequirements();
        boolean showRequirements = requirements != null && !requirements.isEmpty();

        if (data.getHolderTaxId() != null)
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    Res.get("payment.account.name.emailAndHolderId", BankUtil.getHolderIdLabel(countryCode)),
                    data.getHolderName() + " / " + data.getHolderEmail() + " / " + data.getHolderTaxId());
        else
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.account.name.email"),
                    data.getHolderName() + " / " + data.getHolderEmail());

        if (!showRequirements)
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.bank.country"),
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
                    bankIdBranchIdCombined = !bankNameBankIdCombined && BankUtil.isBankIdRequired(countryCode) &&
                            BankUtil.isBranchIdRequired(countryCode);
                    if (bankIdBranchIdCombined)
                        nrRows--;

                    if (nrRows > 2) {
                        // Next we try BankId + BranchId
                        bankNameBranchIdCombined = !bankNameBankIdCombined && !bankIdBranchIdCombined &&
                                BankUtil.isBankNameRequired(countryCode) && BankUtil.isBranchIdRequired(countryCode);
                        if (bankNameBranchIdCombined)
                            nrRows--;

                        if (nrRows > 2) {
                            branchIdAccountNrCombined = !bankNameBranchIdCombined && !bankIdBranchIdCombined &&
                                    !accountNrAccountTypeCombined &&
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
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    bankNameLabel.substring(0, bankNameLabel.length() - 1) + " / " +
                            bankIdLabel.substring(0, bankIdLabel.length() - 1) + ":",
                    data.getBankName() + " / " + data.getBankId());
        }
        if (bankNameBranchIdCombined) {
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    bankNameLabel.substring(0, bankNameLabel.length() - 1) + " / " +
                            branchIdLabel.substring(0, branchIdLabel.length() - 1) + ":",
                    data.getBankName() + " / " + data.getBranchId());
        }

        if (!bankNameBankIdCombined && !bankNameBranchIdCombined && BankUtil.isBankNameRequired(countryCode))
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, bankNameLabel, data.getBankName());

        if (!bankNameBankIdCombined && !bankNameBranchIdCombined && !branchIdAccountNrCombined && bankIdBranchIdCombined) {
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    bankIdLabel.substring(0, bankIdLabel.length() - 1) + " / " +
                            branchIdLabel.substring(0, branchIdLabel.length() - 1) + ":",
                    data.getBankId() + " / " + data.getBranchId());
        }

        if (!bankNameBankIdCombined && !bankIdBranchIdCombined && BankUtil.isBankIdRequired(countryCode))
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, bankIdLabel, data.getBankId());

        if (!bankNameBranchIdCombined && !bankIdBranchIdCombined && branchIdAccountNrCombined) {
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    branchIdLabel.substring(0, branchIdLabel.length() - 1) + " / " +
                            accountNrLabel.substring(0, accountNrLabel.length() - 1) + ":",
                    data.getBranchId() + " / " + data.getAccountNr());
        }

        if (!bankNameBranchIdCombined && !bankIdBranchIdCombined && !branchIdAccountNrCombined &&
                BankUtil.isBranchIdRequired(countryCode))
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, branchIdLabel, data.getBranchId());

        if (!branchIdAccountNrCombined && accountNrAccountTypeCombined) {
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                    accountNrLabel.substring(0, accountNrLabel.length() - 1) + " / " + accountTypeLabel,
                    data.getAccountNr() + " / " + data.getAccountType());
        }

        if (!branchIdAccountNrCombined && !accountNrAccountTypeCombined && BankUtil.isAccountNrRequired(countryCode))
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, accountNrLabel, data.getAccountNr());

        if (!accountNrAccountTypeCombined && BankUtil.isAccountTypeRequired(countryCode))
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, accountTypeLabel, data.getAccountType());

        if (showRequirements) {
            TextArea textArea = FormBuilder.addLabelTextArea(gridPane, ++gridRow, Res.get("payment.extras"), "").second;
            textArea.setMinHeight(45);
            textArea.setMaxHeight(45);
            textArea.setEditable(false);
            textArea.setId("text-area-disabled");
            textArea.setText(requirements);
        }

        return gridRow;
    }

    protected final CashDepositAccountPayload cashDepositAccountPayload;
    private InputTextField bankNameInputTextField, bankIdInputTextField, branchIdInputTextField, accountNrInputTextField, holderIdInputTextField;
    private Label holderIdLabel;
    protected InputTextField holderNameInputTextField, emailInputTextField;
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
    private ComboBox<TradeCurrency> currencyComboBox;
    private final EmailValidator emailValidator;


    public CashDepositForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator,
                           GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.cashDepositAccountPayload = (CashDepositAccountPayload) paymentAccount.paymentAccountPayload;

        emailValidator = new EmailValidator();
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        String countryCode = cashDepositAccountPayload.getCountryCode();

        FormBuilder.addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), paymentAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(paymentAccount.getPaymentMethod().getId()));
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.country"),
                getCountryBasedPaymentAccount().getCountry() != null ? getCountryBasedPaymentAccount().getCountry().name : "");
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"),
                nameAndCode);
        addAcceptedBanksForDisplayAccount();
        addHolderNameAndIdForDisplayAccount();
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                cashDepositAccountPayload.getHolderEmail());

        if (BankUtil.isBankNameRequired(countryCode))
            FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.bank.name"),
                    cashDepositAccountPayload.getBankName()).second.setMouseTransparent(false);

        if (BankUtil.isBankIdRequired(countryCode))
            FormBuilder.addLabelTextField(gridPane, ++gridRow, BankUtil.getBankIdLabel(countryCode),
                    cashDepositAccountPayload.getBankId()).second.setMouseTransparent(false);

        if (BankUtil.isBranchIdRequired(countryCode))
            FormBuilder.addLabelTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(countryCode),
                    cashDepositAccountPayload.getBranchId()).second.setMouseTransparent(false);

        if (BankUtil.isAccountNrRequired(countryCode))
            FormBuilder.addLabelTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(countryCode),
                    cashDepositAccountPayload.getAccountNr()).second.setMouseTransparent(false);

        if (BankUtil.isAccountTypeRequired(countryCode))
            FormBuilder.addLabelTextField(gridPane, ++gridRow, BankUtil.getAccountTypeLabel(countryCode),
                    cashDepositAccountPayload.getAccountType()).second.setMouseTransparent(false);

        String requirements = cashDepositAccountPayload.getRequirements();
        boolean showRequirements = requirements != null && !requirements.isEmpty();
        if (showRequirements) {
            TextArea textArea = FormBuilder.addLabelTextArea(gridPane, ++gridRow, Res.get("payment.extras"), "").second;
            textArea.setMinHeight(30);
            textArea.setMaxHeight(30);
            textArea.setEditable(false);
            textArea.setId("text-area-disabled");
            textArea.setText(requirements);
        }

        addLimitations();
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        Tuple3<Label, ComboBox, ComboBox> tuple3 = FormBuilder.addLabelComboBoxComboBox(gridPane, ++gridRow, Res.get("payment.country"));

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
                emailInputTextField.resetValidation();
                bankNameInputTextField.resetValidation();
                bankIdInputTextField.resetValidation();
                branchIdInputTextField.resetValidation();
                accountNrInputTextField.resetValidation();

                boolean requiresHolderId = BankUtil.isHolderIdRequired(countryCode);
                if (requiresHolderId) {
                    holderNameInputTextField.minWidthProperty().unbind();
                    holderNameInputTextField.setMinWidth(300);
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
        });

        regionComboBox.setOnAction(e -> {
            Region selectedItem = regionComboBox.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                countryComboBox.setDisable(false);
                countryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllCountriesForRegion(selectedItem)));
            }
        });

        //noinspection unchecked
        currencyComboBox = FormBuilder.addLabelComboBox(gridPane, ++gridRow, Res.getWithCol("shared.currency")).second;
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

        bankNameTuple = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.bank.name"));
        bankNameInputTextField = bankNameTuple.second;

        bankNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setBankName(newValue);
            updateFromInputs();

        });

        bankIdTuple = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, BankUtil.getBankIdLabel(""));
        bankIdLabel = bankIdTuple.first;
        bankIdInputTextField = bankIdTuple.second;
        bankIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setBankId(newValue);
            updateFromInputs();

        });

        branchIdTuple = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, BankUtil.getBranchIdLabel(""));
        branchIdLabel = branchIdTuple.first;
        branchIdInputTextField = branchIdTuple.second;
        branchIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setBranchId(newValue);
            updateFromInputs();

        });

        accountNrTuple = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, BankUtil.getAccountNrLabel(""));
        accountNrLabel = accountNrTuple.first;
        accountNrInputTextField = accountNrTuple.second;
        accountNrInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setAccountNr(newValue);
            updateFromInputs();

        });

        accountTypeTuple = FormBuilder.addLabelComboBox(gridPane, ++gridRow, "");
        accountTypeLabel = accountTypeTuple.first;
        //noinspection unchecked
        accountTypeComboBox = accountTypeTuple.second;
        accountTypeComboBox.setPromptText(Res.get("payment.select.account"));
        accountTypeComboBox.setOnAction(e -> {
            if (BankUtil.isAccountTypeRequired(cashDepositAccountPayload.getCountryCode())) {
                cashDepositAccountPayload.setAccountType(accountTypeComboBox.getSelectionModel().getSelectedItem());
                updateFromInputs();
            }
        });

        TextArea requirementsTextArea = FormBuilder.addLabelTextArea(gridPane, ++gridRow, Res.get("payment.extras"), "").second;
        requirementsTextArea.setMinHeight(30);
        requirementsTextArea.setMaxHeight(30);
        requirementsTextArea.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setRequirements(newValue);
            updateFromInputs();
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
        Tuple4<Label, InputTextField, Label, InputTextField> tuple = FormBuilder.addLabelInputTextFieldLabelInputTextField(gridPane,
                ++gridRow, Res.getWithCol("payment.account.owner"), BankUtil.getHolderIdLabel(""));
        holderNameInputTextField = tuple.second;
        holderNameInputTextField.setMinWidth(300);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setHolderName(newValue);
            updateFromInputs();
        });
        holderNameInputTextField.minWidthProperty().bind(currencyComboBox.widthProperty());
        holderNameInputTextField.setValidator(inputValidator);

        emailInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.email")).second;
        emailInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setHolderEmail(newValue);
            updateFromInputs();
        });
        emailInputTextField.minWidthProperty().bind(currencyComboBox.widthProperty());
        emailInputTextField.setValidator(emailValidator);

        useHolderID = true;
        holderIdLabel = tuple.third;
        holderIdLabel.setVisible(false);
        holderIdLabel.setManaged(false);

        holderIdInputTextField = tuple.forth;
        holderIdInputTextField.setVisible(false);
        holderIdInputTextField.setManaged(false);
        holderIdInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cashDepositAccountPayload.setHolderTaxId(newValue);
            updateFromInputs();
        });
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String bankId = null;
            String countryCode = cashDepositAccountPayload.getCountryCode();
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
                && holderNameInputTextField.getValidator().validate(cashDepositAccountPayload.getHolderName()).isValid
                && emailInputTextField.getValidator().validate(cashDepositAccountPayload.getHolderEmail()).isValid;

        String countryCode = cashDepositAccountPayload.getCountryCode();
        if (validatorsApplied && BankUtil.useValidation(countryCode)) {
            if (BankUtil.isBankNameRequired(countryCode))
                result = result && bankNameInputTextField.getValidator().validate(cashDepositAccountPayload.getBankName()).isValid;

            if (BankUtil.isBankIdRequired(countryCode))
                result = result && bankIdInputTextField.getValidator().validate(cashDepositAccountPayload.getBankId()).isValid;

            if (BankUtil.isBranchIdRequired(countryCode))
                result = result && branchIdInputTextField.getValidator().validate(cashDepositAccountPayload.getBranchId()).isValid;

            if (BankUtil.isAccountNrRequired(countryCode))
                result = result && accountNrInputTextField.getValidator().validate(cashDepositAccountPayload.getAccountNr()).isValid;

            if (BankUtil.isAccountTypeRequired(countryCode))
                result = result && cashDepositAccountPayload.getAccountType() != null;

            if (useHolderID && BankUtil.isHolderIdRequired(countryCode))
                result = result && holderIdInputTextField.getValidator().validate(cashDepositAccountPayload.getHolderTaxId()).isValid;
        }
        allInputsValid.set(result);
    }

    protected void addHolderNameAndIdForDisplayAccount() {
        String countryCode = cashDepositAccountPayload.getCountryCode();
        if (BankUtil.isHolderIdRequired(countryCode)) {
            Tuple4<Label, TextField, Label, TextField> tuple = FormBuilder.addLabelTextFieldLabelTextField(gridPane, ++gridRow,
                    Res.getWithCol("payment.account.owner"), BankUtil.getHolderIdLabel(countryCode));
            TextField holderNameTextField = tuple.second;
            holderNameTextField.setText(cashDepositAccountPayload.getHolderName());
            holderNameTextField.setMinWidth(300);
            tuple.forth.setText(cashDepositAccountPayload.getHolderTaxId());
        } else {
            FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
                    cashDepositAccountPayload.getHolderName());
        }
    }

    protected void addAcceptedBanksForAddAccount() {
    }

    public void addAcceptedBanksForDisplayAccount() {
    }
}
