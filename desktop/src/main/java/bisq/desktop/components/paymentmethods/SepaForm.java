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
import bisq.desktop.util.normalization.IBANNormalizer;
import bisq.desktop.util.validation.BICValidator;
import bisq.desktop.util.validation.SepaIBANValidator;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.locale.Country;
import bisq.core.locale.CountryUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.SepaAccount;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.payload.SepaAccountPayload;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import javafx.scene.control.ComboBox;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;

import java.util.List;
import java.util.Optional;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;

public class SepaForm extends GeneralSepaForm {

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload, String amount) {
        SepaAccountPayload sepaAccountPayload = (SepaAccountPayload) paymentAccountPayload;
        return GeneralSepaForm.addFormForBuyer(gridPane, gridRow, amount,
                sepaAccountPayload.getCountryCode(),
                sepaAccountPayload.getHolderName(),
                sepaAccountPayload.getBic(),
                sepaAccountPayload.getIban());
    }

    private final SepaAccount sepaAccount;
    private final SepaIBANValidator sepaIBANValidator;
    private final BICValidator bicValidator;

    public SepaForm(PaymentAccount paymentAccount,
                    AccountAgeWitnessService accountAgeWitnessService,
                    BICValidator bicValidator,
                    InputValidator inputValidator,
                    GridPane gridPane,
                    int gridRow,
                    CoinFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.sepaAccount = (SepaAccount) paymentAccount;
        this.bicValidator = bicValidator;
        this.sepaIBANValidator = new SepaIBANValidator();
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        InputTextField holderNameInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow,
                Res.get("payment.account.owner.fullname"));
        holderNameInputTextField.setValidator(inputValidator);
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaAccount.setHolderName(newValue);
            updateFromInputs();
        });

        InputTextField ibanInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, IBAN);
        ibanInputTextField.setTextFormatter(new TextFormatter<>(new IBANNormalizer()));
        ibanInputTextField.setValidator(sepaIBANValidator);

        InputTextField bicInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow, BIC);
        bicInputTextField.setValidator(bicValidator);
        bicInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaAccount.setBic(newValue);
            updateFromInputs();

        });

        ComboBox<Country> countryComboBox = addCountrySelection();

        setCountryComboBoxAction(countryComboBox, sepaAccount);

        addCountriesGrid(Res.get("payment.accept.euro"), CountryUtil.getAllSepaEuroCountries());
        addCountriesGrid(Res.get("payment.accept.nonEuro"), CountryUtil.getAllSepaNonEuroCountries());
        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();

        countryComboBox.setItems(FXCollections.observableArrayList(CountryUtil.getAllSepaCountries()));
        Country country = CountryUtil.getDefaultCountry();
        if (CountryUtil.getAllSepaCountries().contains(country)) {
            countryComboBox.getSelectionModel().select(country);
            sepaAccount.setCountry(country);
        }

        ibanInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            sepaAccount.setIban(newValue);
            updateFromInputs();

            if (ibanInputTextField.validate()) {
                List<Country> countries = CountryUtil.getAllSepaCountries();
                String ibanCountryCode = newValue.substring(0, 2).toUpperCase();
                Optional<Country> ibanCountry = countries
                        .stream()
                        .filter(c -> c.code.equals(ibanCountryCode))
                        .findFirst();

                ibanCountry.ifPresent(countryComboBox::setValue);
            }
        });

        countryComboBox.valueProperty().addListener((ov, oldValue, newValue) -> {
            sepaIBANValidator.setRestrictToCountry(newValue.code);
            ibanInputTextField.refreshValidation();
        });

        updateFromInputs();
    }

    @Override
    public void updateAllInputsValid() {
        allInputsValid.set(isAccountNameValid()
                && bicValidator.validate(sepaAccount.getBic()).isValid
                && sepaIBANValidator.validate(sepaAccount.getIban()).isValid
                && inputValidator.validate(sepaAccount.getHolderName()).isValid
                && sepaAccount.getAcceptedCountryCodes().size() > 0
                && sepaAccount.getSingleTradeCurrency() != null
                && sepaAccount.getCountry() != null);
    }

    @Override
    public void addFormForEditAccount() {
        gridRowFrom = gridRow;
        addAccountNameTextFieldWithAutoFillToggleButton();
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(sepaAccount.getPaymentMethod().getId()));
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.account.owner.fullname"), sepaAccount.getHolderName());
        addCompactTopLabelTextField(gridPane, ++gridRow, IBAN, sepaAccount.getIban()).second.setMouseTransparent(false);
        addCompactTopLabelTextField(gridPane, ++gridRow, BIC, sepaAccount.getBic()).second.setMouseTransparent(false);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.bank.country"),
                sepaAccount.getCountry() != null ? sepaAccount.getCountry().name : "");
        TradeCurrency singleTradeCurrency = sepaAccount.getSingleTradeCurrency();
        String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "null";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.currency"), nameAndCode);

        addCountriesGrid(Res.get("payment.accept.euro"), CountryUtil.getAllSepaEuroCountries());
        addCountriesGrid(Res.get("payment.accept.nonEuro"), CountryUtil.getAllSepaNonEuroCountries());
        addLimitations(true);
    }

    @Override
    void removeAcceptedCountry(String countryCode) {
        sepaAccount.removeAcceptedCountry(countryCode);
    }

    @Override
    void addAcceptedCountry(String countryCode) {
        sepaAccount.addAcceptedCountry(countryCode);
    }

    @Override
    boolean isCountryAccepted(String countryCode) {
        return sepaAccount.getAcceptedCountryCodes().contains(countryCode);
    }

    @Override
    protected String getIban() {
        return sepaAccount.getIban();
    }
}
