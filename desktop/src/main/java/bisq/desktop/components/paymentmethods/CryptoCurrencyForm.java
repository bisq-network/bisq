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

import bisq.core.dao.governance.asset.AssetService;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.CryptoCurrencyAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.CryptoCurrencyAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.validation.AltCoinAddressValidator;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.util.Tuple3;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.Optional;

import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;
import static bisq.desktop.util.GUIUtil.getComboBoxButtonCell;

public class CryptoCurrencyForm extends PaymentMethodForm {
    private final CryptoCurrencyAccount cryptoCurrencyAccount;
    private final AltCoinAddressValidator altCoinAddressValidator;
    private final AssetService assetService;

    private InputTextField addressInputTextField;

    public static int addFormForBuyer(GridPane gridPane,
                                      int gridRow,
                                      PaymentAccountPayload paymentAccountPayload,
                                      String labelTitle) {
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, labelTitle,
                ((CryptoCurrencyAccountPayload) paymentAccountPayload).getAddress());
        return gridRow;
    }

    public CryptoCurrencyForm(PaymentAccount paymentAccount,
                              AccountAgeWitnessService accountAgeWitnessService,
                              AltCoinAddressValidator altCoinAddressValidator,
                              InputValidator inputValidator,
                              GridPane gridPane,
                              int gridRow,
                              BSFormatter formatter,
                              AssetService assetService) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.cryptoCurrencyAccount = (CryptoCurrencyAccount) paymentAccount;
        this.altCoinAddressValidator = altCoinAddressValidator;
        this.assetService = assetService;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        addTradeCurrencyComboBox();
        currencyComboBox.setPrefWidth(250);
        addressInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow,
                Res.get("payment.altcoin.address"));
        addressInputTextField.setValidator(altCoinAddressValidator);

        addressInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            cryptoCurrencyAccount.setAddress(newValue);
            updateFromInputs();
        });

        addLimitations();
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    @Override
    public void updateFromInputs() {
        if (addressInputTextField != null && cryptoCurrencyAccount.getSingleTradeCurrency() != null)
            addressInputTextField.setPromptText(Res.get("payment.altcoin.address.dyn",
                    cryptoCurrencyAccount.getSingleTradeCurrency().getName()));
        super.updateFromInputs();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameToggleButton != null && !useCustomAccountNameToggleButton.isSelected()) {
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
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                cryptoCurrencyAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(cryptoCurrencyAccount.getPaymentMethod().getId()));
        Tuple3<Label, TextField, VBox> tuple2 = addCompactTopLabelTextField(gridPane, ++gridRow,
                Res.get("payment.altcoin.address"), cryptoCurrencyAccount.getAddress());
        TextField field = tuple2.second;
        field.setMouseTransparent(false);
        final TradeCurrency singleTradeCurrency = cryptoCurrencyAccount.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.altcoin"),
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
        currencyComboBox = FormBuilder.<TradeCurrency>addLabelSearchComboBox(gridPane, ++gridRow, Res.get("payment.altcoin"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        currencyComboBox.setPromptText(Res.get("payment.select.altcoin"));
        currencyComboBox.setButtonCell(getComboBoxButtonCell(Res.get("payment.select.altcoin"), currencyComboBox));

        currencyComboBox.getEditor().focusedProperty().addListener(observable -> {
            currencyComboBox.setPromptText("");
        });

        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getActiveSortedCryptoCurrencies(assetService)));
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
                return tradeCurrencyOptional.orElse(null);
            }
        });
        currencyComboBox.setOnAction(e -> {

            addressInputTextField.resetValidation();
            addressInputTextField.validate();

            paymentAccount.setSingleTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
            updateFromInputs();
        });
    }
}
