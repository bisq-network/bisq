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
import bisq.desktop.util.BSFormatter;
import bisq.desktop.util.Layout;
import bisq.desktop.util.validation.AltCoinAddressValidator;
import bisq.desktop.util.validation.InputValidator;

import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.CryptoCurrencyAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.CryptoCurrencyAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;

import bisq.common.locale.CurrencyUtil;
import bisq.common.locale.Res;
import bisq.common.locale.TradeCurrency;
import bisq.common.util.Tuple2;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static bisq.desktop.util.FormBuilder.addLabelInputTextField;
import static bisq.desktop.util.FormBuilder.addLabelSearchComboBox;
import static bisq.desktop.util.FormBuilder.addLabelTextField;
import static bisq.desktop.util.FormBuilder.addLabelTextFieldWithCopyIcon;

public class CryptoCurrencyForm extends PaymentMethodForm {
    private static final Logger log = LoggerFactory.getLogger(CryptoCurrencyForm.class);

    private final CryptoCurrencyAccount cryptoCurrencyAccount;
    private final AltCoinAddressValidator altCoinAddressValidator;
    private InputTextField addressInputTextField;

    private ComboBox<TradeCurrency> currencyComboBox;
    private Label addressLabel;

    public static int addFormForBuyer(GridPane gridPane,
                                      int gridRow,
                                      PaymentAccountPayload paymentAccountPayload,
                                      String labelTitle) {
        addLabelTextFieldWithCopyIcon(gridPane, ++gridRow, labelTitle,
                ((CryptoCurrencyAccountPayload) paymentAccountPayload).getAddress());
        return gridRow;
    }

    public CryptoCurrencyForm(PaymentAccount paymentAccount,
                              AccountAgeWitnessService accountAgeWitnessService,
                              AltCoinAddressValidator altCoinAddressValidator,
                              InputValidator inputValidator,
                              GridPane gridPane,
                              int gridRow,
                              BSFormatter formatter) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.cryptoCurrencyAccount = (CryptoCurrencyAccount) paymentAccount;
        this.altCoinAddressValidator = altCoinAddressValidator;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        addTradeCurrencyComboBox();
        currencyComboBox.setPrefWidth(250);
        Tuple2<Label, InputTextField> tuple2 = addLabelInputTextField(gridPane, ++gridRow,
                Res.get("payment.altcoin.address"));
        addressLabel = tuple2.first;
        addressInputTextField = tuple2.second;
        addressInputTextField.setValidator(altCoinAddressValidator);

        addressInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cryptoCurrencyAccount.setAddress(newValue);
            updateFromInputs();
        });

        addLimitations();
        addAccountNameTextFieldWithAutoFillCheckBox();
    }

    @Override
    public void updateFromInputs() {
        if (addressLabel != null && cryptoCurrencyAccount.getSingleTradeCurrency() != null)
            addressLabel.setText(Res.get("payment.altcoin.address.dyn",
                    cryptoCurrencyAccount.getSingleTradeCurrency().getName()));
        super.updateFromInputs();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameCheckBox != null && !useCustomAccountNameCheckBox.isSelected()) {
            String currency = paymentAccount.getSingleTradeCurrency() != null ? paymentAccount.getSingleTradeCurrency().getCode() : "";
            if (currency != null) {
                String address = addressInputTextField.getText();
                address = StringUtils.abbreviate(address, 9);
                accountNameTextField.setText(currency.concat(": ").concat(address));
            }
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                cryptoCurrencyAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addLabelTextField(gridPane, ++gridRow, Res.getWithCol("shared.paymentMethod"),
                Res.get(cryptoCurrencyAccount.getPaymentMethod().getId()));
        Tuple2<Label, TextField> tuple2 = addLabelTextField(gridPane, ++gridRow,
                Res.get("payment.altcoin.address"), cryptoCurrencyAccount.getAddress());
        addressLabel = tuple2.first;
        TextField field = tuple2.second;
        field.setMouseTransparent(false);
        final TradeCurrency singleTradeCurrency = cryptoCurrencyAccount.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addLabelTextField(gridPane, ++gridRow, Res.get("payment.altcoin"),
                nameAndCode);
        addLimitations();
    }

    @Override
    public void updateAllInputsValid() {
        TradeCurrency selectedTradeCurrency = cryptoCurrencyAccount.getSelectedTradeCurrency();
        if (selectedTradeCurrency != null) {
            altCoinAddressValidator.setCurrencyCode(selectedTradeCurrency.getCode());
            allInputsValid.set(isAccountNameValid()
                    && altCoinAddressValidator.validate(cryptoCurrencyAccount.getAddress()).isValid
                    && cryptoCurrencyAccount.getSingleTradeCurrency() != null);
        }
    }

    @Override
    protected void addTradeCurrencyComboBox() {
        //noinspection unchecked
        currencyComboBox = addLabelSearchComboBox(gridPane, ++gridRow, Res.get("payment.altcoin"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        currencyComboBox.setPromptText(Res.get("payment.select.altcoin"));
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getAllSortedCryptoCurrencies()));
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 15));
        currencyComboBox.setConverter(new StringConverter<TradeCurrency>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency != null ? tradeCurrency.getNameAndCode() : "";
            }

            @Override
            public TradeCurrency fromString(String s) {
                Optional<TradeCurrency> tradeCurrencyOptional = currencyComboBox.getItems().stream().
                        filter(tradeCurrency -> tradeCurrency.getNameAndCode().equals(s)).
                        findAny();
                if (tradeCurrencyOptional.isPresent())
                    return tradeCurrencyOptional.get();
                else
                    return null;
            }
        });
        currencyComboBox.setOnAction(e -> {
            paymentAccount.setSingleTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
            updateFromInputs();
        });
    }
}
