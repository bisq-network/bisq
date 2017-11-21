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
import io.bisq.core.payment.AccountAgeWitnessService;
import io.bisq.core.payment.CountryBasedPaymentAccount;
import io.bisq.core.payment.PaymentAccount;
import io.bisq.core.payment.payload.PaymentAccountPayload;
import io.bisq.core.payment.payload.WesternUnionAccountPayload;
import io.bisq.gui.components.InputTextField;
import io.bisq.gui.main.overlays.popups.Popup;
import io.bisq.gui.util.BSFormatter;
import io.bisq.gui.util.FormBuilder;
import io.bisq.gui.util.Layout;
import io.bisq.gui.util.validation.EmailValidator;
import io.bisq.gui.util.validation.InputValidator;
import javafx.collections.FXCollections;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.util.StringConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import static io.bisq.gui.util.FormBuilder.addLabelTextFieldWithCopyIcon;

@Slf4j
public class WesternUnionForm extends PaymentMethodForm {

    public static int addFormForBuyer(GridPane gridPane, int gridRow,
                                      PaymentAccountPayload paymentAccountPayload) {
        final WesternUnionAccountPayload payload = (WesternUnionAccountPayload) paymentAccountPayload;
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.account.fullName"),
                payload.getHolderName());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.account.city"),
                payload.getCity());
        if (BankUtil.isStateRequired(payload.getCountryCode()))
            addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.account.state"),
                    payload.getState());
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.getWithCol("payment.email"),
                payload.getEmail());

        return gridRow;
    }

    protected final WesternUnionAccountPayload westernUnionAccountPayload;
    protected InputTextField holderNameInputTextField, emailInputTextField, cityInputTextField, stateInputTextField;
    private Label stateLabel;
    private ComboBox<TradeCurrency> currencyComboBox;
    private final EmailValidator emailValidator;

    public WesternUnionForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService, InputValidator inputValidator,
                            GridPane gridPane, int gridRow, BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.westernUnionAccountPayload = (WesternUnionAccountPayload) paymentAccount.paymentAccountPayload;

        emailValidator = new EmailValidator();
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
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.getWithCol("payment.account.fullName"),
                westernUnionAccountPayload.getHolderName());
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.account.city"),
                westernUnionAccountPayload.getCity()).second.setMouseTransparent(false);
        if (BankUtil.isStateRequired(westernUnionAccountPayload.getCountryCode()))
            FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.account.state"),
                    westernUnionAccountPayload.getState()).second.setMouseTransparent(false);
        FormBuilder.addLabelTextField(gridPane, ++gridRow, Res.get("payment.email"),
                westernUnionAccountPayload.getEmail());
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
                updateFromInputs();
                applyIsStateRequired();
                cityInputTextField.setText("");
                stateInputTextField.setText("");
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

        holderNameInputTextField = FormBuilder.addLabelInputTextField(gridPane,
                ++gridRow, Res.getWithCol("payment.account.fullName")).second;
        holderNameInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            westernUnionAccountPayload.setHolderName(newValue);
            updateFromInputs();
        });
        holderNameInputTextField.setValidator(inputValidator);

        cityInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.account.city")).second;
        cityInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            westernUnionAccountPayload.setCity(newValue);
            updateFromInputs();

        });

        final Tuple2<Label, InputTextField> tuple2 = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.account.state"));
        stateLabel = tuple2.first;
        stateInputTextField = tuple2.second;
        stateInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            westernUnionAccountPayload.setState(newValue);
            updateFromInputs();

        });
        applyIsStateRequired();

        emailInputTextField = FormBuilder.addLabelInputTextField(gridPane, ++gridRow, Res.get("payment.email")).second;
        emailInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            westernUnionAccountPayload.setEmail(newValue);
            updateFromInputs();
        });
        emailInputTextField.setValidator(emailValidator);

        addLimitations();
        addAccountNameTextFieldWithAutoFillCheckBox();

        updateFromInputs();
    }

    private void applyIsStateRequired() {
        final boolean stateRequired = BankUtil.isStateRequired(westernUnionAccountPayload.getCountryCode());
        stateLabel.setManaged(stateRequired);
        stateLabel.setVisible(stateRequired);
        stateInputTextField.setManaged(stateRequired);
        stateInputTextField.setVisible(stateRequired);
    }

    private CountryBasedPaymentAccount getCountryBasedPaymentAccount() {
        return (CountryBasedPaymentAccount) this.paymentAccount;
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            accountNameTextField.setText(Res.get(paymentAccount.getPaymentMethod().getId())
                    .concat(": ")
                    .concat(StringUtils.abbreviate(holderNameInputTextField.getText(), 9)));
        }
    }

    @Override
    public void updateAllInputsValid() {
        boolean result = isAccountNameValid()
                && paymentAccount.getSingleTradeCurrency() != null
                && getCountryBasedPaymentAccount().getCountry() != null
                && inputValidator.validate(westernUnionAccountPayload.getHolderName()).isValid
                && inputValidator.validate(westernUnionAccountPayload.getCity()).isValid
                && emailValidator.validate(westernUnionAccountPayload.getEmail()).isValid;
        allInputsValid.set(result);
    }
}
