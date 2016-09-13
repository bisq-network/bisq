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

import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.util.BSFormatter;
import io.bitsquare.gui.util.Layout;
import io.bitsquare.gui.util.validation.BICValidator;
import io.bitsquare.gui.util.validation.IBANValidator;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.locale.*;
import io.bitsquare.payment.*;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.bitsquare.gui.util.FormBuilder.*;

public class SepaForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(SepaForm.class);

    private final SepaAccount sepaAccount;
    private final IBANValidator ibanValidator;
    private final BICValidator bicValidator;
    private InputTextField ibanInputTextField;
    private InputTextField bicInputTextField;
    private TextField currencyTextField;
    private final List<CheckBox> euroCountryCheckBoxes = new ArrayList<>();
    private final List<CheckBox> nonEuroCountryCheckBoxes = new ArrayList<>();
    private ComboBox<TradeCurrency> currencyComboBox;

    public static int addFormForBuyer(GridPane gridPane, int gridRow, PaymentAccountContractData paymentAccountContractData) {
        SepaAccountContractData sepaAccountContractData = (SepaAccountContractData) paymentAccountContractData;
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Account holder name:", sepaAccountContractData.getHolderName());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "Country of bank:", CountryUtil.getNameAndCode(sepaAccountContractData.getCountryCode()));
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "IBAN:", sepaAccountContractData.getIban());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, "BIC/SWIFT:", sepaAccountContractData.getBic());
        return gridRow;
    }

    public SepaForm(PaymentAccount paymentAccount, IBANValidator ibanValidator, BICValidator bicValidator, InputValidator inputValidator,
                    GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, inputValidator, gridPane, gridRow, formatter);
        this.sepaAccount = (SepaAccount) paymentAccount;
        this.ibanValidator = ibanValidator;
        this.bicValidator = bicValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = addLabelInputTextField(gridPane, ++gridRow, "Account holder name:").second;
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaAccount.setHolderName(newValue);
            updateFromInputs();
        });

        ibanInputTextField = addLabelInputTextField(gridPane, ++gridRow, "IBAN:").second;
        ibanInputTextField.setValidator(ibanValidator);
        ibanInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaAccount.setIban(newValue);
            updateFromInputs();

        });
        bicInputTextField = addLabelInputTextField(gridPane, ++gridRow, "BIC/SWIFT:").second;
        bicInputTextField.setValidator(bicValidator);
        bicInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaAccount.setBic(newValue);
            updateFromInputs();

        });


        addLabel(gridPane, ++gridRow, "Country of your Bank:");
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


        countryComboBox.setPromptText("Select country of your Bank");
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
        addAllowedPeriod();
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
            currencyTextField.setText("Currency: " + currency.getNameAndCode());
        } else {
            currencyComboBox.setVisible(true);
            currencyComboBox.setManaged(true);
            currencyTextField.setVisible(false);
            currencyTextField.setManaged(false);
            currencyComboBox.setItems(FXCollections.observableArrayList(currency, CurrencyUtil.getFiatCurrency("EUR").get()));
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
        addCountriesGrid(isEditable, "Accept trades from those Euro countries:", euroCountryCheckBoxes, CountryUtil.getAllSepaEuroCountries());
    }

    private void addNonEuroCountriesGrid(boolean isEditable) {
        addCountriesGrid(isEditable, "Accept trades from those non-Euro countries:", nonEuroCountryCheckBoxes, CountryUtil.getAllSepaNonEuroCountries());
    }

    private void addCountriesGrid(boolean isEditable, String title, List<CheckBox> checkBoxList, List<Country> dataProvider) {
        Label label = addLabel(gridPane, ++gridRow, title, 0);
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
                selected = CurrencyUtil.getCurrencyByCountryCode(countryCode).getCode().equals(selectedCurrency.getCode());

                if (selected)
                    sepaAccount.addAcceptedCountry(countryCode);
                else
                    sepaAccount.removeAcceptedCountry(countryCode);
            } else {
                selected = sepaAccount.getAcceptedCountryCodes().contains(countryCode);
            }
            checkBox.setSelected(selected);
        });
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String iban = ibanInputTextField.getText();
            if (iban.length() > 9)
                iban = StringUtils.abbreviate(iban, 9);
            String method = BSResources.get(paymentAccount.getPaymentMethod().getId());
            CountryBasedPaymentAccount countryBasedPaymentAccount = (CountryBasedPaymentAccount) this.paymentAccount;
            String country = countryBasedPaymentAccount.getCountry() != null ? countryBasedPaymentAccount.getCountry().code : "?";
            String currency = this.paymentAccount.getSingleTradeCurrency() != null ? this.paymentAccount.getSingleTradeCurrency().getCode() : "?";
            accountNameTextField.setText(method.concat(" (").concat(currency).concat("/").concat(country).concat("): ").concat(iban));
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
        addLabelTextField(gridPane, gridRow, "Account name:", sepaAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, "Payment method:", BSResources.get(sepaAccount.getPaymentMethod().getId()));
        addLabelTextField(gridPane, ++gridRow, "Account holder name:", sepaAccount.getHolderName());
        addLabelTextField(gridPane, ++gridRow, "IBAN:", sepaAccount.getIban()).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "BIC/SWIFT:", sepaAccount.getBic()).second.setMouseTransparent(false);
        addLabelTextField(gridPane, ++gridRow, "Location of Bank:",
                sepaAccount.getCountry() != null ? sepaAccount.getCountry().name : "");
        addLabelTextField(gridPane, ++gridRow, "Currency:", sepaAccount.getSingleTradeCurrency().getNameAndCode());
        String countries;
        Tooltip tooltip = null;
        if (CountryUtil.containsAllSepaEuroCountries(sepaAccount.getAcceptedCountryCodes())) {
            countries = "All Euro countries";
        } else {
            countries = CountryUtil.getCodesString(sepaAccount.getAcceptedCountryCodes());
            tooltip = new Tooltip(CountryUtil.getNamesByCodesString(sepaAccount.getAcceptedCountryCodes()));
        }
        TextField acceptedCountries = addLabelTextField(gridPane, ++gridRow, "Accepted countries:", countries).second;
        if (tooltip != null) {
            acceptedCountries.setMouseTransparent(false);
            acceptedCountries.setTooltip(tooltip);
        }
        addAllowedPeriod();
    }
}
