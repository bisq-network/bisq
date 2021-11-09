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

import bisq.desktop.components.AutocompleteComboBox;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.dao.governance.asset.AssetService;
import bisq.core.filter.FilterManager;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.payment.AssetAccount;
import bisq.core.payment.InstantCryptoCurrencyAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.payment.payload.AssetsAccountPayload;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.payment.validation.AltCoinAddressValidator;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.UserThread;
import bisq.common.util.Tuple3;

import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;

import javafx.util.StringConverter;

import static bisq.desktop.util.DisplayUtils.createAssetsAccountName;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextField;
import static bisq.desktop.util.FormBuilder.addCompactTopLabelTextFieldWithCopyIcon;
import static bisq.desktop.util.FormBuilder.addLabelCheckBox;
import static bisq.desktop.util.FormBuilder.addTopLabelTextField;
import static bisq.desktop.util.GUIUtil.getComboBoxButtonCell;

public class AssetsForm extends PaymentMethodForm {
    public static final String INSTANT_TRADE_NEWS = "instantTradeNews0.9.5";
    private final AssetAccount assetAccount;
    private final AltCoinAddressValidator altCoinAddressValidator;
    private final AssetService assetService;
    private final FilterManager filterManager;

    private InputTextField addressInputTextField;
    private CheckBox tradeInstantCheckBox;
    private boolean tradeInstant;

    public static int addFormForBuyer(GridPane gridPane,
                                      int gridRow,
                                      PaymentAccountPayload paymentAccountPayload,
                                      String labelTitle) {
        addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, labelTitle,
                ((AssetsAccountPayload) paymentAccountPayload).getAddress());
        return gridRow;
    }

    public AssetsForm(PaymentAccount paymentAccount,
                      AccountAgeWitnessService accountAgeWitnessService,
                      AltCoinAddressValidator altCoinAddressValidator,
                      InputValidator inputValidator,
                      GridPane gridPane,
                      int gridRow,
                      CoinFormatter formatter,
                      AssetService assetService,
                      FilterManager filterManager) {
        super(paymentAccount, accountAgeWitnessService, inputValidator, gridPane, gridRow, formatter);
        this.assetAccount = (AssetAccount) paymentAccount;
        this.altCoinAddressValidator = altCoinAddressValidator;
        this.assetService = assetService;
        this.filterManager = filterManager;

        tradeInstant = paymentAccount instanceof InstantCryptoCurrencyAccount;
    }

    @Override
    public void addFormForAddAccount() {
        gridRowFrom = gridRow + 1;

        addTradeCurrencyComboBox();
        currencyComboBox.setPrefWidth(250);

        tradeInstantCheckBox = addLabelCheckBox(gridPane, ++gridRow,
                Res.get("payment.altcoin.tradeInstantCheckbox"), 10);
        tradeInstantCheckBox.setSelected(tradeInstant);
        tradeInstantCheckBox.setOnAction(e -> {
            tradeInstant = tradeInstantCheckBox.isSelected();
            if (tradeInstant)
                new Popup().information(Res.get("payment.altcoin.tradeInstant.popup")).show();
            paymentLimitationsTextField.setText(getLimitationsText());
        });

        gridPane.getChildren().remove(tradeInstantCheckBox);
        tradeInstantCheckBox.setPadding(new Insets(0, 40, 0, 0));

        gridPane.getChildren().add(tradeInstantCheckBox);

        addressInputTextField = FormBuilder.addInputTextField(gridPane, ++gridRow,
                Res.get("payment.altcoin.address"));
        addressInputTextField.setValidator(altCoinAddressValidator);

        addressInputTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            if (newValue.startsWith("monero:")) {
                UserThread.execute(() -> {
                    String addressWithoutPrefix = newValue.replace("monero:", "");
                    addressInputTextField.setText(addressWithoutPrefix);
                });
                return;
            }
            assetAccount.setAddress(newValue);
            updateFromInputs();
        });

        addLimitations(false);
        addAccountNameTextFieldWithAutoFillToggleButton();
    }

    @Override
    public PaymentAccount getPaymentAccount() {
        if (tradeInstant) {
            InstantCryptoCurrencyAccount instantCryptoCurrencyAccount = new InstantCryptoCurrencyAccount();
            instantCryptoCurrencyAccount.init();
            instantCryptoCurrencyAccount.setAccountName(paymentAccount.getAccountName());
            instantCryptoCurrencyAccount.setSaltAsHex(paymentAccount.getSaltAsHex());
            instantCryptoCurrencyAccount.setSalt(paymentAccount.getSalt());
            instantCryptoCurrencyAccount.setSingleTradeCurrency(paymentAccount.getSingleTradeCurrency());
            instantCryptoCurrencyAccount.setSelectedTradeCurrency(paymentAccount.getSelectedTradeCurrency());
            instantCryptoCurrencyAccount.setAddress(assetAccount.getAddress());
            return instantCryptoCurrencyAccount;
        } else {
            return paymentAccount;
        }
    }

    @Override
    public void updateFromInputs() {
        if (addressInputTextField != null && assetAccount.getSingleTradeCurrency() != null)
            addressInputTextField.setPromptText(Res.get("payment.altcoin.address.dyn",
                    assetAccount.getSingleTradeCurrency().getName()));
        super.updateFromInputs();
    }

    @Override
    protected void autoFillNameTextField() {
        if (useCustomAccountNameToggleButton != null && !useCustomAccountNameToggleButton.isSelected()) {
            accountNameTextField.setText(createAssetsAccountName(paymentAccount, addressInputTextField.getText()));
        }
    }

    @Override
    public void addFormForDisplayAccount() {
        gridRowFrom = gridRow;
        addTopLabelTextField(gridPane, gridRow, Res.get("payment.account.name"),
                assetAccount.getAccountName(), Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("shared.paymentMethod"),
                Res.get(assetAccount.getPaymentMethod().getId()));
        Tuple3<Label, TextField, VBox> tuple2 = addCompactTopLabelTextField(gridPane, ++gridRow,
                Res.get("payment.altcoin.address"), assetAccount.getAddress());
        TextField field = tuple2.second;
        field.setMouseTransparent(false);
        final TradeCurrency singleTradeCurrency = assetAccount.getSingleTradeCurrency();
        final String nameAndCode = singleTradeCurrency != null ? singleTradeCurrency.getNameAndCode() : "";
        addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.altcoin"),
                nameAndCode);
        addLimitations(true);
    }

    @Override
    public void updateAllInputsValid() {
        TradeCurrency selectedTradeCurrency = assetAccount.getSelectedTradeCurrency();
        if (selectedTradeCurrency != null) {
            altCoinAddressValidator.setCurrencyCode(selectedTradeCurrency.getCode());
            allInputsValid.set(isAccountNameValid()
                    && altCoinAddressValidator.validate(assetAccount.getAddress()).isValid
                    && assetAccount.getSingleTradeCurrency() != null);
        }
    }

    @Override
    protected void addTradeCurrencyComboBox() {
        currencyComboBox = FormBuilder.<TradeCurrency>addLabelAutocompleteComboBox(gridPane, ++gridRow, Res.get("payment.altcoin"),
                Layout.FIRST_ROW_AND_GROUP_DISTANCE).second;
        currencyComboBox.setPromptText(Res.get("payment.select.altcoin"));
        currencyComboBox.setButtonCell(getComboBoxButtonCell(Res.get("payment.select.altcoin"), currencyComboBox));

        currencyComboBox.getEditor().focusedProperty().addListener(observable ->
                currencyComboBox.setPromptText(""));

        ((AutocompleteComboBox<TradeCurrency>) currencyComboBox).setAutocompleteItems(
                CurrencyUtil.getActiveSortedCryptoCurrencies(assetService, filterManager));
        currencyComboBox.setVisibleRowCount(Math.min(currencyComboBox.getItems().size(), 10));

        currencyComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency != null ? tradeCurrency.getNameAndCode() : "";
            }

            @Override
            public TradeCurrency fromString(String s) {
                return currencyComboBox.getItems().stream().
                        filter(item -> item.getNameAndCode().equals(s)).
                        findAny().orElse(null);
            }
        });

        ((AutocompleteComboBox<?>) currencyComboBox).setOnChangeConfirmed(e -> {
            addressInputTextField.resetValidation();
            addressInputTextField.validate();
            TradeCurrency tradeCurrency = currencyComboBox.getSelectionModel().getSelectedItem();
            paymentAccount.setSingleTradeCurrency(tradeCurrency);
            updateFromInputs();

            if (tradeCurrency != null && tradeCurrency.getCode().equals("BSQ")) {
                new Popup().information(Res.get("payment.select.altcoin.bsq.warning")).show();
            }
        });
    }
}
