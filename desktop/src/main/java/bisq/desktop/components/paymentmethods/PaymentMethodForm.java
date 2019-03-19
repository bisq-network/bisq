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

import bisq.desktop.components.AutoTooltipCheckBox;
import bisq.desktop.components.InfoTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;

import bisq.core.locale.Country;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.FiatCurrency;
import bisq.core.locale.Res;
import bisq.core.locale.TradeCurrency;
import bisq.core.offer.Offer;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.payment.AssetAccount;
import bisq.core.payment.PaymentAccount;
import bisq.core.util.BSFormatter;
import bisq.core.util.validation.InputValidator;

import bisq.common.util.Tuple3;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import org.apache.commons.lang3.StringUtils;

import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import javafx.collections.FXCollections;

import javafx.util.StringConverter;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static bisq.desktop.util.FormBuilder.*;

@Slf4j
public abstract class PaymentMethodForm {
    protected final PaymentAccount paymentAccount;
    private final AccountAgeWitnessService accountAgeWitnessService;
    protected final InputValidator inputValidator;
    protected final GridPane gridPane;
    protected int gridRow;
    private final BSFormatter formatter;
    protected final BooleanProperty allInputsValid = new SimpleBooleanProperty();

    protected int gridRowFrom;
    InputTextField accountNameTextField;
    ToggleButton useCustomAccountNameToggleButton;
    protected ComboBox<TradeCurrency> currencyComboBox;

    public PaymentMethodForm(PaymentAccount paymentAccount, AccountAgeWitnessService accountAgeWitnessService,
                             InputValidator inputValidator, GridPane gridPane, int gridRow, BSFormatter formatter) {
        this.paymentAccount = paymentAccount;
        this.accountAgeWitnessService = accountAgeWitnessService;
        this.inputValidator = inputValidator;
        this.gridPane = gridPane;
        this.gridRow = gridRow;
        this.formatter = formatter;
    }

    protected void addTradeCurrencyComboBox() {
        currencyComboBox = FormBuilder.addComboBox(gridPane, ++gridRow, Res.get("shared.currency"));
        currencyComboBox.setPromptText(Res.get("list.currency.select"));
        currencyComboBox.setItems(FXCollections.observableArrayList(CurrencyUtil.getMainFiatCurrencies()));
        currencyComboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(TradeCurrency tradeCurrency) {
                return tradeCurrency.getNameAndCode();
            }

            @Override
            public TradeCurrency fromString(String s) {
                return null;
            }
        });
        currencyComboBox.setOnAction(e -> {
            paymentAccount.setSingleTradeCurrency(currencyComboBox.getSelectionModel().getSelectedItem());
            updateFromInputs();
        });
    }

    protected void addAccountNameTextFieldWithAutoFillToggleButton() {
        Tuple3<Label, InputTextField, ToggleButton> tuple = addTopLabelInputTextFieldSlideToggleButton(gridPane, ++gridRow,
                Res.get("payment.account.name"), Res.get("payment.useCustomAccountName"));
        accountNameTextField = tuple.second;
        accountNameTextField.setPrefWidth(300);
        accountNameTextField.setEditable(false);
        accountNameTextField.setValidator(inputValidator);
        accountNameTextField.setFocusTraversable(false);
        accountNameTextField.textProperty().addListener((ov, oldValue, newValue) -> {
            paymentAccount.setAccountName(newValue);
            updateAllInputsValid();
        });
        useCustomAccountNameToggleButton = tuple.third;
        useCustomAccountNameToggleButton.setSelected(false);
        useCustomAccountNameToggleButton.setOnAction(e -> {
            boolean selected = useCustomAccountNameToggleButton.isSelected();
            accountNameTextField.setEditable(selected);
            accountNameTextField.setFocusTraversable(selected);
            autoFillNameTextField();
        });
    }

    public static InfoTextField addOpenTradeDuration(GridPane gridPane,
                                                     int gridRow,
                                                     Offer offer) {
        long hours = offer.getMaxTradePeriod() / 3600_000;
        final Tuple3<Label, InfoTextField, VBox> labelInfoTextFieldVBoxTuple3 =
                addTopLabelInfoTextField(gridPane, gridRow, Res.get("payment.maxPeriod"),
                        getTimeText(hours), -Layout.FLOATING_LABEL_DISTANCE);
        return labelInfoTextFieldVBoxTuple3.second;
    }

    private static String getTimeText(long hours) {
        String time = hours + " " + Res.get("time.hours");
        if (hours == 1)
            time = Res.get("time.1hour");
        else if (hours == 24)
            time = Res.get("time.1day");
        else if (hours > 24)
            time = hours / 24 + " " + Res.get("time.days");

        return time;
    }

    protected void addLimitations(boolean isDisplayForm) {
        long hours = paymentAccount.getMaxTradePeriod() / 3600_000;

        final TradeCurrency tradeCurrency;
        if (paymentAccount.getSingleTradeCurrency() != null)
            tradeCurrency = paymentAccount.getSingleTradeCurrency();
        else if (paymentAccount.getSelectedTradeCurrency() != null)
            tradeCurrency = paymentAccount.getSelectedTradeCurrency();
        else if (!paymentAccount.getTradeCurrencies().isEmpty())
            tradeCurrency = paymentAccount.getTradeCurrencies().get(0);
        else
            tradeCurrency = paymentAccount instanceof AssetAccount ?
                    CurrencyUtil.getAllSortedCryptoCurrencies().get(0) :
                    CurrencyUtil.getDefaultTradeCurrency();


        final boolean isAddAccountScreen = paymentAccount.getAccountName() == null;
        final long accountAge = !isAddAccountScreen ? accountAgeWitnessService.getMyAccountAge(paymentAccount.getPaymentAccountPayload()) : 0L;

        final String limitationsText = paymentAccount instanceof AssetAccount ?
                Res.get("payment.maxPeriodAndLimitCrypto",
                        getTimeText(hours),
                        formatter.formatCoinWithCode(Coin.valueOf(accountAgeWitnessService.getMyTradeLimit(paymentAccount, tradeCurrency.getCode()))))
                :
                Res.get("payment.maxPeriodAndLimit",
                        getTimeText(hours),
                        formatter.formatCoinWithCode(Coin.valueOf(accountAgeWitnessService.getMyTradeLimit(paymentAccount, tradeCurrency.getCode()))),
                        formatter.formatAccountAge(accountAge));

        if (isDisplayForm)
            addCompactTopLabelTextField(gridPane, ++gridRow, Res.get("payment.limitations"), limitationsText);
        else
            addTopLabelTextField(gridPane, ++gridRow, Res.get("payment.limitations"), limitationsText);

        if (isAddAccountScreen) {
            InputTextField inputTextField = addInputTextField(gridPane, ++gridRow, Res.get("payment.salt"), 0);
            inputTextField.setText(Utilities.bytesAsHexString(paymentAccount.getPaymentAccountPayload().getSalt()));
            inputTextField.textProperty().addListener((observable, oldValue, newValue) -> {
                if (!newValue.isEmpty()) {
                    try {
                        // test if input is hex
                        Utilities.decodeFromHex(newValue);

                        paymentAccount.setSaltAsHex(newValue);
                    } catch (Throwable t) {
                        new Popup().warning(Res.get("payment.error.noHexSalt")).show();
                        inputTextField.setText(Utilities.bytesAsHexString(paymentAccount.getPaymentAccountPayload().getSalt()));
                        log.warn(t.toString());
                    }
                }
            });
        } else {
            addCompactTopLabelTextFieldWithCopyIcon(gridPane, ++gridRow, Res.get("payment.salt",
                    Utilities.bytesAsHexString(paymentAccount.getPaymentAccountPayload().getSalt())),
                    Utilities.bytesAsHexString(paymentAccount.getPaymentAccountPayload().getSalt()));
        }
    }

    void applyTradeCurrency(TradeCurrency tradeCurrency, FiatCurrency defaultCurrency) {
        if (!defaultCurrency.equals(tradeCurrency)) {
            new Popup<>().warning(Res.get("payment.foreign.currency"))
                    .actionButtonText(Res.get("shared.yes"))
                    .onAction(() -> {
                        paymentAccount.setSingleTradeCurrency(tradeCurrency);
                        autoFillNameTextField();
                    })
                    .closeButtonText(Res.get("payment.restore.default"))
                    .onClose(() -> currencyComboBox.getSelectionModel().select(defaultCurrency))
                    .show();
        } else {
            paymentAccount.setSingleTradeCurrency(tradeCurrency);
            autoFillNameTextField();
        }
    }

    void setAccountNameWithString(String name) {
        if (useCustomAccountNameToggleButton != null && !useCustomAccountNameToggleButton.isSelected()) {
            name = StringUtils.abbreviate(name, 9);
            String method = Res.get(paymentAccount.getPaymentMethod().getId());
            accountNameTextField.setText(method.concat(": ").concat(name));
        }
    }

    void fillUpFlowPaneWithCurrencies(boolean isEditable, FlowPane flowPane,
                                      TradeCurrency e, PaymentAccount paymentAccount) {
        CheckBox checkBox = new AutoTooltipCheckBox(e.getCode());
        checkBox.setMouseTransparent(!isEditable);
        checkBox.setSelected(paymentAccount.getTradeCurrencies().contains(e));
        checkBox.setMinWidth(60);
        checkBox.setMaxWidth(checkBox.getMinWidth());
        checkBox.setTooltip(new Tooltip(e.getName()));
        checkBox.setOnAction(event -> {
            if (checkBox.isSelected())
                paymentAccount.addCurrency(e);
            else
                paymentAccount.removeCurrency(e);

            updateAllInputsValid();
        });
        flowPane.getChildren().add(checkBox);
    }

    void fillUpFlowPaneWithCountries(List<CheckBox> checkBoxList, FlowPane flowPane, Country country) {
        final String countryCode = country.code;
        CheckBox checkBox = new AutoTooltipCheckBox(countryCode);
        checkBox.setUserData(countryCode);
        checkBoxList.add(checkBox);
        checkBox.setMouseTransparent(false);
        checkBox.setMinWidth(45);
        checkBox.setMaxWidth(45);
        checkBox.setTooltip(new Tooltip(country.name));
        checkBox.setOnAction(event -> {
            if (checkBox.isSelected()) {
                addAcceptedCountry(countryCode);
            } else {
                removeAcceptedCountry(countryCode);
            }

            updateAllInputsValid();
        });
        flowPane.getChildren().add(checkBox);
    }

    abstract protected void autoFillNameTextField();

    abstract public void addFormForAddAccount();

    abstract public void addFormForDisplayAccount();

    protected abstract void updateAllInputsValid();

    public void updateFromInputs() {
        autoFillNameTextField();
        updateAllInputsValid();
    }

    public boolean isAccountNameValid() {
        return inputValidator.validate(paymentAccount.getAccountName()).isValid;
    }

    public int getGridRow() {
        return gridRow;
    }

    public int getRowSpan() {
        return gridRow - gridRowFrom + 2;
    }

    public PaymentAccount getPaymentAccount() {
        return paymentAccount;
    }

    public BooleanProperty allInputsValidProperty() {
        return allInputsValid;
    }

    void removeAcceptedCountry(String countryCode) {
    }

    void addAcceptedCountry(String countryCode) {
    }
}
