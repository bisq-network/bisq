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
import io.bisq.core.payment.payload.WesternUnionAccountPayload;
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

public class WesternUnionForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(WesternUnionForm.class);

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountPayload paymentAccountPayload) {
        WesternUnionAccountPayload data = (WesternUnionAccountPayload) paymentAccountPayload;
        String holderName = data.getHolderName();
        String countryCode = data.getCountryCode();
        String requirements = data.getRequirements();
        boolean showRequirements = requirements != null && !requirements.isEmpty();

        FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.account.owner"),
                    data.getHolderName());

        if (!showRequirements)
            FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.country"),
                    CountryUtil.getNameAndCode(countryCode));
        else
            requirements += "\n" + Res.get("payment.country") + " " + CountryUtil.getNameAndCode(countryCode);

        // We don't want to display more than 6 rows to avoid scrolling, so if we get too many fields we combine them horizontally
        int nrRows = 0;
        /* if (BankUtil.isBankNameRequired(countryCode)) */
        /*     nrRows++; */
        /* if (BankUtil.isBankIdRequired(countryCode)) */
        /*     nrRows++; */
        /* if (BankUtil.isBranchIdRequired(countryCode)) */
        /*     nrRows++; */
        /* if (BankUtil.isAccountNrRequired(countryCode)) */
        /*     nrRows++; */
        /* if (BankUtil.isAccountTypeRequired(countryCode)) */
        /*     nrRows++; */

        /* String bankNameLabel = BankUtil.getBankNameLabel(countryCode); */
        /* String bankIdLabel = BankUtil.getBankIdLabel(countryCode); */
        /* String branchIdLabel = BankUtil.getBranchIdLabel(countryCode); */
        /* String accountNrLabel = BankUtil.getAccountNrLabel(countryCode); */
        /* String accountTypeLabel = BankUtil.getAccountTypeLabel(countryCode); */

        /* boolean accountNrAccountTypeCombined = false; */
        /* boolean bankNameBankIdCombined = false; */
        /* boolean bankIdBranchIdCombined = false; */
        /* boolean bankNameBranchIdCombined = false; */
        /* boolean branchIdAccountNrCombined = false; */
        /* if (nrRows > 2) { */
        /*     // Try combine AccountNr + AccountType */
        /*     accountNrAccountTypeCombined = BankUtil.isAccountNrRequired(countryCode) && BankUtil.isAccountTypeRequired(countryCode); */
        /*     if (accountNrAccountTypeCombined) */
        /*         nrRows--; */

        /*     if (nrRows > 2) { */
        /*         // Next we try BankName + BankId */
        /*         bankNameBankIdCombined = BankUtil.isBankNameRequired(countryCode) && BankUtil.isBankIdRequired(countryCode); */
        /*         if (bankNameBankIdCombined) */
        /*             nrRows--; */

        /*         if (nrRows > 2) { */
        /*             // Next we try BankId + BranchId */
        /*             bankIdBranchIdCombined = !bankNameBankIdCombined && BankUtil.isBankIdRequired(countryCode) && */
        /*                     BankUtil.isBranchIdRequired(countryCode); */
        /*             if (bankIdBranchIdCombined) */
        /*                 nrRows--; */

        /*             if (nrRows > 2) { */
        /*                 // Next we try BankId + BranchId */
        /*                 bankNameBranchIdCombined = !bankNameBankIdCombined && !bankIdBranchIdCombined && */
        /*                         BankUtil.isBankNameRequired(countryCode) && BankUtil.isBranchIdRequired(countryCode); */
        /*                 if (bankNameBranchIdCombined) */
        /*                     nrRows--; */

        /*                 if (nrRows > 2) { */
        /*                     branchIdAccountNrCombined = !bankNameBranchIdCombined && !bankIdBranchIdCombined && */
        /*                             !accountNrAccountTypeCombined && */
        /*                             BankUtil.isBranchIdRequired(countryCode) && BankUtil.isAccountNrRequired(countryCode); */
        /*                     if (branchIdAccountNrCombined) */
        /*                         nrRows--; */

        /*                     if (nrRows > 2) */
        /*                         log.warn("We still have too many rows...."); */
        /*                 } */
        /*             } */
        /*         } */
        /*     } */
        /* } */

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

    protected final WesternUnionAccountPayload westernUnionAccountPayload;
    private Label holderIdLabel;
    protected InputTextField holderNameInputTextField;
    private Tuple2<Label, InputTextField> accountNrTuple;
    private Tuple2<Label, ComboBox> accountTypeTuple;
    private Label accountTypeLabel;
    private ComboBox<String> accountTypeComboBox;
    private boolean validatorsApplied;
    private ComboBox<TradeCurrency> currencyComboBox;


    public WesternUnionForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator,
                           GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.westernUnionAccountPayload = (WesternUnionAccountPayload) paymentAccount.paymentAccountPayload;

    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        String countryCode = westernUnionAccountPayload.getCountryCode();

        FormBuilder.addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), paymentAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(paymentAccount.getPaymentMethod().getId()));
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.country"),
                getCountryBasedPaymentAccount().getCountry() != null ? getCountryBasedPaymentAccount().getCountry().name : "");
        TradeCurrency singleTradeCurrency = paymentAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"),
                nameAndCode);
        addHolderNameAndIdForDisplayAccount();

        String requirements = westernUnionAccountPayload.getRequirements();
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

                holderNameInputTextField.resetValidation();

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

        addHolderNameAndId();

        TextArea requirementsTextArea = FormBuilder.addLabelTextArea(gridPane, ++gridRow, Res.get("payment.extras"), "").second;
        requirementsTextArea.setMinHeight(30);
        requirementsTextArea.setMaxHeight(30);
        requirementsTextArea.textProperty().addListener((ov, oldValue, newValue) -> {
            westernUnionAccountPayload.setRequirements(newValue);
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
        Tuple2<Label, InputTextField> tuple = FormBuilder.addLabelInputTextField(gridPane,
                ++gridRow, Res.getWithCol("payment.account.owner"));
        holderNameInputTextField = tuple.second;
        holderNameInputTextField.setMinWidth(300);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            westernUnionAccountPayload.setHolderName(newValue);
            updateFromInputs();
        });
        holderNameInputTextField.minWidthProperty().bind(currencyComboBox.widthProperty());
        holderNameInputTextField.setValidator(inputValidator);

        /* emailInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.email")).second; */
        /* emailInputTextField.textProperty().addListener((ov, oldValue, newValue) -> { */
        /*     westernUnionAccountPayload.setHolderEmail(newValue); */
        /*     updateFromInputs(); */
        /* }); */
        /* emailInputTextField.minWidthProperty().bind(currencyComboBox.widthProperty()); */
        /* emailInputTextField.setValidator(emailValidator); */

    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String bankId = null;
            String countryCode = westernUnionAccountPayload.getCountryCode();
            if (countryCode == null)
                countryCode = "";

            String method = Res.get(paymentAccount.getPaymentMethod().getId());
        }
    }

    @Override
    public void updateAllInputsValid() {
        boolean result = isAccountNameValid()
                && paymentAccount.getSingleTradeCurrency() != null
                && getCountryBasedPaymentAccount().getCountry() != null
                && holderNameInputTextField.getValidator().validate(westernUnionAccountPayload.getHolderName()).isValid;

        String countryCode = westernUnionAccountPayload.getCountryCode();
        allInputsValid.set(result);
    }

    protected void addHolderNameAndIdForDisplayAccount() {
        String countryCode = westernUnionAccountPayload.getCountryCode();

        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"),
            westernUnionAccountPayload.getHolderName());
    }

    protected void addAcceptedBanksForAddAccount() {
    }

    public void addAcceptedBanksForDisplayAccount() {
    }
}
