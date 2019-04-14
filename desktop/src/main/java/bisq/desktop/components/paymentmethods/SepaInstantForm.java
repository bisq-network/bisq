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
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.BICValidator;
import bisq.desktop.util.validation.IBANValidator;

import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.SepaInstantAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.SepaInstantAccountPayload;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;

import java.util.List;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;

public class SepaInstantForm extends GeneralSepaForm {

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        SepaInstantAccountPayload sepaInstantAccountPayload = (SepaInstantAccountPayload) paymentAccountPayload;

        final String title = Res.get("payment.account.owner");
        final String value = sepaInstantAccountPayload.getHolderName();
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, title, value);

        addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1,
                Res.get("payment.bank.country"),
                CountryUtil.getNameAndCode(sepaInstantAccountPayload.getCountryCode()));
        // IBAN, BIC will not be translated
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, IBAN, sepaInstantAccountPayload.getIban());
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, gridRow, 1, BIC, sepaInstantAccountPayload.getBic());
        return gridRow;
    }

    private final SepaInstantAccount sepaInstantAccount;
    private final IBANValidator ibanValidator;
    private final BICValidator bicValidator;

    public SepaInstantForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, IBANValidator ibanValidator,
                           BICValidator bicValidator, InputValidator inputValidator,
                           GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.sepaInstantAccount = (SepaInstantAccount) paymentAccount;
        this.ibanValidator = ibanValidator;
        this.bicValidator = bicValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow,
                Res.get("payment.account.owner"));
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaInstantAccount.setHolderName(newValue);
            updateFromInputs();
        });

        ibanInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, IBAN);
        ibanInputTextField.setValidator(ibanValidator);
        ibanInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaInstantAccount.setIban(newValue);
            updateFromInputs();

        });
        InputTextField bicInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, BIC);
        bicInputTextField.setValidator(bicValidator);
        bicInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaInstantAccount.setBic(newValue);
            updateFromInputs();

        });

        ComboBox<Country> countryComboBox = addCountrySelection();

        setCountryComboBoxAction(countryComboBox, sepaInstantAccount);

        addEuroCountriesGrid();
        addNonEuroCountriesGrid();
        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();

        countryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllSepaInstantCountries()));
        Country country = CountryUtil.getDefaultCountry();
        if (CountryUtil.getAllSepaInstantCountries().contains(country)) {
            countryComboBox.getSelectionModel().select(country);
            sepaInstantAccount.setCountry(country);
            TradeCurrency currency = CurrencyUtil.getCurrencyByCountryCode(country.code);
            setupCurrency(country, currency);
        }

        updateFromInputs();
    }

    @Override
    void setupCurrency(Country country, TradeCurrency currency) {
        final boolean isSepaInstantCountry = CountryUtil.getAllSepaInstantEuroCountries().contains(country);
        updateCurrencyFormElements(currency, isSepaInstantCountry, sepaInstantAccount);
    }

    private void addEuroCountriesGrid() {
        addCountriesGrid(Res.get("payment.accept.euro"), euroCountryCheckBoxes,
                CountryUtil.getAllSepaInstantEuroCountries());
    }

    private void addNonEuroCountriesGrid() {
        addCountriesGrid(Res.get("payment.accept.nonEuro"), nonEuroCountryCheckBoxes,
                CountryUtil.getAllSepaInstantNonEuroCountries());
    }

    @Override
    void updateCountriesSelection(List<CheckBox> checkBoxList) {
        checkBoxList.forEach(checkBox -> {
            String countryCode = (String) checkBox.getUserData();
            TradeCurrency selectedCurrency = sepaInstantAccount.getSelectedTradeCurrency();
            if (selectedCurrency == null) {
                Country country = CountryUtil.getDefaultCountry();
                if (CountryUtil.getAllSepaInstantCountries().contains(country))
                    selectedCurrency = CurrencyUtil.getCurrencyByCountryCode(country.code);
            }

            boolean selected;
            if (selectedCurrency != null) {
                selected = true;
                sepaInstantAccount.addAcceptedCountry(countryCode);
            } else {
                selected = sepaInstantAccount.getAcceptedCountryCodes().contains(countryCode);
            }
            checkBox.setSelected(selected);
        });
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && bicValidator.validate(sepaInstantAccount.getBic()).isValid
                && ibanValidator.validate(sepaInstantAccount.getIban()).isValid
                && inputValidator.validate(sepaInstantAccount.getHolderName()).isValid
                && sepaInstantAccount.getAcceptedCountryCodes().size() > 0
                && sepaInstantAccount.getSingleTradeCurrency() != null
                && sepaInstantAccount.getCountry() != null);
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"), sepaInstantAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(sepaInstantAccount.getPaymentMethod().getId()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner"), sepaInstantAccount.getHolderName());
        addCompactTopLabelTextField(gridPane, ++gridRow, IBAN, sepaInstantAccount.getIban()).second.setMouseTransparent(false);
        addCompactTopLabelTextField(gridPane, ++gridRow, BIC, sepaInstantAccount.getBic()).second.setMouseTransparent(false);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.bank.country"),
                sepaInstantAccount.getCountry() != null ? sepaInstantAccount.getCountry().name : "");
        TradeCurrency singleTradeCurrency = sepaInstantAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);
        String countries;
        Tooltip tooltip = null;
        if (CountryUtil.containsAllSepaInstantEuroCountries(sepaInstantAccount.getAcceptedCountryCodes())) {
            countries = Res.get("shared.allEuroCountries");
        } else {
            countries = CountryUtil.getCodesString(sepaInstantAccount.getAcceptedCountryCodes());
            tooltip = new Tooltip(CountryUtil.getNamesByCodesString(sepaInstantAccount.getAcceptedCountryCodes()));
        }
        TextField acceptedCountries = addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.accepted.countries"), countries).second;
        if (tooltip != null) {
            acceptedCountries.setMouseTransparent(false);
            acceptedCountries.setTooltip(tooltip);
        }
        addLimitations(true);
    }

    @Override
    void removeAcceptedCountry(String countryCode) {
        sepaInstantAccount.removeAcceptedCountry(countryCode);
    }

    @Override
    void addAcceptedCountry(String countryCode) {
        sepaInstantAccount.addAcceptedCountry(countryCode);
    }
}
