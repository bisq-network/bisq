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
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.CountryBasedPaymentAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.SepaAccount;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.SepaAccountPayload;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.FormBuilder;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.BICValidator;
import io.bisq.gui.util.validation.IBANValidator;
import io.bisq.gui.util.validation.InputValidator;
import javafx.collections.FXCollections;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.TextAlignment;
import javafx.util.StringConverter;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import static io.bisq.gui.util.FormBuilder.addLabelTextFieldWithCopyIcon;

public class SepaForm extends PaymentMethodForm {
    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        SepaAccountPayload sepaAccountPayload = (SepaAccountPayload) paymentAccountPayload;

        final String title = Res.get("payment.account.owner");
        final String value = sepaAccountPayload.getHolderName();
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, title, value);

        FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow,
                Res.getWithCol("payment.bank.country"),
                CountryUtil.getNameAndCode(sepaAccountPayload.getCountryCode()));
        // IBAN, BIC will not be translated
        FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "IBAN:", sepaAccountPayload.getIban());
        FormBuilder.addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "BIC:", sepaAccountPayload.getBic());
        return gridRow;
    }

    private final SepaAccount sepaAccount;
    private final IBANValidator ibanValidator;
    private final BICValidator bicValidator;
    private InputTextField ibanInputTextField;
    private TextField currencyTextField;
    private final List<CheckBox> euroCountryCheckBoxes = new ArrayList<>();
    private final List<CheckBox> nonEuroCountryCheckBoxes = new ArrayList<>();
    private ComboBox<TradeCurrency> currencyComboBox;

    public SepaForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, IBANValidator ibanValidator,
                    BICValidator bicValidator, InputValidator inputValidator,
                    GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.sepaAccount = (SepaAccount) paymentAccount;
        this.ibanValidator = ibanValidator;
        this.bicValidator = bicValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow,
                Res.getWithCol("payment.account.owner")).second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaAccount.setHolderName(newValue);
            updateFromInputs();
        });

        ibanInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, "IBAN:").second;
        ibanInputTextField.setValidator(ibanValidator);
        ibanInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaAccount.setIban(newValue);
            updateFromInputs();

        });
        InputTextField bicInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, "BIC:").second;
        bicInputTextField.setValidator(bicValidator);
        bicInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaAccount.setBic(newValue);
            updateFromInputs();

        });


        FormBuilder.addLabel(gridPane, ++gridRow, Res.getWithCol("payment.bank.country"));
        HBox hBox = new HBox();
        hBox.setSpacing(10);
        ComboBox<Country> countryComboBox = new ComboBox<>();
        currencyComboBox = new ComboBox<>();
        currencyTextField = new TextField("");
        currencyTextField.setEditable(false);
        currencyTextField.setMouseTransparent(true);
        currencyTextField.setFocusTraversable(false);
        currencyTextField.setMinWidth(300);

        currencyTextField.setVisible(false);
        currencyTextField.setManaged(false);
        currencyComboBox.setVisible(false);
        currencyComboBox.setManaged(false);

        hBox.getChildren().addAll(countryComboBox, currencyTextField, currencyComboBox);
        GridPane.setRowIndex(hBox, gridRow);
        GridPane.setColumnIndex(hBox, 1);
        gridPane.getChildren().add(hBox);


        countryComboBox.setPromptText(Res.get("payment.select.bank.country"));
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
            sepaAccount.setCountry(selectedItem);
            TradeCurrency currency = CurrencyUtil.getCurrencyByCountryCode(selectedItem.code);
            setupCurrency(selectedItem, currency);

            updateCountriesSelection(true, euroCountryCheckBoxes);
            updateCountriesSelection(true, nonEuroCountryCheckBoxes);
            updateFromInputs();
        });

        addEuroCountriesGrid(true);
        addNonEuroCountriesGrid(true);
        addLimitations();
        addAccountNameTextFieldWithAutoFillCheckBox();

        countryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllSepaCountries()));
        Country country = CountryUtil.getDefaultCountry();
        if (CountryUtil.getAllSepaCountries().contains(country)) {
            countryComboBox.getSelectionModel().select(country);
            sepaAccount.setCountry(country);
            TradeCurrency currency = CurrencyUtil.getCurrencyByCountryCode(country.code);
            setupCurrency(country, currency);
        }

        updateFromInputs();
    }

    private void setupCurrency(Country country, TradeCurrency currency) {
        if (CountryUtil.getAllSepaEuroCountries().contains(country)) {
            currencyTextField.setVisible(true);
            currencyTextField.setManaged(true);
            currencyComboBox.setVisible(false);
            currencyComboBox.setManaged(false);
            sepaAccount.setSingleTradeCurrency(currency);
            currencyTextField.setText(Res.get("payment.currencyWithSymbol", currency.getNameAndCode()));
        } else {
            currencyComboBox.setVisible(true);
            currencyComboBox.setManaged(true);
            currencyTextField.setVisible(false);
            currencyTextField.setManaged(false);
            currencyComboBox.setItems(FXCollections.observableArrayList(currency,
                    CurrencyUtil.getFiatCurrency("EUR").get()));
            currencyComboBox.setOnAction(e2 -> {
                sepaAccount.setSingleTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
                updateCountriesSelection(true, euroCountryCheckBoxes);
                autoFillNameTextField();
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
            currencyComboBox.getSelectionModel().select(0);
        }
    }

    private void addEuroCountriesGrid(boolean isEditable) {
        addCountriesGrid(isEditable, Res.get("payment.accept.euro"), euroCountryCheckBoxes,
                CountryUtil.getAllSepaEuroCountries());
    }

    private void addNonEuroCountriesGrid(boolean isEditable) {
        addCountriesGrid(isEditable, Res.get("payment.accept.nonEuro"), nonEuroCountryCheckBoxes,
                CountryUtil.getAllSepaNonEuroCountries());
    }

    private void addCountriesGrid(boolean isEditable, String title, List<CheckBox> checkBoxList, List<Country> dataProvider) {
        Label label = FormBuilder.addLabel(gridPane, ++gridRow, title, 0);
        label.setWrapText(true);
        label.setMaxWidth(180);
        label.setTextAlignment(TextAlignment.RIGHT);
        GridPane.setHalignment(label, HPos.RIGHT);
        GridPane.setValignment(label, VPos.TOP);
        FlowPane flowPane = new FlowPane();
        flowPane.setPadding(new Insets(10, 10, 10, 10));
        flowPane.setVgap(10);
        flowPane.setHgap(10);
        flowPane.setMinHeight(55);

        if (isEditable)
            flowPane.setId("flow-pane-checkboxes-bg");
        else
            flowPane.setId("flow-pane-checkboxes-non-editable-bg");

        dataProvider.stream().forEach(country ->
        {
            final String countryCode = country.code;
            CheckBox checkBox = new CheckBox(countryCode);
            checkBox.setUserData(countryCode);
            checkBoxList.add(checkBox);
            checkBox.setMouseTransparent(!isEditable);
            checkBox.setMinWidth(45);
            checkBox.setMaxWidth(45);
            checkBox.setTooltip(new Tooltip(country.name));
            checkBox.setOnAction(event -> {
                if (checkBox.isSelected())
                    sepaAccount.addAcceptedCountry(countryCode);
                else
                    sepaAccount.removeAcceptedCountry(countryCode);

                updateAllInputsValid();
            });
            flowPane.getChildren().add(checkBox);
        });
        updateCountriesSelection(isEditable, checkBoxList);

        GridPane.setRowIndex(flowPane, gridRow);
        GridPane.setColumnIndex(flowPane, 1);
        gridPane.getChildren().add(flowPane);
    }

    private void updateCountriesSelection(boolean isEditable, List<CheckBox> checkBoxList) {
        checkBoxList.stream().forEach(checkBox -> {
            String countryCode = (String) checkBox.getUserData();
            TradeCurrency selectedCurrency = sepaAccount.getSelectedTradeCurrency();
            if (selectedCurrency == null) {
                Country country = CountryUtil.getDefaultCountry();
                if (CountryUtil.getAllSepaCountries().contains(country))
                    selectedCurrency = CurrencyUtil.getCurrencyByCountryCode(country.code);
            }

            boolean selected;
            if (isEditable && selectedCurrency != null) {

                // We changed behaviour to auto select all be default
                // Lets keep the old code in case we want to revert...
                selected = true;
                //selected = CurrencyUtil.getCurrencyByCountryCode(countryCode).getCode().equals(selectedCurrency.getCode());

                //if (selected)
                sepaAccount.addAcceptedCountry(countryCode);
                //else
                //    sepaAccount.removeAcceptedCountry(countryCode);
            } else {
                selected = sepaAccount.getAcceptedCountryCodes().contains(countryCode);
            }
            checkBox.setSelected(selected);
        });
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            TradeCurrency singleTradeCurrency = this.paymentAccount.getSingleTradeCurrency();
            String currency = singleTradeCurrency != null ? singleTradeCurrency.getCode() : null;
            if (currency != null) {
                String iban = ibanInputTextField.getText();
                if (iban.length() > 9)
                    iban = StringUtils.abbreviate(iban, 9);
                String method = Res.get(paymentAccount.getPaymentMethod().getId());
                CountryBasedPaymentAccount countryBasedPaymentAccount = (CountryBasedPaymentAccount) this.paymentAccount;
                String country = countryBasedPaymentAccount.getCountry() != null ?
                        countryBasedPaymentAccount.getCountry().code : null;
                if (country != null)
                    accountNameTextField.setText(method.concat(" (").concat(currency).concat("/").concat(country)
                            .concat("): ").concat(iban));
            }
        }
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && bicValidator.validate(sepaAccount.getBic()).isValid
                && ibanValidator.validate(sepaAccount.getIban()).isValid
                && inputValidator.validate(sepaAccount.getHolderName()).isValid
                && sepaAccount.getAcceptedCountryCodes().size() > 0
                && sepaAccount.getSingleTradeCurrency() != null
                && sepaAccount.getCountry() != null);
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        FormBuilder.addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), sepaAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(sepaAccount.getPaymentMethod().getId()));
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.owner"), sepaAccount.getHolderName());
        FormBuilder.addLabelTextField(gridPane, ++gridRow, "IBAN:", sepaAccount.getIban()).second.setMouseTransparent(false);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, "BIC:", sepaAccount.getBic()).second.setMouseTransparent(false);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.bank.country"),
                sepaAccount.getCountry() != null ? sepaAccount.getCountry().name : "");
        TradeCurrency singleTradeCurrency = sepaAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.currency"), nameAndCode);
        String countries;
        Tooltip tooltip = null;
        if (CountryUtil.containsAllSepaEuroCountries(sepaAccount.getAcceptedCountryCodes())) {
            countries = Res.getWithCol("shared.allEuroCountries");
        } else {
            countries = CountryUtil.getCodesString(sepaAccount.getAcceptedCountryCodes());
            tooltip = new Tooltip(CountryUtil.getNamesByCodesString(sepaAccount.getAcceptedCountryCodes()));
        }
        TextField acceptedCountries = FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.accepted.countries"), countries).second;
        if (tooltip != null) {
            acceptedCountries.setMouseTransparent(false);
            acceptedCountries.setTooltip(tooltip);
        }
        addLimitations();
    }
}
